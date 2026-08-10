package com.pulsetrack.backend.push;

import com.pulsetrack.backend.common.security.AuthenticatedUser;
import com.pulsetrack.backend.push.dto.DeviceTokenRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Appareils de l'utilisateur courant, destinataires des notifications.
 */
@RestController
@RequestMapping("/api/v1/me/device-tokens")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    public DeviceTokenController(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    /**
     * {@code PUT} car l'operation est identifiee par le jeton et rejouable :
     * l'application l'appelle a chaque demarrage sans jamais creer de doublon.
     */
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.register(AuthenticatedUser.idOf(jwt), request.token(), request.platform());
    }

    /** Appele a la deconnexion, pour ne plus recevoir les rappels d'un compte quitte. */
    @DeleteMapping("/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
        deviceTokenService.unregister(AuthenticatedUser.idOf(jwt), token);
    }
}
