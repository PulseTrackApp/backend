package com.pulsetrack.backend.access;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.pulsetrack.backend.common.ratelimit.MutableClock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Regles d'acces et cache, sans Spring ni base.
 */
class ModuleAccessServiceTest {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final UserModuleRepository repository = mock(UserModuleRepository.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-11T10:00:00Z"));
    private final ModuleAccessService service = new ModuleAccessService(
            repository,
            new AccessProperties("", EnumSet.of(AppModule.WORKOUTS, AppModule.STATS), CACHE_TTL),
            clock);

    @Test
    void un_administrateur_a_tout_sans_consulter_la_base() {
        assertThat(service.enabledFor(USER, true)).containsExactlyElementsOf(EnumSet.allOf(AppModule.class));
        verify(repository, never()).findModulesByUserId(any());
    }

    @Test
    void un_utilisateur_ordinaire_a_ce_que_la_base_lui_accorde() {
        given(repository.findModulesByUserId(USER)).willReturn(Set.of(AppModule.GOALS));

        assertThat(service.isEnabled(USER, false, AppModule.GOALS)).isTrue();
        assertThat(service.isEnabled(USER, false, AppModule.COACH)).isFalse();
    }

    /**
     * Le cas qui casserait une implementation naive : {@code EnumSet.copyOf}
     * refuse une collection vide, faute de pouvoir en deduire le type. Un compte
     * prive de tous ses modules est pourtant parfaitement legitime — c'est meme
     * le geste le plus radical de l'ecran d'administration.
     */
    @Test
    void supporte_un_compte_prive_de_tous_ses_modules() {
        given(repository.findModulesByUserId(USER)).willReturn(Set.of());

        assertThat(service.enabledFor(USER, false)).isEmpty();
    }

    @Test
    void ne_relit_pas_la_base_pendant_la_duree_du_cache() {
        given(repository.findModulesByUserId(USER)).willReturn(Set.of(AppModule.STATS));

        service.enabledFor(USER, false);
        clock.advanceBy(CACHE_TTL.minusSeconds(1));
        service.enabledFor(USER, false);

        verify(repository, times(1)).findModulesByUserId(USER);
    }

    @Test
    void relit_la_base_une_fois_le_cache_perime() {
        given(repository.findModulesByUserId(USER)).willReturn(Set.of(AppModule.STATS));

        service.enabledFor(USER, false);
        clock.advanceBy(CACHE_TTL);
        service.enabledFor(USER, false);

        verify(repository, times(2)).findModulesByUserId(USER);
    }

    /**
     * Sans cet oubli, un droit retire par un administrateur resterait accorde
     * jusqu'a l'expiration du cache, et le geste semblerait sans effet.
     */
    @Test
    void oublie_le_cache_quand_les_droits_changent() {
        given(repository.findModulesByUserId(USER)).willReturn(Set.of(AppModule.STATS));
        service.enabledFor(USER, false);

        service.evict(USER);
        service.enabledFor(USER, false);

        verify(repository, times(2)).findModulesByUserId(USER);
    }
}
