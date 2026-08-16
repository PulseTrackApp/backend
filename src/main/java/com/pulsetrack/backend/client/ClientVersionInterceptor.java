package com.pulsetrack.backend.client;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Refuse les applications trop anciennes.
 *
 * <p>Intercepteur MVC et non regle de securite, pour la meme raison que le
 * controle des modules : le refus sort ainsi au format RFC 9457 comme le reste de
 * l'API, en portant la version exigee et l'adresse du magasin. Un refus pose dans
 * la chaine de filtres produirait un corps different, et le client aurait deux
 * formats d'erreur a savoir lire.
 */
public class ClientVersionInterceptor implements HandlerInterceptor {

    /**
     * Routes qui restent ouvertes meme a un client perime.
     *
     * <p>Refuser {@code /client/requirements} rendrait le dispositif inutilisable :
     * c'est la route que le client interroge precisement pour savoir s'il doit se
     * mettre a jour. Quant a {@code /admin}, l'application d'administration est
     * distribuee a la main et suit son propre rythme — la bloquer priverait
     * Nicolas de son tableau de bord au pire moment, celui ou le parc migre.
     */
    private static final Set<String> EXEMPT_PREFIXES = Set.of(
            "/api/v1/client",
            "/api/v1/admin");

    /**
     * Plateformes soumises au verrou. Le verrou vise les applications
     * distribuees par magasin, celles qu'on ne peut pas mettre a jour de force.
     */
    private static final Set<String> GATED_PLATFORMS = Set.of("ANDROID", "IOS");

    private final ClientProperties properties;

    public ClientVersionInterceptor(ClientProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.enforced() || !properties.hasMinimum()) {
            return true;
        }
        if (isExempt(request.getRequestURI())) {
            return true;
        }

        String platform = platformOf(request);
        // Une plateforme explicitement declaree hors du champ du verrou passe :
        // c'est le cas d'un outil interne ou d'un appel en ligne de commande.
        // L'absence de declaration, elle, ne passe pas — voir ci-dessous.
        if (platform != null && !GATED_PLATFORMS.contains(platform)) {
            return true;
        }

        String announced = request.getHeader(ClientProperties.VERSION_HEADER);
        Optional<ClientVersion> version = ClientVersion.parse(announced);

        // Aucune version annoncee, ou une version illisible : c'est une
        // application publiee avant ce dispositif. C'est exactement la population
        // que le verrou existe pour arreter, et le seul moyen de la reconnaitre.
        if (version.isEmpty() || !version.get().isAtLeast(properties.minimum())) {
            throw new ClientUpgradeRequiredException(
                    properties.minimum().toString(),
                    announced,
                    storeUrlFor(platform));
        }
        return true;
    }

    private boolean isExempt(String path) {
        return EXEMPT_PREFIXES.stream().anyMatch(prefix ->
                path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private String platformOf(HttpServletRequest request) {
        String raw = request.getHeader(ClientProperties.PLATFORM_HEADER);
        return raw == null || raw.isBlank() ? null : raw.trim().toUpperCase(Locale.ROOT);
    }

    /** iOS quand il est declare, Android sinon : c'est le parc reellement deploye. */
    private String storeUrlFor(String platform) {
        if ("IOS".equals(platform)) {
            return blankToNull(properties.iosStoreUrl());
        }
        return blankToNull(properties.androidStoreUrl());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
