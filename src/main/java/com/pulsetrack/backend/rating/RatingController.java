package com.pulsetrack.backend.rating;

import java.time.ZoneId;
import java.time.ZoneOffset;

import com.pulsetrack.backend.common.security.AuthenticatedUser;
import com.pulsetrack.backend.rating.dto.RatingResponse;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Note de l'utilisateur courant et encouragement qui l'accompagne.
 *
 * <p>Une seule route, en lecture, sans effet de bord. Le calcul est deterministe
 * et local : deux appels le meme jour rendent la meme note, et rien n'est
 * stocke — une seance supprimee se repercute d'elle-meme.
 */
@RestController
@RequestMapping("/api/v1/me/rating")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    /**
     * @param zone fuseau de l'utilisateur, UTC par defaut. Il decide ou tombent
     *             les bornes de journee, donc le compte des jours actifs — la
     *             composante qui pese le plus lourd dans la note
     */
    @GetMapping
    public RatingResponse rating(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(required = false) String zone) {
        ZoneId zoneId = (zone == null || zone.isBlank()) ? ZoneOffset.UTC : ZoneId.of(zone);
        return ratingService.rate(AuthenticatedUser.idOf(jwt), zoneId);
    }
}
