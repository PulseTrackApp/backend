package com.pulsetrack.backend.user;

import java.util.Set;
import java.util.UUID;

import com.pulsetrack.backend.common.error.AccountDisabledException;
import com.pulsetrack.backend.common.security.AuthenticatedUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Ferme l'API aux comptes suspendus, jetons deja emis compris.
 *
 * <p><strong>C'est ce qui rend la suspension immediate.</strong> Refuser la
 * connexion ne suffit pas : le jeton d'acces vit vingt-quatre heures, et sans ce
 * controle un compte ferme continuerait de fonctionner une journee entiere —
 * precisement le delai pendant lequel on voulait qu'il s'arrete.
 *
 * <p>Passe <strong>avant</strong> le verrou de version et celui du paiement :
 * quand un compte est suspendu, c'est le fait saillant. Lui repondre « mets a
 * jour ton application » ou « il faut payer » l'enverrait corriger des choses
 * qui ne changeront rien.
 */
public class DisabledAccountInterceptor implements HandlerInterceptor {

    /**
     * Ce qui reste ouvert a un compte suspendu.
     *
     * <p>Meme raisonnement que pour le mur de paiement : retenir les donnees de
     * quelqu'un parce qu'on lui a ferme son compte serait indefendable, et
     * l'empecher de supprimer ce compte encore plus. Etre suspendu d'une
     * application de sport ne doit pas couter son propre historique.
     */
    private static final Set<String> FREE_PREFIXES = Set.of(
            "/api/v1/auth",
            "/api/v1/me/export",
            "/actuator");

    /** Suppression de son propre compte : {@code DELETE /api/v1/me}, chemin exact. */
    private static final String ACCOUNT_PATH = "/api/v1/me";

    private final AccountStatusService statuses;

    public DisabledAccountInterceptor(AccountStatusService statuses) {
        this.statuses = statuses;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isFree(request.getRequestURI())) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            // Requete non authentifiee : la chaine de securite a deja tranche.
            // Repondre « compte suspendu » designerait un compte au hasard.
            return true;
        }

        // Pas d'immunite administrateur ici, contrairement aux modules et au
        // paiement. Ceux-la protegent d'une erreur de configuration ; une
        // suspension est une decision, et un administrateur suspendu doit l'etre
        // vraiment. Le service refuse par ailleurs qu'un administrateur se
        // suspende lui-meme, ce qui evite de se fermer la porte au nez.
        UUID userId = AuthenticatedUser.idOf(jwt);
        AccountStatusService.Status status = statuses.of(userId);
        if (status.disabled()) {
            throw new AccountDisabledException(status.message());
        }
        return true;
    }

    private boolean isFree(String path) {
        if (ACCOUNT_PATH.equals(path)) {
            return true;
        }
        return FREE_PREFIXES.stream().anyMatch(prefix ->
                path.equals(prefix) || path.startsWith(prefix + "/"));
    }
}
