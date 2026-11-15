package com.pulsetrack.backend.admin;

import java.util.UUID;

import com.pulsetrack.backend.admin.dto.AdminNotificationRequest;
import com.pulsetrack.backend.admin.dto.AdminNotificationResponse;
import com.pulsetrack.backend.common.error.BusinessRuleException;
import com.pulsetrack.backend.common.error.ResourceNotFoundException;
import com.pulsetrack.backend.push.DeviceTokenService;
import com.pulsetrack.backend.push.PushNotification;
import com.pulsetrack.backend.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Envoi d'une notification depuis l'application d'administration.
 *
 * <p>Un message pousse sur le telephone de quelqu'un est un geste qui ne se
 * reprend pas : une fois parti, il est affiche. Cette classe existe pour que ce
 * geste soit encadre plutot que direct — destinataire explicite, compte verifie,
 * envoi journalise avec son auteur.
 *
 * <p>Aucune notification n'est enregistree en base. C'est assume : l'historique
 * utile est celui du journal du serveur, et stocker les messages envoyes
 * demanderait une table, une migration et un ecran pour la lire, pour un besoin
 * — se souvenir de ce qu'on a annonce — que le journal couvre deja.
 */
@Service
public class AdminNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AdminNotificationService.class);

    private final DeviceTokenService devices;
    private final UserRepository users;

    public AdminNotificationService(DeviceTokenService devices, UserRepository users) {
        this.devices = devices;
        this.users = users;
    }

    /**
     * @param author administrateur a l'origine de l'envoi, pour le journal
     * @throws BusinessRuleException     si la cible et le compte vise se
     *                                   contredisent
     * @throws ResourceNotFoundException si le compte vise n'existe pas
     */
    public AdminNotificationResponse send(AdminNotificationRequest request, UUID author) {
        PushNotification notification = notificationOf(request);

        return switch (request.target()) {
            case USER -> sendToUser(request, notification, author);
            case ALL -> sendToAll(request, notification, author);
        };
    }

    private AdminNotificationResponse sendToUser(AdminNotificationRequest request,
                                                 PushNotification notification,
                                                 UUID author) {
        UUID userId = request.userId();
        if (userId == null) {
            throw new BusinessRuleException("Précise le compte destinataire.");
        }
        if (!users.existsById(userId)) {
            throw new ResourceNotFoundException("Compte introuvable.");
        }

        int delivered = devices.notifyUser(userId, notification);
        log.info("Administrateur {} a notifié le compte {} : {} appareil(s) joint(s), titre « {} »",
                author, userId, delivered, request.title());
        return new AdminNotificationResponse(1, delivered);
    }

    private AdminNotificationResponse sendToAll(AdminNotificationRequest request,
                                                PushNotification notification,
                                                UUID author) {
        // Un identifiant de compte renseigne avec une diffusion generale est le
        // signe d'un formulaire mal rempli. Ignorer le champ en silence enverrait
        // a tout le monde un message qu'on croyait adresser a une personne.
        if (request.userId() != null) {
            throw new BusinessRuleException(
                    "Une diffusion générale ne vise pas de compte en particulier.");
        }

        DeviceTokenService.Delivery delivery = devices.notifyAll(notification);
        log.info("Administrateur {} a diffusé à {} compte(s), {} appareil(s), titre « {} »",
                author, delivery.recipients(), delivery.devices(), request.title());
        return new AdminNotificationResponse(delivery.recipients(), delivery.devices());
    }

    private PushNotification notificationOf(AdminNotificationRequest request) {
        PushNotification notification =
                PushNotification.of(request.title().trim(), request.body().trim());
        String route = request.route();
        if (route == null || route.isBlank()) {
            return notification;
        }
        return notification.withRoute(route.trim());
    }
}
