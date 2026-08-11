package com.pulsetrack.backend.coach;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglages d'inference envoyes au fournisseur, sans Spring ni appel reseau.
 *
 * <p>Le comportement teste ici a une histoire : le 11 aout 2026, la reflexion
 * interne du modele consommait 860 des 900 jetons de sortie et le conseil
 * s'arretait au bout de trente-six, en plein milieu d'une phrase. Rien dans
 * l'application ne le signalait — la reponse partait, simplement tronquee.
 */
class GeminiGenerationConfigTest {

    @Test
    void coupe_la_reflexion_pour_rendre_le_plafond_a_la_reponse() {
        GeminiClient.GenerationConfig config = clientWithThinkingBudget(0).generationConfig();

        assertThat(config.thinkingConfig()).isNotNull();
        assertThat(config.thinkingConfig().thinkingBudget()).isZero();
        assertThat(config.maxOutputTokens()).isEqualTo(900);
    }

    @Test
    void transmet_un_budget_de_reflexion_non_nul_quand_il_est_demande() {
        GeminiClient.GenerationConfig config = clientWithThinkingBudget(256).generationConfig();

        assertThat(config.thinkingConfig().thinkingBudget()).isEqualTo(256);
    }

    /**
     * Porte de sortie pour un modele futur qui refuserait le reglage : la clef
     * disparait alors du corps envoye, Jackson ne serialisant pas les nuls.
     */
    @Test
    void n_envoie_aucun_reglage_de_reflexion_quand_le_budget_est_negatif() {
        GeminiClient.GenerationConfig config = clientWithThinkingBudget(-1).generationConfig();

        assertThat(config.thinkingConfig()).isNull();
    }

    private GeminiClient clientWithThinkingBudget(int budget) {
        GeminiProperties properties = new GeminiProperties(
                "https://exemple.invalid", "un-modele", Duration.ofSeconds(30), 900, budget, "");
        return new GeminiClient(RestClient.builder(), properties);
    }
}
