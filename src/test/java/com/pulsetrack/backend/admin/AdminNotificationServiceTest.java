package com.pulsetrack.backend.admin;

import java.util.UUID;

import com.pulsetrack.backend.admin.dto.AdminNotificationRequest;
import com.pulsetrack.backend.admin.dto.AdminNotificationResponse;
import com.pulsetrack.backend.common.error.BusinessRuleException;
import com.pulsetrack.backend.common.error.ResourceNotFoundException;
import com.pulsetrack.backend.push.DeviceTokenService;
import com.pulsetrack.backend.push.PushNotification;
import com.pulsetrack.backend.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Garde-fous de l'envoi manuel de notifications.
 *
 * <p>Ce qui est eprouve ici n'est pas la mecanique d'envoi — elle est deja
 * couverte — mais les deux confusions qui coutent cher : croire ecrire a une
 * personne et diffuser a tout le monde, ou croire diffuser et n'atteindre
 * personne.
 */
class AdminNotificationServiceTest {

    private final DeviceTokenService devices = mock(DeviceTokenService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AdminNotificationService service = new AdminNotificationService(devices, users);

    private final UUID author = UUID.randomUUID();

    @Test
    void notifie_un_compte_et_rend_le_nombre_d_appareils() {
        UUID target = UUID.randomUUID();
        when(users.existsById(target)).thenReturn(true);
        when(devices.notifyUser(eq(target), any())).thenReturn(2);

        AdminNotificationResponse response = service.send(
                new AdminNotificationRequest(AdminNotificationRequest.Target.USER, target,
                        "Salle fermée", "Le club est fermé demain.", "/home"),
                author);

        assertThat(response.recipients()).isEqualTo(1);
        assertThat(response.devices()).isEqualTo(2);

        ArgumentCaptor<PushNotification> sent = ArgumentCaptor.forClass(PushNotification.class);
        verify(devices).notifyUser(eq(target), sent.capture());
        assertThat(sent.getValue().title()).isEqualTo("Salle fermée");
        // La route voyage dans les donnees : c'est elle qui ouvre le bon ecran au
        // clic plutot que la page d'accueil.
        assertThat(sent.getValue().data()).containsEntry("route", "/home");
    }

    @Test
    void diffuse_a_tous_les_comptes_equipes() {
        when(devices.notifyAll(any())).thenReturn(new DeviceTokenService.Delivery(7, 9));

        AdminNotificationResponse response = service.send(
                new AdminNotificationRequest(AdminNotificationRequest.Target.ALL, null,
                        "Nouveauté", "Les statistiques sont arrivées.", null),
                author);

        assertThat(response.recipients()).isEqualTo(7);
        assertThat(response.devices()).isEqualTo(9);
    }

    /**
     * Le cas qui justifie a lui seul le champ {@code target} : sans lui, un
     * identifiant oublie dans le formulaire aurait envoye a tout le monde un
     * message destine a une seule personne.
     */
    @Test
    void refuse_une_diffusion_generale_qui_vise_aussi_un_compte() {
        assertThatThrownBy(() -> service.send(
                new AdminNotificationRequest(AdminNotificationRequest.Target.ALL, UUID.randomUUID(),
                        "Titre", "Corps", null),
                author))
                .isInstanceOf(BusinessRuleException.class);

        verify(devices, never()).notifyAll(any());
    }

    @Test
    void refuse_un_envoi_cible_sans_compte() {
        assertThatThrownBy(() -> service.send(
                new AdminNotificationRequest(AdminNotificationRequest.Target.USER, null,
                        "Titre", "Corps", null),
                author))
                .isInstanceOf(BusinessRuleException.class);
    }

    /**
     * Un identifiant inconnu doit se voir : sinon l'ecran annonce « 0 appareil »
     * et laisse croire a un telephone eteint, alors que le compte n'existe pas.
     */
    @Test
    void refuse_un_compte_inconnu() {
        UUID unknown = UUID.randomUUID();
        when(users.existsById(unknown)).thenReturn(false);

        assertThatThrownBy(() -> service.send(
                new AdminNotificationRequest(AdminNotificationRequest.Target.USER, unknown,
                        "Titre", "Corps", null),
                author))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(devices, never()).notifyUser(any(), any());
    }

    @Test
    void ne_met_pas_de_route_vide_dans_les_donnees() {
        UUID target = UUID.randomUUID();
        when(users.existsById(target)).thenReturn(true);
        when(devices.notifyUser(eq(target), any())).thenReturn(1);

        service.send(new AdminNotificationRequest(AdminNotificationRequest.Target.USER, target,
                "  Titre  ", "  Corps  ", "   "), author);

        ArgumentCaptor<PushNotification> sent = ArgumentCaptor.forClass(PushNotification.class);
        verify(devices).notifyUser(eq(target), sent.capture());
        assertThat(sent.getValue().data()).isEmpty();
        // Les espaces de saisie ne doivent pas se retrouver a l'ecran du
        // destinataire.
        assertThat(sent.getValue().title()).isEqualTo("Titre");
        assertThat(sent.getValue().body()).isEqualTo("Corps");
    }

    @Test
    void diffuse_sans_destinataire_le_dit_par_ses_compteurs() {
        when(devices.notifyAll(any())).thenReturn(new DeviceTokenService.Delivery(0, 0));

        AdminNotificationResponse response = service.send(
                new AdminNotificationRequest(AdminNotificationRequest.Target.ALL, null,
                        "Titre", "Corps", null),
                author);

        assertThat(response.recipients()).isZero();
        assertThat(response.devices()).isZero();
    }
}
