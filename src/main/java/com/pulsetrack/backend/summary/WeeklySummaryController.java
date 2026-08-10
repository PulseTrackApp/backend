package com.pulsetrack.backend.summary;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.pulsetrack.backend.common.security.AuthenticatedUser;
import com.pulsetrack.backend.summary.dto.WeeklySummaryResponse;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bilan hebdomadaire alimentant le dashboard.
 */
@RestController
@RequestMapping("/api/v1/me/weekly-summary")
public class WeeklySummaryController {

    private final WeeklySummaryService weeklySummaryService;

    public WeeklySummaryController(WeeklySummaryService weeklySummaryService) {
        this.weeklySummaryService = weeklySummaryService;
    }

    /**
     * @param weekStart jour quelconque de la semaine voulue, ramene a son lundi ;
     *                  absent pour la semaine en cours
     * @param zone      fuseau de l'utilisateur (ex. {@code Africa/Ouagadougou}).
     *                  UTC par defaut : sans cette information, le serveur ne
     *                  peut pas savoir ou commence la journee de l'utilisateur
     */
    @GetMapping
    public WeeklySummaryResponse summary(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(required = false) String zone) {

        // ZoneId.of leve une DateTimeException sur un fuseau inconnu, traduite en
        // 400 par le gestionnaire global.
        ZoneId zoneId = (zone == null || zone.isBlank()) ? ZoneOffset.UTC : ZoneId.of(zone);
        return weeklySummaryService.summarize(AuthenticatedUser.idOf(jwt), weekStart, zoneId);
    }
}
