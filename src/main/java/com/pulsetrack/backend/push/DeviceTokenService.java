package com.pulsetrack.backend.push;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.common.error.ResourceNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enregistrement des appareils et diffusion des notifications.
 */
@Service
public class DeviceTokenService {

    private static final Logger log = LoggerFactory.getLogger(DeviceTokenService.class);

    private final DeviceTokenRepository tokens;
    private final PushSender pushSender;

    public DeviceTokenService(DeviceTokenRepository tokens, PushSender pushSender) {
        this.tokens = tokens;
        this.pushSender = pushSender;
    }

    /**
     * Enregistre ou rafraichit un appareil.
     *
     * <p>Idempotent : l'application appelle cet endpoint a chaque demarrage et a
     * chaque renouvellement de jeton par FCM. Si le jeton existe deja pour un
     * autre compte, il change simplement de proprietaire — c'est le cas du
     * telephone reconnecte avec un compte different.
     */
    @Transactional
    public void register(UUID userId, String token, DevicePlatform platform) {
        Instant now = Instant.now();
        tokens.findByToken(token)
                .ifPresentOrElse(
                        existing -> existing.refresh(userId, platform, now),
                        () -> tokens.save(new DeviceToken(userId, token, platform, now)));
    }

    @Transactional
    public void unregister(UUID userId, String token) {
        DeviceToken found = tokens.findByTokenAndUserId(token, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Appareil introuvable."));
        tokens.delete(found);
    }

    /**
     * Envoie a tous les appareils de l'utilisateur, et supprime au passage les
     * jetons que FCM declare perimes.
     *
     * <p>Un appareil en echec n'interrompt pas les autres : le rappel doit
     * arriver sur le telephone qui marche, meme si la tablette a ete effacee.
     *
     * @return nombre d'appareils effectivement joints
     */
    @Transactional
    public int notifyUser(UUID userId, PushNotification notification) {
        List<DeviceToken> devices = tokens.findByUserId(userId);
        int delivered = 0;

        for (DeviceToken device : devices) {
            boolean stillValid = pushSender.send(device.getToken(), notification);
            if (stillValid) {
                delivered++;
            } else {
                tokens.delete(device);
                log.info("Jeton perime supprime pour l'utilisateur {}", userId);
            }
        }
        return delivered;
    }

    /** Utilisateurs disposant d'au moins un appareil, seuls destinataires possibles. */
    @Transactional(readOnly = true)
    public List<UUID> userIdsWithDevices() {
        return tokens.findDistinctUserIds();
    }
}
