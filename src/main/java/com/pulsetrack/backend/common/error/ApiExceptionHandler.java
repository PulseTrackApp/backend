package com.pulsetrack.backend.common.error;

import java.net.URI;
import java.time.DateTimeException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.pulsetrack.backend.access.ModuleLockedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduit les exceptions en reponses {@link ProblemDetail} (RFC 9457), pour que
 * le client mobile analyse une erreur sans connaitre sa cause technique.
 *
 * <p>Herite de {@link ResponseEntityExceptionHandler} afin de conserver la
 * gestion des exceptions standard de Spring MVC, et l'enrichit des cas metier.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String PROBLEM_BASE = "https://pulsetrack.app/problems/";

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Ressource introuvable", ex.getMessage(), "not-found");
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Conflit", ex.getMessage(), "conflict");
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Regle metier non respectee",
                ex.getMessage(), "business-rule");
    }

    /**
     * Le module bloque est expose en propriete a part, et pas seulement dans le
     * texte du message : le client doit pouvoir reagir a la fonctionnalite
     * concernee sans analyser une phrase en francais, qui changera.
     *
     * <p>C'est aussi ce qui distingue ce refus d'un {@code 403} ordinaire, les
     * deux partageant le meme code HTTP.
     */
    @ExceptionHandler(ModuleLockedException.class)
    ProblemDetail handleModuleLocked(ModuleLockedException ex) {
        ProblemDetail body = problem(HttpStatus.FORBIDDEN, "Fonctionnalite non activee",
                ex.getMessage(), "module-locked");
        body.setProperty("module", ex.module().name());
        return body;
    }

    /**
     * L'en-tete {@code Retry-After} est renseigne quand le delai est connu : il
     * indique au client la seconde a partir de laquelle retenter, plutot que de
     * le laisser marteler l'endpoint.
     */
    @ExceptionHandler(RateLimitedException.class)
    ResponseEntity<ProblemDetail> handleRateLimited(RateLimitedException ex) {
        ProblemDetail body = problem(HttpStatus.TOO_MANY_REQUESTS, "Quota atteint",
                ex.getMessage(), "rate-limited");

        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        // Arrondi au superieur : annoncer 0 seconde inviterait a retenter
        // immediatement, pour se faire refuser a nouveau.
        ex.retryAfter().ifPresent(delay ->
                response.header(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, ceilSeconds(delay)))));
        return response.body(body);
    }

    /**
     * Le message de l'exception est ici volontairement expose : il est redige
     * pour l'utilisateur (« Gemini n'a pas repondu »), et ne contient jamais de
     * detail technique du service tiers.
     */
    @ExceptionHandler(ExternalServiceException.class)
    ProblemDetail handleExternalService(ExternalServiceException ex) {
        log.warn("Appel a un service tiers en echec : {}", ex.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "Service indisponible", ex.getMessage(), "external-service");
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        // Message volontairement vague : distinguer "email inconnu" de "mot de
        // passe faux" permettrait d'enumerer les comptes existants.
        return problem(HttpStatus.UNAUTHORIZED, "Authentification refusee",
                "Email ou mot de passe incorrect.", "bad-credentials");
    }

    /**
     * Le detail est volontairement le meme quelle que soit la raison du refus
     * (jeton inconnu, expire, revoque) : le distinguer permettrait de tester des
     * jetons au hasard et de savoir lesquels ont existe.
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Session expiree",
                "Session expiree, veuillez vous reconnecter.", "invalid-refresh-token");
    }

    /**
     * Le detail est le meme pour un code inconnu, expire ou deja consomme : les
     * distinguer apprendrait a un attaquant lesquels de ses essais ont existe.
     */
    @ExceptionHandler(InvalidResetCodeException.class)
    ProblemDetail handleInvalidResetCode(InvalidResetCodeException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Code invalide",
                "Ce code de reinitialisation est invalide ou a expire.", "invalid-reset-code");
    }

    /**
     * Valeur temporelle irrecevable, typiquement un fuseau horaire inconnu passe
     * en parametre de requete. C'est une faute du client, pas du serveur : sans
     * ce cas, {@code ZoneId.of("Mars/Olympus")} finirait en 500.
     */
    @ExceptionHandler(DateTimeException.class)
    ProblemDetail handleDateTime(DateTimeException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Requete invalide",
                "Date ou fuseau horaire invalide.", "invalid-datetime");
    }

    /**
     * Autorisation refusee sur une ressource dont l'existence n'est pas secrete.
     *
     * <p>Sans ce cas explicite, l'{@code AccessDeniedException} levee par un
     * futur {@code @PreAuthorize} serait attrapee par le filet a
     * {@link Exception} ci-dessous et deviendrait une 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Acces refuse",
                "Vous n'avez pas les droits necessaires.", "access-denied");
    }

    /**
     * Filet de securite : toute exception non prevue devient une 500 neutre.
     * La cause reelle part dans les logs, jamais dans la reponse.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erreur inattendue traitee par le handler global", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne",
                "Une erreur inattendue est survenue.", "internal");
    }

    /**
     * Enrichit l'erreur de validation standard d'un detail par champ, pour que le
     * client puisse afficher le message au bon endroit du formulaire.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Requete invalide",
                "Un ou plusieurs champs sont invalides.", "validation");
        body.setProperty("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private static long ceilSeconds(Duration delay) {
        return (delay.toMillis() + 999) / 1000;
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create(PROBLEM_BASE + type));
        return problemDetail;
    }
}
