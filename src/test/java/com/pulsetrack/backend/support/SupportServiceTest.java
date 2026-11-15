package com.pulsetrack.backend.support;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulsetrack.backend.common.error.RateLimitedException;
import com.pulsetrack.backend.common.ratelimit.FixedWindowRateLimiter;
import com.pulsetrack.backend.user.User;
import com.pulsetrack.backend.user.UserRepository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ce que le formulaire d'aide garantit : l'emetteur est le compte connecte, et
 * personne ne peut s'en servir pour inonder une boite aux lettres.
 */
class SupportServiceTest {

    private final List<SupportMessage> sent = new ArrayList<>();
    private final UserRepository users = mock(UserRepository.class);
    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(Clock.systemUTC());
    private final SupportProperties properties =
            new SupportProperties("moi@exemple.test", 2, Duration.ofHours(1));

    private final SupportService service =
            new SupportService(sent::add, users, limiter, properties);

    @Test
    void poste_le_message_avec_l_adresse_du_compte_et_le_contexte_technique() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("coureur@exemple.test");
        when(users.findById(userId)).thenReturn(Optional.of(user));

        service.send(userId, "  Le GPS décroche  ", "  Ma trace s'arrête au bout de 2 km.  ",
                "1.4.0", "android");

        assertThat(sent).hasSize(1);
        SupportMessage message = sent.get(0);
        // L'identite vient du compte, jamais du corps de la requete : sans cela le
        // formulaire permettrait d'ecrire au nom de quelqu'un d'autre.
        assertThat(message.senderEmail()).isEqualTo("coureur@exemple.test");
        assertThat(message.userId()).isEqualTo(userId);
        // Version et plateforme partent avec le message : un signalement sans elles
        // oblige a un aller-retour avant de commencer a chercher.
        assertThat(message.appVersion()).isEqualTo("1.4.0");
        assertThat(message.platform()).isEqualTo("android");
        assertThat(message.subject()).isEqualTo("Le GPS décroche");
        assertThat(message.body()).isEqualTo("Ma trace s'arrête au bout de 2 km.");
    }

    /**
     * Un compte supprime entre-temps ne doit pas faire perdre le message : mieux
     * vaut un signalement sans adresse de reponse que pas de signalement.
     */
    @Test
    void poste_meme_sans_adresse_connue() {
        UUID userId = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.empty());

        service.send(userId, "Sujet", "Corps", null, null);

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).senderEmail()).isNull();
    }

    @Test
    void refuse_au_dela_du_quota_et_dit_combien_de_temps_attendre() {
        UUID userId = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.empty());

        service.send(userId, "Sujet", "Corps", null, null);
        service.send(userId, "Sujet", "Corps", null, null);

        assertThatThrownBy(() -> service.send(userId, "Sujet", "Corps", null, null))
                .isInstanceOf(RateLimitedException.class)
                .extracting(exception -> ((RateLimitedException) exception).retryAfter())
                .isNotNull();

        // Le troisieme message n'est pas parti.
        assertThat(sent).hasSize(2);
    }

    /**
     * Une configuration absente ne doit pas fermer le formulaire. Sans le
     * rattrapage, {@code maxMessages = 0} refuserait le premier message en
     * repondant « trop de messages envoyes ».
     */
    @Test
    void un_plafond_absent_retombe_sur_une_valeur_utilisable() {
        SupportProperties incomplete = new SupportProperties("moi@exemple.test", 0, null);

        assertThat(incomplete.maxMessages()).isPositive();
        assertThat(incomplete.window()).isNotNull();
        assertThat(incomplete.window()).isPositive();
    }

    /**
     * Le quota est par compte : celui qui abuse ne doit pas fermer le canal aux
     * autres.
     */
    @Test
    void compte_le_quota_par_compte() {
        UUID bavard = UUID.randomUUID();
        UUID autre = UUID.randomUUID();
        when(users.findById(bavard)).thenReturn(Optional.empty());
        when(users.findById(autre)).thenReturn(Optional.empty());

        service.send(bavard, "Sujet", "Corps", null, null);
        service.send(bavard, "Sujet", "Corps", null, null);
        assertThatThrownBy(() -> service.send(bavard, "Sujet", "Corps", null, null))
                .isInstanceOf(RateLimitedException.class);

        service.send(autre, "Sujet", "Corps", null, null);

        assertThat(sent).hasSize(3);
    }
}
