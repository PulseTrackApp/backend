package com.pulsetrack.backend.coach;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.pulsetrack.backend.coach.dto.ApiKeyRequest;
import com.pulsetrack.backend.coach.dto.CoachMessageResponse;
import com.pulsetrack.backend.coach.dto.CoachQuestionRequest;
import com.pulsetrack.backend.coach.dto.GeminiSettingsRequest;
import com.pulsetrack.backend.coach.dto.GeminiSettingsResponse;
import com.pulsetrack.backend.common.security.AuthenticatedUser;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assistant Gemini : reglages, cle API et conseils.
 */
@RestController
@RequestMapping("/api/v1/me/coach")
public class CoachController {

    private final CoachService coachService;
    private final GeminiSettingsService settingsService;

    public CoachController(CoachService coachService, GeminiSettingsService settingsService) {
        this.coachService = coachService;
        this.settingsService = settingsService;
    }

    @GetMapping("/settings")
    public GeminiSettingsResponse settings(@AuthenticationPrincipal Jwt jwt) {
        return settingsService.getOrCreate(AuthenticatedUser.idOf(jwt));
    }

    @PutMapping("/settings")
    public GeminiSettingsResponse updateSettings(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody GeminiSettingsRequest request) {
        return settingsService.updatePreferences(AuthenticatedUser.idOf(jwt), request);
    }

    /**
     * Depose la cle API de l'utilisateur.
     *
     * <p>Endpoint distinct des autres reglages : la cle ne transite ainsi que
     * lorsqu'on la change, et jamais lors d'un simple changement de ton.
     * La reponse confirme l'enregistrement sans jamais renvoyer la cle.
     */
    @PutMapping("/settings/api-key")
    public GeminiSettingsResponse storeApiKey(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody ApiKeyRequest request) {
        return settingsService.storeApiKey(AuthenticatedUser.idOf(jwt), request.apiKey());
    }

    @DeleteMapping("/settings/api-key")
    public GeminiSettingsResponse deleteApiKey(@AuthenticationPrincipal Jwt jwt) {
        return settingsService.deleteApiKey(AuthenticatedUser.idOf(jwt));
    }

    /**
     * Bilan de la semaine.
     *
     * @param refresh force une nouvelle generation ; sans lui, un bilan deja
     *                produit pour cette semaine est renvoye sans consommer de quota
     */
    @PostMapping("/weekly-review")
    public CoachMessageResponse weeklyReview(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(required = false) String zone,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return coachService.weeklyReview(AuthenticatedUser.idOf(jwt), weekStart, zoneOf(zone), refresh);
    }

    @PostMapping("/ask")
    public CoachMessageResponse ask(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody CoachQuestionRequest request,
                                    @RequestParam(required = false) String zone) {
        return coachService.ask(AuthenticatedUser.idOf(jwt), request.question(), zoneOf(zone));
    }

    /**
     * Dernier conseil connu, sans appeler Gemini. C'est ce qu'affiche le
     * dashboard, et il repond 204 tant qu'aucun conseil n'existe.
     */
    @GetMapping("/latest")
    public ResponseEntity<CoachMessageResponse> latest(@AuthenticationPrincipal Jwt jwt) {
        return coachService.latest(AuthenticatedUser.idOf(jwt))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    private ZoneId zoneOf(String zone) {
        return (zone == null || zone.isBlank()) ? ZoneOffset.UTC : ZoneId.of(zone);
    }
}
