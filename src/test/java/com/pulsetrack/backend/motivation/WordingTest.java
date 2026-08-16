package com.pulsetrack.backend.motivation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le formatage est fait a la main pour ne dependre d'aucune locale installee sur
 * la machine : ces tests doivent donner le meme resultat ici et sur le serveur.
 */
class WordingTest {

    @Test
    void ecrit_les_courtes_distances_en_metres() {
        assertThat(Wording.distance(850)).isEqualTo("850 m");
        assertThat(Wording.distance(999)).isEqualTo("999 m");
    }

    @Test
    void bascule_en_kilometres_avec_une_virgule_francaise() {
        assertThat(Wording.distance(6_300)).isEqualTo("6,3 km");
        assertThat(Wording.distance(1_000)).isEqualTo("1 km");
    }

    @Test
    void n_affiche_pas_de_decimale_inutile() {
        assertThat(Wording.decimal(4.0, 1)).isEqualTo("4");
        assertThat(Wording.decimal(4.25, 1)).isEqualTo("4,3");
    }

    @Test
    void ecrit_les_durees_sans_unite_nulle_en_tete() {
        assertThat(Wording.duration(45)).isEqualTo("45 s");
        assertThat(Wording.duration(3_280)).isEqualTo("54 min 40 s");
        assertThat(Wording.duration(3_600)).isEqualTo("1 h");
        assertThat(Wording.duration(4_320)).isEqualTo("1 h 12 min");
    }

    @Test
    void n_affiche_pas_les_secondes_au_dela_de_l_heure() {
        // Personne ne lit « 1 h 12 min 03 s ».
        assertThat(Wording.duration(4_323)).isEqualTo("1 h 12 min");
    }

    @Test
    void ecrit_l_allure_au_format_du_chronometre() {
        assertThat(Wording.pace(330)).isEqualTo("5:30/km");
        // Les secondes toujours sur deux chiffres : « 5:3/km » ne se lit pas.
        assertThat(Wording.pace(303)).isEqualTo("5:03/km");
    }

    @Test
    void accorde_le_pluriel_francais_a_partir_de_deux() {
        assertThat(Wording.plural(0, "seance", "seances")).isEqualTo("0 seance");
        assertThat(Wording.plural(1, "seance", "seances")).isEqualTo("1 seance");
        assertThat(Wording.plural(4, "seance", "seances")).isEqualTo("4 seances");
    }

    @Test
    void formate_les_valeurs_negatives_sans_signe_parasite() {
        assertThat(Wording.distance(-1_180)).isEqualTo("1,18 km");
        assertThat(Wording.duration(-60)).isEqualTo("1 min");
    }
}
