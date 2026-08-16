package com.pulsetrack.backend.profile;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La protection du profil contre l'ecrasement silencieux.
 *
 * <p>Le probleme d'origine : {@code PUT} remplace tout, et un ecran qui ne
 * corrige qu'un champ efface au passage la date de naissance et le sexe — les
 * deux seuls champs facultatifs, donc les seuls que la validation ne protege pas.
 */
class ProfilePatchApiIntegrationTest extends AbstractApiIntegrationTest {

    private static final String COMPLET = """
            {
              "displayName": "Nicolas",
              "heightCm": 178,
              "currentWeightKg": 78.0,
              "birthDate": "1995-04-12",
              "sex": "MALE",
              "primaryGoal": "IMPROVE_ENDURANCE",
              "fitnessLevel": "INTERMEDIATE",
              "preferredSports": ["RUN", "WALK"]
            }
            """;

    @Test
    void un_remplacement_incomplet_efface_les_champs_facultatifs() throws Exception {
        String token = registerUser();
        save(token, COMPLET);

        // Exactement ce que fait un ecran « modifier mon poids » ecrit sans y
        // penser : tous les champs obligatoires sont la, la validation passe.
        save(token, """
                {
                  "displayName": "Nicolas",
                  "heightCm": 178,
                  "currentWeightKg": 80.0,
                  "primaryGoal": "IMPROVE_ENDURANCE",
                  "fitnessLevel": "INTERMEDIATE",
                  "preferredSports": ["RUN"]
                }
                """);

        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeightKg").value(80.0))
                // La perte silencieuse que PATCH existe pour eviter.
                .andExpect(jsonPath("$.birthDate").doesNotExist())
                .andExpect(jsonPath("$.sex").doesNotExist());
    }

    @Test
    void une_modification_partielle_preserve_tout_le_reste() throws Exception {
        String token = registerUser();
        save(token, COMPLET);

        mockMvc.perform(patch("/api/v1/me/profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentWeightKg": 80.5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeightKg").value(80.5))
                .andExpect(jsonPath("$.birthDate").value("1995-04-12"))
                .andExpect(jsonPath("$.sex").value("MALE"))
                .andExpect(jsonPath("$.displayName").value("Nicolas"))
                .andExpect(jsonPath("$.heightCm").value(178))
                // Le piege du code : la collection est videe avant d'etre
                // remplie. Sans copie defensive, les sports disparaitraient a
                // chaque modification qui ne les mentionne pas.
                .andExpect(jsonPath("$.preferredSports.length()").value(2));
    }

    @Test
    void modifie_les_sports_sans_toucher_au_reste() throws Exception {
        String token = registerUser();
        save(token, COMPLET);

        mockMvc.perform(patch("/api/v1/me/profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredSports": ["RIDE"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredSports.length()").value(1))
                .andExpect(jsonPath("$.preferredSports[0]").value("RIDE"))
                .andExpect(jsonPath("$.birthDate").value("1995-04-12"));
    }

    @Test
    void refuse_une_modification_vide() throws Exception {
        String token = registerUser();
        save(token, COMPLET);

        // Repondre 200 laisserait croire a une modification enregistree.
        mockMvc.perform(patch("/api/v1/me/profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Aucune modification demandée."));
    }

    @Test
    void refuse_un_nom_affiche_vide() throws Exception {
        String token = registerUser();
        save(token, COMPLET);

        mockMvc.perform(patch("/api/v1/me/profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "   "}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void refuse_une_valeur_aberrante_comme_le_remplacement_complet() throws Exception {
        String token = registerUser();
        save(token, COMPLET);

        mockMvc.perform(patch("/api/v1/me/profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentWeightKg": 4.0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refuse_de_modifier_un_profil_qui_n_existe_pas() throws Exception {
        String token = registerUser();

        // Creer un profil a moitie rempli laisserait un poids a zero, qui
        // fausserait toutes les estimations de calories.
        mockMvc.perform(patch("/api/v1/me/profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentWeightKg": 80.0}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void un_remplacement_complet_reste_le_moyen_d_effacer_un_champ() throws Exception {
        String token = registerUser();
        save(token, COMPLET);

        // PATCH ne sait pas effacer : c'est un compromis assume, et PUT reste
        // le geste explicite.
        save(token, """
                {
                  "displayName": "Nicolas",
                  "heightCm": 178,
                  "currentWeightKg": 78.0,
                  "primaryGoal": "IMPROVE_ENDURANCE",
                  "fitnessLevel": "INTERMEDIATE",
                  "preferredSports": ["RUN"]
                }
                """);

        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", token))
                .andExpect(jsonPath("$.birthDate").doesNotExist());
    }

    private void save(String token, String body) throws Exception {
        mockMvc.perform(put("/api/v1/me/profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
