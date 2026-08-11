package com.pulsetrack.backend.goal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.common.error.BusinessRuleException;
import com.pulsetrack.backend.common.error.ConflictException;
import com.pulsetrack.backend.common.error.ResourceNotFoundException;
import com.pulsetrack.backend.goal.dto.GoalRequest;
import com.pulsetrack.backend.goal.dto.GoalResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des objectifs de l'utilisateur.
 */
@Service
public class GoalService {

    private final GoalRepository goals;

    public GoalService(GoalRepository goals) {
        this.goals = goals;
    }

    /**
     * Fixe un nouvel objectif.
     *
     * @throws ConflictException     si un objectif du meme type est deja en cours
     * @throws BusinessRuleException si l'echeance precede le debut
     */
    @Transactional
    public GoalResponse create(UUID userId, GoalRequest request) {
        goals.findByUserIdAndTypeAndActiveTrue(userId, request.type()).ifPresent(existing -> {
            throw new ConflictException(
                    "Un objectif de ce type est deja en cours. Modifiez-le ou archivez-le d'abord.");
        });

        LocalDate startDate = request.startDate() == null ? LocalDate.now() : request.startDate();
        validateDates(startDate, request.endDate());

        Instant now = Instant.now();
        Goal goal = new Goal(userId, request.type(), now);
        goal.update(request.targetValue(), startDate, request.endDate(), now);

        return toResponse(goals.save(goal));
    }

    /**
     * Modifie la cible ou les dates d'un objectif existant. Le type n'est pas
     * modifiable : changer la nature d'un objectif, c'est en fixer un autre.
     */
    @Transactional
    public GoalResponse update(UUID userId, UUID goalId, GoalRequest request) {
        Goal goal = requireOwned(userId, goalId);

        if (goal.getType() != request.type()) {
            throw new BusinessRuleException(
                    "Le type d'un objectif ne se modifie pas. Archivez celui-ci et creez-en un nouveau.");
        }

        LocalDate startDate = request.startDate() == null ? goal.getStartDate() : request.startDate();
        validateDates(startDate, request.endDate());

        goal.update(request.targetValue(), startDate, request.endDate(), Instant.now());
        return toResponse(goal);
    }

    /**
     * @param activeOnly {@code true} pour les seuls objectifs en cours ;
     *                   {@code false} ajoute les archives, d'ou la pagination
     * @return page d'objectifs, du plus recent au plus ancien
     */
    @Transactional(readOnly = true)
    public Page<GoalResponse> list(UUID userId, boolean activeOnly, Pageable pageable) {
        Page<Goal> found = activeOnly
                ? goals.findByUserIdAndActiveTrue(userId, pageable)
                : goals.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return found.map(this::toResponse);
    }

    /**
     * Tous les objectifs, archives comprises, sans pagination.
     *
     * <p>Reserve a l'export des donnees personnelles, qui doit etre complet par
     * definition : une page d'export ne serait pas un export. C'est aussi
     * pourquoi il ne s'appelle pas {@code list} — l'endpoint public, lui, reste
     * pagine.
     */
    @Transactional(readOnly = true)
    public List<GoalResponse> exportAll(UUID userId) {
        return goals.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged())
                .map(this::toResponse)
                .getContent();
    }

    /** Objectifs en cours, sous forme d'entites, pour le calcul de progression. */
    @Transactional(readOnly = true)
    public List<Goal> activeGoalsOf(UUID userId) {
        return goals.findByUserIdAndActiveTrue(userId);
    }

    /**
     * Archive l'objectif au lieu de l'effacer : on garde la trace de ce qu'on
     * s'etait fixe, et la place se libere pour un nouvel objectif du meme type.
     */
    @Transactional
    public GoalResponse archive(UUID userId, UUID goalId) {
        Goal goal = requireOwned(userId, goalId);
        goal.archive(Instant.now());
        return toResponse(goal);
    }

    @Transactional
    public void delete(UUID userId, UUID goalId) {
        goals.delete(requireOwned(userId, goalId));
    }

    private Goal requireOwned(UUID userId, UUID goalId) {
        return goals.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Objectif introuvable."));
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessRuleException("L'echeance ne peut pas preceder la date de debut.");
        }
    }

    private GoalResponse toResponse(Goal goal) {
        return new GoalResponse(
                goal.getId(),
                goal.getType(),
                goal.getType().unit(),
                goal.getTargetValue(),
                goal.getStartDate(),
                goal.getEndDate(),
                goal.isActive());
    }
}
