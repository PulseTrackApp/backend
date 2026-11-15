package com.pulsetrack.backend.support;

import com.pulsetrack.backend.client.ClientProperties;
import com.pulsetrack.backend.common.security.AuthenticatedUser;
import com.pulsetrack.backend.support.dto.SupportMessageRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ecrire a l'assistance depuis l'application.
 *
 * <p>Sous {@code /me} : l'emetteur est celui que le jeton designe, et il n'y a
 * donc aucun moyen d'ecrire au nom d'un autre.
 *
 * <p><strong>Reste ouvert meme sans droit d'usage</strong> (voir
 * {@code SubscriptionInterceptor}). Quelqu'un dont l'essai vient de finir, ou
 * qui n'arrive pas a payer, doit precisement pouvoir le signaler : lui fermer ce
 * canal reviendrait a rendre le probleme insoluble au moment ou il se pose.
 */
@RestController
@RequestMapping("/api/v1/me/support")
public class SupportController {

    private final SupportService support;

    public SupportController(SupportService support) {
        this.support = support;
    }

    /**
     * Poste le message. Repond {@code 202} : l'acheminement est asynchrone, et
     * repondre {@code 200} laisserait croire a une remise deja faite.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void send(@AuthenticationPrincipal Jwt jwt,
                     @Valid @RequestBody SupportMessageRequest request,
                     @RequestHeader(value = ClientProperties.VERSION_HEADER, required = false)
                     String appVersion,
                     @RequestHeader(value = ClientProperties.PLATFORM_HEADER, required = false)
                     String platform) {
        support.send(AuthenticatedUser.idOf(jwt), request.subject(), request.message(),
                appVersion, platform);
    }
}
