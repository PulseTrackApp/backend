package com.pulsetrack.backend.coach;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import com.pulsetrack.backend.common.error.BusinessRuleException;
import com.pulsetrack.backend.common.error.ExternalServiceException;
import com.pulsetrack.backend.common.error.RateLimitedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Appelle l'API Google Generative Language pour le compte d'un utilisateur.
 *
 * <p>Sur {@code RestClient} et non {@code RestTemplate}, conformement a la
 * direction prise par Spring Framework 6+.
 *
 * <p>Aucune cle n'est conservee dans cette classe : elle est passee a chaque
 * appel et n'est jamais journalisee.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    /**
     * Temperature moderee : on veut des conseils stables et reproductibles d'une
     * semaine sur l'autre, pas de la creativite litteraire.
     */
    private static final double TEMPERATURE = 0.6;

    /** Etablissement de la connexion : court, l'API est soit joignable, soit non. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final GeminiProperties properties;

    public GeminiClient(RestClient.Builder builder, GeminiProperties properties) {
        this.properties = properties;
        // Un timeout explicite est indispensable : sans lui, un appel qui ne
        // repond jamais bloquerait un thread du serveur indefiniment.
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(timeoutAwareRequestFactory(properties.timeout()))
                .build();
    }

    private JdkClientHttpRequestFactory timeoutAwareRequestFactory(Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    /**
     * @param apiKey            cle de l'utilisateur, en clair le temps de l'appel
     * @param systemInstruction cadre de reponse (role, garde-fous, ton)
     * @param userPrompt        donnees de la semaine et question posee
     * @return le texte produit par le modele
     * @throws BusinessRuleException    si la cle est refusee : c'est a
     *                                  l'utilisateur de la corriger
     * @throws RateLimitedException     si son quota est epuise
     * @throws ExternalServiceException si Gemini est injoignable ou repond mal
     */
    public String generate(String apiKey, String systemInstruction, String userPrompt) {
        GenerateContentRequest request = new GenerateContentRequest(
                List.of(new Content(List.of(new Part(userPrompt)))),
                new Content(List.of(new Part(systemInstruction))),
                generationConfig());

        GenerateContentResponse response;
        try {
            response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.model())
                    // Cle en en-tete et non en parametre d'URL : une URL finit
                    // dans les journaux d'acces, un en-tete beaucoup moins.
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw translate(res.getStatusCode());
                    })
                    .body(GenerateContentResponse.class);
        } catch (ResourceAccessException ex) {
            // Timeout ou reseau coupe : l'utilisateur doit savoir que ce n'est
            // pas sa faute et qu'il peut reessayer.
            throw new ExternalServiceException("L'assistant n'a pas repondu dans le delai imparti.", ex);
        }

        return extractText(response);
    }

    /**
     * Reglages d'inference.
     *
     * <p>Le budget de reflexion se preleve sur le plafond de sortie. Laisse
     * libre, il l'epuisait presque entierement — 860 jetons sur 900 le 11 aout
     * 2026 — et la reponse etait tronquee en plein milieu d'une phrase. Un
     * conseil de cent cinquante mots n'a pas besoin de reflexion en chaine :
     * la couper rend le plafond a ce qui est reellement affiche.
     *
     * <p>Omis quand le reglage est negatif, pour un modele futur qui le
     * refuserait. Jackson ne serialise pas les champs nuls, la clef disparait
     * alors du corps envoye.
     */
    GenerationConfig generationConfig() {
        ThinkingConfig thinking = properties.sendsThinkingBudget()
                ? new ThinkingConfig(properties.thinkingBudget())
                : null;
        return new GenerationConfig(TEMPERATURE, properties.maxOutputTokens(), thinking);
    }

    /**
     * Traduit l'echec du fournisseur en erreur presentable.
     *
     * <p>Aucun de ces messages ne nomme le fournisseur, et c'est delibere : rien
     * de ce que voit un client ne doit reveler quel service alimente l'assistant.
     * Le nom reste dans les journaux du serveur, ou il est utile a qui exploite
     * l'application et invisible a tous les autres.
     */
    private RuntimeException translate(HttpStatusCode status) {
        if (status.value() == 401 || status.value() == 403) {
            return new BusinessRuleException(
                    "Votre cle API a ete refusee. Verifiez-la dans les parametres.");
        }
        if (status.value() == 429) {
            return new RateLimitedException(
                    "Le quota de votre cle est atteint. Reessayez plus tard.");
        }
        // Le detail technique reste dans les journaux, pas dans la reponse.
        log.warn("Reponse en erreur de Gemini : statut {}", status.value());
        return new ExternalServiceException("L'assistant a repondu par une erreur.");
    }

    /**
     * Une reponse sans candidat arrive quand le modele s'arrete sur un filtre de
     * securite. Mieux vaut le dire que renvoyer une chaine vide qui passerait
     * pour un conseil.
     */
    private String extractText(GenerateContentResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new ExternalServiceException("L'assistant n'a produit aucune reponse exploitable.");
        }

        Candidate candidate = response.candidates().get(0);
        if (candidate.content() == null || candidate.content().parts() == null
                || candidate.content().parts().isEmpty()) {
            throw new ExternalServiceException("L'assistant n'a produit aucune reponse exploitable.");
        }

        String text = candidate.content().parts().stream()
                .map(Part::text)
                .filter(part -> part != null && !part.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

        if (text.isBlank()) {
            throw new ExternalServiceException("L'assistant n'a produit aucune reponse exploitable.");
        }
        return text.trim();
    }

    // --- Structures de l'API Gemini -----------------------------------------
    // Records volontairement minimalistes : on ne modelise que les champs
    // utilises. Jackson ignore le reste, ce qui evite de casser a chaque
    // enrichissement de l'API par Google.

    record GenerateContentRequest(List<Content> contents,
                                  Content systemInstruction,
                                  GenerationConfig generationConfig) {
    }

    record Content(List<Part> parts) {
    }

    record Part(String text) {
    }

    record GenerationConfig(double temperature, int maxOutputTokens, ThinkingConfig thinkingConfig) {
    }

    record ThinkingConfig(int thinkingBudget) {
    }

    record GenerateContentResponse(List<Candidate> candidates) {
    }

    record Candidate(Content content, String finishReason) {
    }
}
