package com.pulsetrack.backend.user;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;

import com.pulsetrack.backend.access.AccessProperties;
import com.pulsetrack.backend.access.AppModule;
import com.pulsetrack.backend.access.ModuleAccessService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Amorçage du compte administrateur, sans Spring ni base.
 *
 * <p>Teste en unitaire et non en integration : le comportement depend de l'etat
 * de la base au demarrage du contexte, qu'un test d'integration ne peut pas
 * mettre en scene sans dependre de l'ordre d'execution des autres classes.
 */
class AdminBootstrapTest {

    private static final String EMAIL = "chef@pulsetrack.test";
    private static final String PASSWORD = "motdepasse123";

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ModuleAccessService moduleAccess = mock(ModuleAccessService.class);

    @Test
    void cree_le_compte_administrateur_quand_il_est_absent() {
        given(users.findByEmail(EMAIL)).willReturn(Optional.empty());
        given(passwordEncoder.encode(PASSWORD)).willReturn("{bcrypt}hache");
        given(users.save(any(User.class))).willAnswer(call -> call.getArgument(0));

        bootstrap(EMAIL, PASSWORD).run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().isAdmin()).isTrue();
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}hache");
        verify(moduleAccess).grantDefaults(any());
    }

    /**
     * Le controle qui compte : sans lui, chaque redeploiement reappliquerait le
     * mot de passe de l'environnement et effacerait celui que l'administrateur
     * aurait change depuis l'application, sans que rien ne le signale.
     */
    @Test
    void ne_reecrit_jamais_le_mot_de_passe_d_un_compte_existant() {
        User existant = new User(EMAIL, "{bcrypt}ancien", Instant.parse("2026-08-01T08:00:00Z"));
        existant.changeRole(Role.ADMIN);
        given(users.findByEmail(EMAIL)).willReturn(Optional.of(existant));

        bootstrap(EMAIL, "un-tout-autre-mot-de-passe").run(null);

        assertThat(existant.getPasswordHash()).isEqualTo("{bcrypt}ancien");
        verify(passwordEncoder, never()).encode(any());
        verify(users, never()).save(any());
    }

    @Test
    void promeut_un_compte_existant_encore_ordinaire() {
        User existant = new User(EMAIL, "{bcrypt}ancien", Instant.parse("2026-08-01T08:00:00Z"));
        given(users.findByEmail(EMAIL)).willReturn(Optional.of(existant));

        bootstrap(EMAIL, PASSWORD).run(null);

        assertThat(existant.isAdmin()).isTrue();
        verify(users).save(existant);
    }

    @Test
    void ne_cree_rien_sans_mot_de_passe_d_amorcage() {
        given(users.findByEmail(EMAIL)).willReturn(Optional.empty());

        bootstrap(EMAIL, "").run(null);

        verify(users, never()).save(any());
    }

    /**
     * Journalise et abandonne, plutot que d'empecher le demarrage : une erreur
     * de saisie sur une variable accessoire ne doit pas priver d'API tous les
     * utilisateurs de l'application mobile.
     */
    @Test
    void refuse_un_mot_de_passe_trop_court_sans_empecher_le_demarrage() {
        given(users.findByEmail(EMAIL)).willReturn(Optional.empty());

        bootstrap(EMAIL, "court").run(null);

        verify(users, never()).save(any());
    }

    @Test
    void ne_fait_rien_sans_adresse_configuree() {
        bootstrap("", PASSWORD).run(null);

        verify(users, never()).findByEmail(any());
        verify(users, never()).save(any());
    }

    private AdminBootstrap bootstrap(String email, String password) {
        AccessProperties properties = new AccessProperties(
                email, password, EnumSet.of(AppModule.WORKOUTS), Duration.ofSeconds(60));
        return new AdminBootstrap(users, properties, passwordEncoder, moduleAccess);
    }
}
