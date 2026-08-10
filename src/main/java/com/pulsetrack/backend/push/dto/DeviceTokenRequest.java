package com.pulsetrack.backend.push.dto;

import com.pulsetrack.backend.push.DevicePlatform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param token    jeton d'enregistrement fourni par le SDK FCM sur l'appareil
 * @param platform plateforme, pour adapter plus tard la charge utile
 */
public record DeviceTokenRequest(
        @NotBlank @Size(max = 512) String token,
        @NotNull DevicePlatform platform) {
}
