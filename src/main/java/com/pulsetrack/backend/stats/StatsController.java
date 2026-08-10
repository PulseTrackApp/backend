package com.pulsetrack.backend.stats;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.pulsetrack.backend.common.security.AuthenticatedUser;
import com.pulsetrack.backend.stats.dto.StatsResponse;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Statistiques de l'utilisateur courant, sur la periode de son choix.
 */
@RestController
@RequestMapping("/api/v1/me/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * @param period    {@code WEEK} (defaut), {@code MONTH}, {@code YEAR} ou
     *                  {@code LIFETIME}
     * @param reference jour quelconque de la periode voulue ; permet de naviguer
     *                  vers le mois dernier sans calculer ses bornes cote client
     * @param zone      fuseau de l'utilisateur, UTC par defaut
     */
    @GetMapping
    public StatsResponse stats(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "WEEK") StatsPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reference,
            @RequestParam(required = false) String zone) {

        ZoneId zoneId = (zone == null || zone.isBlank()) ? ZoneOffset.UTC : ZoneId.of(zone);
        return statsService.compute(AuthenticatedUser.idOf(jwt), period, reference, zoneId);
    }
}
