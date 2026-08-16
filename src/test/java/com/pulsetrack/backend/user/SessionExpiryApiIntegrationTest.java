package com.pulsetrack.backend.user;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ce que le client recoit quand sa session expire.
 *
 * <p>Sans le relais pose dans la chaine de securite, un jeton expire produit un
 * {@code 401} au corps vide : le mobile ne peut alors pas distinguer « ta session
 * a expire » d'une panne reseau, et affiche « une erreur est survenue » dans les
 * deux cas — alors que l'utilisateur n'a rien d'autre a faire que se reconnecter.
 */
class SessionExpiryApiIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private com.pulsetrack.backend.config.SecurityProperties securityProperties;

    @Test
    void nomme_explicitement_une_session_expiree() throws Exception {
        String expired = "Bearer " + expiredToken();

        mockMvc.perform(get("/api/v1/me/modules").header("Authorization", expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://pulsetrack.app/problems/token-expired"))
                .andExpect(jsonPath("$.title").value("Session expiree"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                // L'en-tete exige par la RFC 6750 reste pose : on habille la
                // reponse standard, on ne la remplace pas.
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    void distingue_une_absence_de_jeton_d_une_session_expiree() throws Exception {
        mockMvc.perform(get("/api/v1/me/modules"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://pulsetrack.app/problems/unauthenticated"));
    }

    @Test
    void ne_dit_pas_pourquoi_un_jeton_est_illisible() throws Exception {
        // Expliquer ce qui cloche dans une signature aiderait a en fabriquer une.
        mockMvc.perform(get("/api/v1/me/modules").header("Authorization", "Bearer pas-un-jeton"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://pulsetrack.app/problems/unauthenticated"));
    }

    @Test
    void ne_nomme_pas_le_privilege_manquant_a_qui_n_y_a_pas_droit() throws Exception {
        String token = registerUser();

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://pulsetrack.app/problems/access-denied"));
    }

    /**
     * Fabrique un jeton correctement signe mais dont l'expiration est passee :
     * c'est le seul moyen d'eprouver ce chemin sans attendre vingt-quatre heures.
     */
    private String expiredToken() {
        Instant issuedAt = Instant.now().minus(48, ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(securityProperties.jwt().issuer())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(1, ChronoUnit.HOURS))
                .subject(java.util.UUID.randomUUID().toString())
                .build();

        JwsHeader header = JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
