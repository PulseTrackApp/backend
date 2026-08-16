package com.pulsetrack.backend.common.error;

import java.nio.charset.StandardCharsets;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Garde-fou d'encodage : les accents doivent traverser l'API intacts.
 *
 * <p><strong>Pourquoi ce test existe.</strong> Les textes rendus par l'API sont
 * en francais accentue. Un accent perdu ne fait echouer aucun autre test — il
 * produit « Defi releve » ou « Dï¿½fi relevï¿½ » et personne ne s'en apercoit
 * avant de le voir a l'ecran. Il suffit d'un encodage par defaut mal pose, dans
 * le build, dans la JVM ou dans la serialisation JSON, pour que tout le francais
 * de l'application se degrade d'un coup.
 *
 * <p>Le test verifie les trois etapes ou cela peut casser : la lecture des
 * sources a la compilation, l'ecriture du JSON, et la lecture de la
 * configuration YAML.
 */
class AccentEncodingApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void un_message_d_erreur_conserve_ses_accents_jusqu_au_client() throws Exception {
        String token = registerUser();

        // Le profil n'existe pas : le message porte « renseigné », « séance ».
        String body = mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sportType": "RUN", "startedAt": "2026-08-10T06:00:00Z",
                                 "endedAt": "2026-08-10T06:30:00Z", "distanceMeters": 5000}
                                """))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        // Sequences d'echappement Unicode, et non lettres accentuees : ecrire
        // « séance » ici ferait passer le test meme si le compilateur relisait
        // mal les sources, puisque l'attendu serait deforme exactement comme le
        // produit. L'echappement, lui, ne depend d'aucun encodage de fichier.
        assertThat(body).contains("avant d'enregistrer une s\u00e9ance");
        // U+FFFD est le caractere de remplacement : sa presence signe un octet
        // perdu en chemin.
        assertThat(body).doesNotContain("\ufffd");
    }

    @Test
    void les_libelles_du_catalogue_de_tarifs_survivent_au_yaml() throws Exception {
        String token = registerUser();

        // Ceux-la viennent de application.yml, pas d'un fichier source Java :
        // c'est un chemin d'encodage different, et il casse independamment.
        mockMvc.perform(get("/api/v1/billing/plans").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].features[0]").value("S\u00e9ances illimit\u00e9es et trace GPS"));
    }

    @Test
    void un_libelle_d_enumeration_conserve_ses_accents() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        // « course à pied » vient du libelle porte par SportType, compose dans
        // le message de felicitation.
        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sportType": "RUN", "startedAt": "2026-08-10T06:00:00Z",
                                 "endedAt": "2026-08-10T06:30:00Z", "distanceMeters": 5000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.achievements[0].message")
                        .value("Ta premi\u00e8re s\u00e9ance de course \u00e0 pied est enregistr\u00e9e. Tout part de l\u00e0."));
    }
}
