package com.pulsetrack.backend.reminder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active le planificateur, sous la meme condition que les rappels eux-memes.
 *
 * <p>Conditionner l'activation permet de couper proprement toute la mecanique en
 * test : un traitement planifie qui se declenche pendant une suite de tests la
 * rend non reproductible.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "pulsetrack.reminders", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
