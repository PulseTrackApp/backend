package com.pulsetrack.backend.billing;

import java.util.Set;
import java.util.UUID;

import com.pulsetrack.backend.access.AdminAuthority;
import com.pulsetrack.backend.common.security.AuthenticatedUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Refuse les routes payantes aux comptes sans droit d'usage.
 *
 * <p>Inactif tant que {@code pulsetrack.billing.enforced} vaut faux, ce qui est
 * le cas aujourd'hui : le dispositif existe pour que l'application puisse
 * construire et eprouver son ecran de paiement des maintenant.
 *
 * <p><strong>Ce qui reste gratuit, et pourquoi.</strong> Un compte expire doit
 * pouvoir se connecter, voir pourquoi il est bloque, payer, changer son mot de
 * passe, <strong>exporter ses donnees</strong> et supprimer son compte. Retenir
 * les donnees de quelqu'un parce qu'il a cesse de payer serait indefendable, et
 * l'enfermer hors de son propre compte encore plus.
 */
public class SubscriptionInterceptor implements HandlerInterceptor {

    /**
     * Prefixes qui restent ouverts meme sans droit d'usage.
     *
     * <p>Liste explicite et non joker : un endpoint ajoute demain nait payant,
     * ce qui est le bon defaut. L'oubli inverse — une route sensible restee
     * gratuite — ne se verrait jamais.
     */
    private static final Set<String> FREE_PREFIXES = Set.of(
            "/api/v1/auth",
            "/api/v1/client",
            "/api/v1/billing",
            "/api/v1/admin",
            "/api/v1/me/subscription",
            "/api/v1/me/profile",
            "/api/v1/me/modules",
            "/api/v1/me/password",
            // Portabilite des donnees : elle ne se monnaye pas.
            "/api/v1/me/export",
            // L'assistant de coaching n'entre pas dans l'offre payante. Ce n'est
            // pas une faveur : il tourne sur une cle personnelle, il n'est ouvert
            // a personne d'autre, et il n'est donc pas vendu. Son acces est
            // gouverne par le module COACH — ferme par defaut — et non par le
            // paiement. Le laisser derriere le mur reviendrait a faire payer une
            // fonction que le catalogue ne promet pas.
            "/api/v1/me/coach",
            "/actuator");

    /** Suppression de compte : {@code DELETE /api/v1/me}, chemin exact. */
    private static final String ACCOUNT_PATH = "/api/v1/me";

    private final SubscriptionService subscriptions;

    public SubscriptionInterceptor(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!subscriptions.isEnforced()) {
            return true;
        }
        if (isFree(request.getRequestURI())) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            // Requete non authentifiee : la chaine de securite a deja tranche.
            // Repondre « il faut payer » masquerait la vraie raison du refus.
            return true;
        }

        // Un administrateur n'est jamais bloque par le paiement, comme il n'est
        // jamais bloque par un module ferme. C'est la meme immunite, et elle
        // evite qu'une erreur de configuration enferme dehors la seule personne
        // capable de la corriger.
        if (AdminAuthority.isAdmin(authentication)) {
            return true;
        }

        UUID userId = AuthenticatedUser.idOf(jwt);
        SubscriptionService.Entitlement entitlement = subscriptions.statusOf(userId);
        if (!entitlement.status().grantsAccess()) {
            throw new SubscriptionRequiredException(
                    entitlement.status(),
                    subscriptions.highlightedPlan().orElse(null));
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
