package com.pulsetrack.backend.achievement;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acces aux trophees.
 *
 * <p>Les lectures par seance ne portent pas {@code userId} : elles suivent
 * toujours une lecture de la seance elle-meme, qui a deja verifie le
 * proprietaire. Les lectures qui partent de l'utilisateur, elles, filtrent
 * dessus comme partout ailleurs.
 */
public interface WorkoutAchievementRepository extends JpaRepository<WorkoutAchievement, UUID> {

    List<WorkoutAchievement> findByWorkoutIdOrderByAchievedAtAsc(UUID workoutId);

    /** Trophees de plusieurs seances d'un coup, pour badger une page d'historique. */
    List<WorkoutAchievement> findByWorkoutIdIn(Collection<UUID> workoutIds);

    long countByUserId(UUID userId);
}
