package com.pulsetrack.backend.coach;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulsetrack.backend.bodycheckin.BodyCheckInService;
import com.pulsetrack.backend.coach.dto.CoachMessageResponse;
import com.pulsetrack.backend.common.error.BusinessRuleException;
import com.pulsetrack.backend.profile.ProfileService;
import com.pulsetrack.backend.summary.WeeklySummaryService;
import com.pulsetrack.backend.summary.dto.WeeklySummaryResponse;
import com.pulsetrack.backend.workout.WorkoutService;
import com.pulsetrack.backend.workout.dto.WorkoutSummaryResponse;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assistant de coaching : rassemble le contexte, interroge Gemini, conserve la
 * reponse.
 *
 * <p>L'application doit rester utilisable sans Gemini, comme l'exige la spec :
 * aucune autre fonctionnalite n'appelle ce service, et lui-meme echoue
 * proprement quand l'assistant n'est pas configure.
 */
@Service
public class CoachService {

    private static final Pageable RECENT_SESSIONS =
            PageRequest.of(0, CoachPromptBuilder.RECENT_SESSIONS_IN_PROMPT,
                    Sort.by(Sort.Direction.DESC, "startedAt"));

    private final GeminiSettingsService settingsService;
    private final GeminiClient geminiClient;
    private final GeminiProperties geminiProperties;
    private final CoachPromptBuilder promptBuilder;
    private final CoachMessageRepository messages;
    private final ProfileService profileService;
    private final WeeklySummaryService weeklySummaryService;
    private final BodyCheckInService bodyCheckInService;
    private final WorkoutService workoutService;

    public CoachService(GeminiSettingsService settingsService,
                        GeminiClient geminiClient,
                        GeminiProperties geminiProperties,
                        CoachPromptBuilder promptBuilder,
                        CoachMessageRepository messages,
                        ProfileService profileService,
                        WeeklySummaryService weeklySummaryService,
                        BodyCheckInService bodyCheckInService,
                        WorkoutService workoutService) {
        this.settingsService = settingsService;
        this.geminiClient = geminiClient;
        this.geminiProperties = geminiProperties;
        this.promptBuilder = promptBuilder;
        this.messages = messages;
        this.profileService = profileService;
        this.weeklySummaryService = weeklySummaryService;
        this.bodyCheckInService = bodyCheckInService;
        this.workoutService = workoutService;
    }

    /**
     * Bilan de la semaine.
     *
     * <p>Un bilan deja produit est renvoye tel quel : chaque appel consomme le
     * quota de la cle de l'utilisateur, et ouvrir son dashboard cinq fois dans la
     * journee ne doit pas le facturer cinq fois. {@code refresh} force une
     * nouvelle generation.
     *
     * @throws BusinessRuleException si l'assistant n'est pas configure
     */
    @Transactional
    public CoachMessageResponse weeklyReview(UUID userId, LocalDate weekStart, ZoneId zone, boolean refresh) {
        GeminiSettingsService.ActiveGeminiAccess access = requireUsableAssistant(userId);

        CoachContext context = buildContext(userId, weekStart, zone);
        LocalDate week = context.week().weekStart();

        Optional<CoachMessage> existing =
                messages.findByUserIdAndKindAndWeekStart(userId, CoachMessageKind.WEEKLY_REVIEW, week);
        if (existing.isPresent() && !refresh) {
            return toResponse(existing.get(), true);
        }

        String prompt = promptBuilder.weeklyReviewPrompt(context);
        String content = geminiClient.generate(
                access.apiKey(), promptBuilder.systemInstruction(access.tone()), prompt);

        Instant now = Instant.now();
        CoachMessage saved = existing
                .map(message -> {
                    message.replaceContent(prompt, content, geminiProperties.model(), now);
                    return message;
                })
                .orElseGet(() -> messages.save(new CoachMessage(userId, CoachMessageKind.WEEKLY_REVIEW,
                        week, prompt, content, geminiProperties.model(), now)));

        return toResponse(saved, false);
    }

    /**
     * Question libre, toujours accompagnee du contexte sportif.
     *
     * <p>Jamais mise en cache : deux questions differentes appellent deux
     * reponses, et la meme question posee une semaine plus tard porte sur des
     * donnees differentes.
     */
    @Transactional
    public CoachMessageResponse ask(UUID userId, String question, ZoneId zone) {
        GeminiSettingsService.ActiveGeminiAccess access = requireUsableAssistant(userId);

        CoachContext context = buildContext(userId, null, zone);
        String prompt = promptBuilder.freeQuestionPrompt(context, question.trim());
        String content = geminiClient.generate(
                access.apiKey(), promptBuilder.systemInstruction(access.tone()), prompt);

        CoachMessage saved = messages.save(new CoachMessage(userId, CoachMessageKind.FREE_QUESTION,
                null, prompt, content, geminiProperties.model(), Instant.now()));
        return toResponse(saved, false);
    }

    /**
     * Dernier conseil connu, pour le dashboard.
     *
     * <p>Ne declenche aucun appel a Gemini et ne demande pas que l'assistant soit
     * configure : un conseil produit hier reste lisible aujourd'hui.
     */
    @Transactional(readOnly = true)
    public Optional<CoachMessageResponse> latest(UUID userId) {
        return messages.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(message -> toResponse(message, true));
    }

    /** Historique complet des conseils, pour l'export des donnees. */
    @Transactional(readOnly = true)
    public List<CoachMessageResponse> exportAll(UUID userId) {
        return messages.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(message -> toResponse(message, true))
                .toList();
    }

    /**
     * Verifie que l'assistant est utilisable et renvoie la cle dechiffree.
     *
     * @throws BusinessRuleException avec un message actionnable ; le client sait
     *                               alors qu'il doit rediriger vers les parametres
     */
    private GeminiSettingsService.ActiveGeminiAccess requireUsableAssistant(UUID userId) {
        return settingsService.activeAccessOf(userId)
                .orElseThrow(() -> new BusinessRuleException(
                        "L'assistant Gemini n'est pas configure. Ajoutez votre cle API dans les parametres."));
    }

    private CoachContext buildContext(UUID userId, LocalDate weekStart, ZoneId zone) {
        // Le profil porte le poids, la taille et l'objectif : sans lui, le conseil
        // se resumerait a des generalites.
        var profile = profileService.getByUserId(userId);
        WeeklySummaryResponse week = weeklySummaryService.summarize(userId, weekStart, zone);
        var body = bodyCheckInService.progress(userId);
        List<WorkoutSummaryResponse> recent =
                workoutService.list(userId, null, RECENT_SESSIONS).getContent();

        return new CoachContext(profile, week, body, recent);
    }

    private CoachMessageResponse toResponse(CoachMessage message, boolean fromCache) {
        return new CoachMessageResponse(
                message.getId(),
                message.getKind(),
                message.getWeekStart(),
                message.getContent(),
                message.getModel(),
                message.getCreatedAt(),
                fromCache);
    }
}
