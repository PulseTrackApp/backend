package com.pulsetrack.backend.coach;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.pulsetrack.backend.coach.dto.CoachSettingsRequest;
import com.pulsetrack.backend.coach.dto.CoachSettingsResponse;

import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglages de l'assistant et gestion de la cle API.
 *
 * <p>Seul endroit du code ou une cle Gemini existe en clair, et uniquement le
 * temps d'un appel. Concentrer le chiffrement ici evite qu'une cle ne se
 * retrouve, par inadvertance, dans un DTO, un log ou un message d'erreur.
 */
@Service
public class GeminiSettingsService {

    private final GeminiSettingsRepository settings;
    private final TextEncryptor encryptor;
    private final GeminiProperties properties;

    public GeminiSettingsService(GeminiSettingsRepository settings,
                                 TextEncryptor apiKeyEncryptor,
                                 GeminiProperties properties) {
        this.settings = settings;
        this.encryptor = apiKeyEncryptor;
        this.properties = properties;
    }

    /**
     * Reglages du compte, crees a la volee au premier acces.
     *
     * <p>Creation paresseuse plutot qu'a l'inscription : un compte qui n'utilise
     * jamais l'assistant n'a pas besoin d'une ligne dediee.
     */
    @Transactional
    public CoachSettingsResponse getOrCreate(UUID userId) {
        return toResponse(loadOrCreate(userId));
    }

    @Transactional
    public CoachSettingsResponse updatePreferences(UUID userId, CoachSettingsRequest request) {
        GeminiSettings current = loadOrCreate(userId);
        current.updatePreferences(
                request.enabled(),
                request.coachingTone(),
                request.weeklyReviewEnabled(),
                request.effortWarningsEnabled(),
                Instant.now());
        return toResponse(current);
    }

    /**
     * Chiffre puis enregistre la cle, et active l'assistant dans la foulee :
     * prendre la peine de saisir sa cle exprime sans ambiguite l'intention de
     * s'en servir.
     */
    @Transactional
    public CoachSettingsResponse storeApiKey(UUID userId, String apiKey) {
        GeminiSettings current = loadOrCreate(userId);
        Instant now = Instant.now();
        current.storeApiKey(encryptor.encrypt(apiKey.trim()), now);
        current.updatePreferences(true, current.getCoachingTone(),
                current.isWeeklyReviewEnabled(), current.isEffortWarningsEnabled(), now);
        return toResponse(current);
    }

    @Transactional
    public CoachSettingsResponse deleteApiKey(UUID userId) {
        GeminiSettings current = loadOrCreate(userId);
        current.clearApiKey(Instant.now());
        return toResponse(current);
    }

    /**
     * Reglages utilisables, cle en clair comprise.
     *
     * <p>Deux sources possibles, dans cet ordre :
     * <ol>
     *   <li>la cle propre a l'utilisateur, si elle a ete saisie et enregistree ;</li>
     *   <li>a defaut, la cle de niveau serveur issue de l'environnement.</li>
     * </ol>
     *
     * <p>La cle utilisateur prime : quelqu'un qui prend la peine de fournir la
     * sienne veut manifestement utiliser son propre quota.
     *
     * <p>Reserve aux services qui appellent reellement Gemini. Renvoie vide si
     * l'assistant est inactif ou qu'aucune cle n'est disponible, ce qui evite a
     * l'appelant de reconstituer la regle.
     */
    @Transactional(readOnly = true)
    public Optional<ActiveGeminiAccess> activeAccessOf(UUID userId) {
        GeminiSettings current = settings.findById(userId).orElse(null);

        CoachingTone tone = current == null ? CoachingTone.ENCOURAGING : current.getCoachingTone();
        boolean enabled = current == null ? properties.hasServerKey() : current.isEnabled();
        if (!enabled) {
            return Optional.empty();
        }

        if (current != null && current.hasApiKey()) {
            return Optional.of(new ActiveGeminiAccess(
                    encryptor.decrypt(current.getEncryptedApiKey()), tone));
        }
        if (properties.hasServerKey()) {
            return Optional.of(new ActiveGeminiAccess(properties.apiKey(), tone));
        }
        return Optional.empty();
    }

    /** Existence des reglages sans les creer, pour les traitements planifies. */
    @Transactional(readOnly = true)
    public Optional<GeminiSettings> findExisting(UUID userId) {
        return settings.findById(userId);
    }

    private GeminiSettings loadOrCreate(UUID userId) {
        return settings.findById(userId)
                .orElseGet(() -> {
                    GeminiSettings created = new GeminiSettings(userId, Instant.now());
                    // Une cle serveur configuree signifie que l'assistant est
                    // voulu : l'activer d'emblee evite d'obliger l'utilisateur a
                    // cocher une case pour une decision deja prise.
                    if (properties.hasServerKey()) {
                        created.updatePreferences(true, created.getCoachingTone(),
                                created.isWeeklyReviewEnabled(), created.isEffortWarningsEnabled(),
                                Instant.now());
                    }
                    return settings.save(created);
                });
    }

    private CoachSettingsResponse toResponse(GeminiSettings source) {
        boolean keyAvailable = source.hasApiKey() || properties.hasServerKey();
        return new CoachSettingsResponse(
                source.isEnabled(),
                source.hasApiKey(),
                properties.hasServerKey(),
                source.isEnabled() && keyAvailable,
                source.getCoachingTone(),
                source.isWeeklyReviewEnabled(),
                source.isEffortWarningsEnabled());
    }

    /**
     * Cle en clair et ton de coaching, le temps d'un appel a Gemini.
     *
     * <p>Ce type ne doit jamais etre serialise ni journalise.
     *
     * @param apiKey cle dechiffree
     */
    public record ActiveGeminiAccess(String apiKey, CoachingTone tone) {
    }
}
