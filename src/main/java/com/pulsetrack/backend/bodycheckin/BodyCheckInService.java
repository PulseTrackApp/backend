package com.pulsetrack.backend.bodycheckin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulsetrack.backend.bodycheckin.dto.BodyCheckInRequest;
import com.pulsetrack.backend.bodycheckin.dto.BodyCheckInResponse;
import com.pulsetrack.backend.bodycheckin.dto.BodyProgressResponse;
import com.pulsetrack.backend.common.error.ResourceNotFoundException;
import com.pulsetrack.backend.profile.ProfileService;
import com.pulsetrack.backend.stats.dto.BodyStats;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suivi de l'evolution physique : releves et indicateurs de progression.
 */
@Service
public class BodyCheckInService {

    private final BodyCheckInRepository checkIns;
    private final BodyProgressCalculator calculator;
    private final ProfileService profileService;

    public BodyCheckInService(BodyCheckInRepository checkIns,
                              BodyProgressCalculator calculator,
                              ProfileService profileService) {
        this.checkIns = checkIns;
        this.calculator = calculator;
        this.profileService = profileService;
    }

    /**
     * Enregistre un releve, ou remplace celui du meme jour.
     *
     * <p>Operation idempotente sur la date plutot que creation stricte : se
     * repeser le soir apres une premiere pesee le matin doit corriger la valeur,
     * pas creer deux points contradictoires sur la courbe.
     */
    @Transactional
    public BodyCheckInResponse save(UUID userId, BodyCheckInRequest request) {
        BodyCheckIn checkIn = checkIns.findByUserIdAndCheckinDate(userId, request.checkinDate())
                .orElseGet(() -> new BodyCheckIn(userId, request.checkinDate(), Instant.now()));

        checkIn.update(
                request.weightKg(),
                request.waistCm(),
                request.chestCm(),
                request.hipsCm(),
                request.energyLevel(),
                request.averageSleepHours(),
                normalizeNote(request.note()));

        BodyCheckIn saved = checkIns.save(checkIn);
        synchronizeProfileWeight(userId);

        return toResponse(saved, heightCmOf(userId));
    }

    @Transactional(readOnly = true)
    public Page<BodyCheckInResponse> list(UUID userId, Pageable pageable) {
        Integer heightCm = heightCmOf(userId);
        return checkIns.findByUserId(userId, pageable).map(checkIn -> toResponse(checkIn, heightCm));
    }

    /**
     * Serie complete et indicateurs derives, pour l'ecran « evolution physique ».
     */
    @Transactional(readOnly = true)
    public BodyProgressResponse progress(UUID userId) {
        Integer heightCm = heightCmOf(userId);
        List<BodyCheckIn> series = checkIns.findByUserIdOrderByCheckinDateAsc(userId);

        BodyProgressCalculator.Indicators indicators = calculator.calculate(series, heightCm);
        List<BodyCheckInResponse> points = series.stream()
                .map(checkIn -> toResponse(checkIn, heightCm))
                .toList();

        return new BodyProgressResponse(
                points,
                points.size(),
                indicators.startWeightKg(),
                indicators.currentWeightKg(),
                indicators.totalChangeKg(),
                indicators.changeSincePreviousKg(),
                indicators.averageWeeklyChangeKg(),
                indicators.trend(),
                indicators.currentBmi(),
                indicators.bmiCategory());
    }

    /**
     * Evolution physique restreinte a une periode, pour l'ecran de statistiques.
     *
     * @param from premier jour inclus
     * @param to   dernier jour inclus
     */
    @Transactional(readOnly = true)
    public BodyStats statsBetween(UUID userId, LocalDate from, LocalDate to) {
        Integer heightCm = heightCmOf(userId);
        List<BodyCheckIn> series =
                checkIns.findByUserIdAndCheckinDateBetweenOrderByCheckinDateAsc(userId, from, to);

        BodyProgressCalculator.PeriodIndicators indicators = calculator.periodStats(series, heightCm);

        return new BodyStats(
                indicators.checkInCount(),
                indicators.startWeightKg(),
                indicators.endWeightKg(),
                indicators.changeKg(),
                indicators.minWeightKg(),
                indicators.maxWeightKg(),
                indicators.averageWeightKg(),
                indicators.averageWeeklyChangeKg(),
                indicators.trend(),
                indicators.endBmi(),
                indicators.endBmi() == null ? null : BmiCategory.of(indicators.endBmi()),
                indicators.waistChangeCm(),
                indicators.chestChangeCm(),
                indicators.hipsChangeCm(),
                series.stream().map(checkIn -> toResponse(checkIn, heightCm)).toList());
    }

    /**
     * Date de la derniere pesee, pour decider s'il faut la rappeler.
     *
     * @return vide si l'utilisateur ne s'est jamais pese
     */
    @Transactional(readOnly = true)
    public Optional<LocalDate> lastCheckInDateOf(UUID userId) {
        return checkIns.findFirstByUserIdOrderByCheckinDateDesc(userId)
                .map(BodyCheckIn::getCheckinDate);
    }

    /**
     * Premier et dernier poids releves, pour mesurer un objectif de poids.
     *
     * @return vide tant qu'aucun releve n'existe ; la progression vers un poids
     *         cible est alors incalculable, et c'est a l'appelant de le dire
     */
    @Transactional(readOnly = true)
    public Optional<WeightRange> weightRangeOf(UUID userId) {
        return checkIns.findFirstByUserIdOrderByCheckinDateAsc(userId)
                .flatMap(first -> checkIns.findFirstByUserIdOrderByCheckinDateDesc(userId)
                        .map(last -> new WeightRange(first.getWeightKg(), last.getWeightKg())));
    }

    /**
     * @param baselineKg poids du tout premier releve
     * @param currentKg  poids du releve le plus recent
     */
    public record WeightRange(double baselineKg, double currentKg) {
    }

    @Transactional
    public void delete(UUID userId, UUID checkInId) {
        BodyCheckIn checkIn = checkIns.findByIdAndUserId(checkInId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Releve introuvable."));
        checkIns.delete(checkIn);
        // Supprimer le dernier releve doit ramener le profil au precedent, pas
        // laisser un poids qui n'existe plus nulle part.
        synchronizeProfileWeight(userId);
    }

    /**
     * Aligne le poids du profil sur le releve le plus recent.
     *
     * <p>On relit le dernier releve plutot que d'utiliser celui qu'on vient
     * d'ecrire : rattraper un oubli de la semaine passee ne doit pas ecraser le
     * poids d'aujourd'hui.
     */
    private void synchronizeProfileWeight(UUID userId) {
        checkIns.findFirstByUserIdOrderByCheckinDateDesc(userId)
                .ifPresent(latest -> profileService.recordMeasuredWeight(userId, latest.getWeightKg()));
    }

    private Integer heightCmOf(UUID userId) {
        return profileService.heightCmOf(userId).orElse(null);
    }

    private BodyCheckInResponse toResponse(BodyCheckIn checkIn, Integer heightCm) {
        return new BodyCheckInResponse(
                checkIn.getId(),
                checkIn.getCheckinDate(),
                checkIn.getWeightKg(),
                checkIn.getWaistCm(),
                checkIn.getChestCm(),
                checkIn.getHipsCm(),
                checkIn.getEnergyLevel(),
                checkIn.getAverageSleepHours(),
                checkIn.getNote(),
                calculator.bmi(checkIn.getWeightKg(), heightCm));
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
