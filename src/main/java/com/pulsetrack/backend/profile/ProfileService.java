package com.pulsetrack.backend.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.common.error.ResourceNotFoundException;
import com.pulsetrack.backend.profile.dto.ProfileRequest;
import com.pulsetrack.backend.profile.dto.ProfileResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lecture et enregistrement du profil sportif de l'utilisateur courant.
 */
@Service
public class ProfileService {

    private final UserProfileRepository profiles;

    public ProfileService(UserProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getByUserId(UUID userId) {
        return profiles.findByUserId(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun profil renseigne pour ce compte."));
    }

    /**
     * Cree le profil au premier appel, le remplace ensuite : l'ecran d'onboarding
     * et l'ecran de modification partagent ainsi le meme endpoint idempotent.
     */
    @Transactional
    public ProfileResponse save(UUID userId, ProfileRequest request) {
        Instant now = Instant.now();
        UserProfile profile = profiles.findByUserId(userId)
                .orElseGet(() -> new UserProfile(userId, now));

        profile.update(
                request.displayName().trim(),
                request.heightCm(),
                request.currentWeightKg(),
                request.birthDate(),
                request.sex(),
                request.primaryGoal(),
                request.fitnessLevel(),
                request.preferredSports(),
                now);

        return toResponse(profiles.save(profile));
    }

    /**
     * Poids servant aux estimations de calories.
     *
     * @throws ResourceNotFoundException si le profil n'est pas encore renseigne ;
     *                                   estimer avec un poids invente donnerait un
     *                                   historique faux et silencieusement errone
     */
    @Transactional(readOnly = true)
    public double weightKgOf(UUID userId) {
        return profiles.findByUserId(userId)
                .map(UserProfile::getCurrentWeightKg)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Renseignez votre profil (poids, taille) avant d'enregistrer une seance."));
    }

    /**
     * Profil s'il existe, sans lever d'exception.
     *
     * <p>Destine a l'export : une archive doit pouvoir se generer meme pour un
     * compte dont le profil n'a jamais ete rempli.
     */
    @Transactional(readOnly = true)
    public Optional<ProfileResponse> findByUserId(UUID userId) {
        return profiles.findByUserId(userId).map(this::toResponse);
    }

    /**
     * Taille du profil, necessaire au calcul de l'IMC.
     *
     * @return vide si le profil n'est pas encore renseigne ; l'appelant affiche
     *         alors les mesures sans IMC plutot que de refuser l'operation
     */
    @Transactional(readOnly = true)
    public Optional<Integer> heightCmOf(UUID userId) {
        return profiles.findByUserId(userId).map(UserProfile::getHeightCm);
    }

    /**
     * Reporte sur le profil le poids de la derniere pesee.
     *
     * <p>Sans effet s'il n'y a pas encore de profil : un releve physique reste
     * une donnee valable en soi, il n'y a pas de raison de le refuser.
     */
    @Transactional
    public void recordMeasuredWeight(UUID userId, double weightKg) {
        profiles.findByUserId(userId)
                .ifPresent(profile -> profile.applyMeasuredWeight(weightKg, Instant.now()));
    }

    private ProfileResponse toResponse(UserProfile profile) {
        Double bmi = profile.bodyMassIndex();
        Set<SportType> sports = profile.getPreferredSports().isEmpty()
                ? EnumSet.noneOf(SportType.class)
                : EnumSet.copyOf(profile.getPreferredSports());

        return new ProfileResponse(
                profile.getId(),
                profile.getDisplayName(),
                profile.getHeightCm(),
                profile.getCurrentWeightKg(),
                profile.getBirthDate(),
                ageOf(profile.getBirthDate()),
                profile.getSex(),
                profile.getPrimaryGoal(),
                profile.getFitnessLevel(),
                sports,
                bmi == null ? null : Math.round(bmi * 10.0) / 10.0,
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    private Integer ageOf(LocalDate birthDate) {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now()).getYears();
    }
}
