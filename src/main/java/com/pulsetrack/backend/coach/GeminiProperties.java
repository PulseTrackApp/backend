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
        String apiKey) {

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
