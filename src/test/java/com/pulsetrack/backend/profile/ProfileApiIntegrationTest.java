package com.pulsetrack.backend.profile;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat du profil sportif : creation, relecture, remplacement et validation.
 */
class ProfileApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void enregistre_puis_relit_le_profil_avec_l_imc_calcule() throws Exception {
        String token = registerUser();

        mockMvc.perform(put("/api/v1/me/profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(178, 72.5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Nicolas"))
                // 72,5 / 1,78^2 = 22,88 -> arrondi a 22,9
                .andExpect(jsonPath("$.bmi").value(22.9));

        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeightKg").value(72.5))
                .andExpect(jsonPath("$.preferredSports.length()").value(2));
    }

    @Test
    void remplace_le_profil_au_second_appel_sans_creer_de_doublon() throws Exception {
        String token = registerUser();

        mockMvc.perform(put("/api/v1/me/profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(178, 72.5)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/me/profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(178, 70.0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeightKg").value(70.0));

        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeightKg").value(70.0));
    }

    @Test
    void repond_404_tant_que_le_profil_n_est_pas_renseigne() throws Exception {
        String token = registerUser();

        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Ressource introuvable"));
    }

    @Test
    void refuse_une_taille_aberrante() throws Exception {
        String token = registerUser();

        mockMvc.perform(put("/api/v1/me/profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(12, 72.5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.heightCm").isNotEmpty());
    }

    @Test
    void refuse_un_profil_sans_sport_prefere() throws Exception {
        String token = registerUser();

        String body = """
                {
                  "displayName": "Nicolas",
                  "heightCm": 178,
                  "currentWeightKg": 72.5,
                  "primaryGoal": "LOSE_WEIGHT",
                  "fitnessLevel": "BEGINNER",
                  "preferredSports": []
                }
                """;

        mockMvc.perform(put("/api/v1/me/profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.preferredSports").isNotEmpty());
    }

    @Test
    void ne_montre_jamais_le_profil_d_un_autre_compte() throws Exception {
        String alice = registerUser();
        saveProfile(alice, 72.5);

        // Bob n'a pas de profil : il ne doit surtout pas voir celui d'Alice.
        String bob = registerUser();
        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", bob))
                .andExpect(status().isNotFound());
    }

    private String profileBody(int heightCm, double weightKg) {
        return """
                {
                  "displayName": "Nicolas",
                  "heightCm": %d,
                  "currentWeightKg": %s,
                  "birthDate": "1995-04-12",
                  "sex": "MALE",
                  "primaryGoal": "IMPROVE_ENDURANCE",
                  "fitnessLevel": "INTERMEDIATE",
                  "preferredSports": ["RUN", "WALK"]
                }
                """.formatted(heightCm, weightKg);
    }
}
