package com.pulsetrack.backend.coach;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration de l'appel a l'API Gemini.
 *
 * <p>Aucune cle ici : elle appartient a l'utilisateur et vit chiffree en base.
 * Ce fichier ne decrit que le « ou » et le « comment » de l'appel.
 *
 * @param baseUrl         racine de l'API Google Generative Language
 * @param model           nom du modele ; configurable car les noms evoluent vite
 *                        et un renommage cote Google ne doit pas imposer de
 *                        recompiler l'application
 * @param timeout         delai au-dela duquel on abandonne l'appel
 * @param maxOutputTokens plafond de longueur de reponse, qui borne aussi ce que
 *                        l'appel coute a l'utilisateur
 * @param thinkingBudget  jetons alloues au raisonnement interne du modele.
 *                        <p>{@code 0} le desactive, et c'est le reglage voulu :
 *                        ce raisonnement se preleve sur {@code maxOutputTokens},
 *                        et il en consommait 860 sur 900 le 11 aout 2026 — la
 *                        reponse etait coupee au bout de trente-six jetons, en
 *                        plein milieu d'une phrase. Un conseil sportif de cent
 *                        cinquante mots n'a aucun besoin de reflexion en
 *                        chaine.
 *                        <p>Une valeur negative n'envoie pas le reglage du tout,
 *                        pour un futur modele qui le refuserait.
 * @param apiKey          cle du serveur, fournie par variable d'environnement
 *                        {@code PULSETRACK_GEMINI_API_KEY}. Vide par defaut :
 *                        l'assistant retombe alors sur la cle propre a chaque
 *                        utilisateur
 */
@ConfigurationProperties(prefix = "pulsetrack.gemini")
@Validated
public record GeminiProperties(
        @NotBlank String baseUrl,
        @NotBlank String model,
        @NotNull Duration timeout,
        @Positive int maxOutputTokens,
        int thinkingBudget,
        String apiKey) {

    /**
     * @return {@code true} si le reglage doit accompagner la requete. Une valeur
     *         negative le fait taire entierement, ce qui laisse l'API decider
     *         comme avant.
     */
    public boolean sendsThinkingBudget() {
        return thinkingBudget >= 0;
    }

    /**
     * Une cle de niveau serveur est-elle configuree ?
     *
     * <p>Quand c'est le cas, l'assistant fonctionne sans qu'aucune cle ne soit
     * saisie ni stockee en base — c'est le mode le plus simple et le plus sur
     * pour un usage personnel : la cle ne quitte jamais l'environnement du
     * serveur.
     */
    public boolean hasServerKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
