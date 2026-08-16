package com.pulsetrack.backend.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La comparaison de versions, sur laquelle repose le verrou des anciennes
 * applications.
 *
 * <p>Le piege classique est la comparaison lexicographique : elle ferait passer
 * « 1.10.0 » pour anterieur a « 1.9.0 », et le verrou laisserait entrer
 * exactement les versions qu'il doit arreter.
 */
class ClientVersionTest {

    @Test
    void compare_numeriquement_et_non_alphabetiquement() {
        ClientVersion ten = ClientVersion.parse("1.10.0").orElseThrow();
        ClientVersion nine = ClientVersion.parse("1.9.0").orElseThrow();

        assertThat(ten).isGreaterThan(nine);
        assertThat(ten.isAtLeast(nine)).isTrue();
        assertThat(nine.isAtLeast(ten)).isFalse();
    }

    @Test
    void complete_les_composantes_absentes_par_zero() {
        assertThat(ClientVersion.parse("2")).contains(new ClientVersion(2, 0, 0));
        assertThat(ClientVersion.parse("2.1")).contains(new ClientVersion(2, 1, 0));
        assertThat(ClientVersion.parse("2.1.3")).contains(new ClientVersion(2, 1, 3));
    }

    @Test
    void ignore_un_suffixe_de_pre_publication() {
        // Un canal beta ne doit pas se faire refuser pour une raison de format.
        assertThat(ClientVersion.parse("1.4.0-beta.2")).contains(new ClientVersion(1, 4, 0));
        assertThat(ClientVersion.parse("1.4.0+42")).contains(new ClientVersion(1, 4, 0));
    }

    @Test
    void rend_vide_sur_une_valeur_illisible() {
        assertThat(ClientVersion.parse(null)).isEmpty();
        assertThat(ClientVersion.parse("")).isEmpty();
        assertThat(ClientVersion.parse("   ")).isEmpty();
        assertThat(ClientVersion.parse("version 3")).isEmpty();
        assertThat(ClientVersion.parse("1.2.3.4")).isEmpty();
        assertThat(ClientVersion.parse("-1.0.0")).isEmpty();
    }

    @Test
    void une_version_egale_au_minimum_est_acceptee() {
        ClientVersion minimum = ClientVersion.parse("1.5.0").orElseThrow();

        assertThat(minimum.isAtLeast(minimum)).isTrue();
        assertThat(ClientVersion.parse("1.5.1").orElseThrow().isAtLeast(minimum)).isTrue();
        assertThat(ClientVersion.parse("1.4.9").orElseThrow().isAtLeast(minimum)).isFalse();
    }

    @Test
    void un_minimum_absent_n_arrete_personne() {
        assertThat(ClientVersion.parseOrZero(null)).isEqualTo(ClientVersion.ZERO);
        assertThat(ClientVersion.parse("0.0.1").orElseThrow().isAtLeast(ClientVersion.ZERO)).isTrue();
    }

    @Test
    void s_ecrit_toujours_en_trois_composantes() {
        assertThat(ClientVersion.parse("2.1").orElseThrow()).hasToString("2.1.0");
    }
}
