package com.pulsetrack.backend.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.pulsetrack.backend.bodycheckin.BodyMassIndexCalculator;
import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.common.error.BusinessRuleException;
import com.pulsetrack.backend.common.error.ResourceNotFoundException;
import com.pulsetrack.backend.profile.dto.ProfilePatchRequest;
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
                .orElseThrow(() -> new ResourceNotFoundException("Aucun profil renseigné pour ce compte."));
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
     * Modifie les seuls champs fournis, et laisse les autres intacts.
     *
     * <p><strong>Ce que cette methode empeche.</strong> Un ecran qui ne corrige
     * que le poids et rejoue un {@code PUT} incomplet efface au passage la date
     * de naissance et le sexe — les deux champs facultatifs — sans qu'aucune
     * validation ne s'y oppose. Les champs obligatoires, eux, sont proteges : un
     * remplacement ampute est refuse en {@code 400}. C'etait donc precisement ce
     * que l'utilisateur avait pris la peine de renseigner en plus qui se perdait
     * silencieusement.
     *
     * @throws ResourceNotFoundException si le profil n'existe pas encore : une
     *                                   modification partielle n'a rien a
     *                                   modifier, et creer un profil a moitie
     *                                   rempli laisserait un poids a zero qui
     *                                   fausserait toutes les calories
     * @throws BusinessRuleException     si le corps ne demande aucun changement ;
     *                                   repondre 200 laisserait croire a une
     *                                   modification enregistree
     */
    @Transactional
    public ProfileResponse patch(UUID userId, ProfilePatchRequest request) {
        if (request.isEmpty()) {
            throw new BusinessRuleException("Aucune modification demandée.");
        }

        UserProfile profile = profiles.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun profil renseigné pour ce compte : enregistre-le en entier d'abord."));

        String displayName = profile.getDisplayName();
        if (request.displayName() != null) {
            displayName = request.displayName().trim();
            if (displayName.isEmpty()) {
                throw new BusinessRuleException("Le nom affiché ne peut pas être vide.");
            }
        }

        // Copie defensive indispensable : `update` vide la collection avant de la
        // remplir. Lui passer la collection du profil lui-meme la viderait, puis
        // la recopierait depuis le vide — les sports pratiques disparaitraient a
        // chaque modification partielle qui ne les mentionne pas.
        Set<SportType> sports = request.preferredSports() == null
                ? Set.copyOf(profile.getPreferredSports())
                : request.preferredSports();

        profile.update(
                displayName,
                request.heightCm() == null ? profile.getHeightCm() : request.heightCm(),
                request.currentWeightKg() == null ? profile.getCurrentWeightKg() : request.currentWeightKg(),
                request.birthDate() == null ? profile.getBirthDate() : request.birthDate(),
                request.sex() == null ? profile.getSex() : request.sex(),
                request.primaryGoal() == null ? profile.getPrimaryGoal() : request.primaryGoal(),
                request.fitnessLevel() == null ? profile.getFitnessLevel() : request.fitnessLevel(),
                sports,
                Instant.now());

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
                        "Renseignez votre profil (poids, taille) avant d'enregistrer une séance."));
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
                bmi,
                BodyMassIndexCalculator.category(bmi),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    private Integer ageOf(LocalDate birthDate) {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now()).getYears();
    }
}
