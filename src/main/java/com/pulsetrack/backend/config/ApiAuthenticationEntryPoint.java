package com.pulsetrack.backend.config;

import java.io.IOException;

import com.pulsetrack.backend.common.error.ProblemWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Rend un refus d'authentification lisible par l'application mobile.
 *
 * <p><strong>Le probleme resolu.</strong> Par defaut, un jeton expire produit un
 * {@code 401} au <em>corps vide</em>, avec la cause reelle uniquement dans
 * l'en-tete {@code WWW-Authenticate}. Le client ne peut alors pas distinguer
 * « ta session a expire, reconnecte-toi » d'une panne reseau, et affiche « une
 * erreur est survenue » dans les deux cas — la pire des reponses, puisque
 * l'utilisateur n'a rien d'autre a faire que se reconnecter.
 *
 * <p>Deux types de probleme sont donc distingues :
 * <ul>
 *   <li>{@code token-expired} — le jeton etait valide, il ne l'est plus. Le
 *       client affiche un message clair et renouvelle sa session ;</li>
 *   <li>{@code unauthenticated} — aucun jeton, ou un jeton illisible. Le client
 *       renvoie vers la connexion.</li>
 * </ul>
 *
 * <p>Le detail reste volontairement pauvre : dire <em>pourquoi</em> une signature
 * est invalide aiderait a fabriquer des jetons. L'expiration, elle, ne se cache
 * pas — c'est une information que le porteur du jeton possede deja.
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * Code d'erreur OAuth2 pose sur un jeton refuse par le decodeur, expiration
     * comprise. La distinction fine passe par la description, seul endroit ou
     * Spring dit ce qui a echoue.
     */
    private static final String INVALID_TOKEN = "invalid_token";

    private final ProblemWriter problems;

    /**
     * Delegue conserve pour qu'il pose l'en-tete {@code WWW-Authenticate}, exige
     * par la RFC 6750 sur un {@code 401}. On ne le remplace pas, on l'habille
     * d'un corps.
     */
    private final BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();

    public ApiAuthenticationEntryPoint(ProblemWriter problems) {
        this.problems = problems;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        delegate.commence(request, response, exception);

        if (isExpired(exception)) {
            problems.write(request, response, HttpStatus.UNAUTHORIZED,
                    "Session expirée",
                    "Ta session a expiré. Reconnecte-toi pour continuer.",
                    "token-expired");
            return;
        }

        problems.write(request, response, HttpStatus.UNAUTHORIZED,
                "Authentification requise",
                "Cette ressource demande une session valide.",
                "unauthenticated");
    }

    /**
     * Spring ne modelise pas l'expiration a part : elle n'apparait que dans la
     * description de l'erreur, sous la forme « Jwt expired at ... ». On s'appuie
     * donc dessus, en se contentant du mot-cle et non de la phrase entiere, qui
     * change d'une version a l'autre.
     */
    private boolean isExpired(AuthenticationException exception) {
        if (!(exception instanceof org.springframework.security.oauth2.server.resource.
                InvalidBearerTokenException invalid)) {
            return false;
        }
        if (!(invalid.getError() instanceof BearerTokenError error)) {
            return false;
        }
        String description = error.getDescription();
        return INVALID_TOKEN.equals(error.getErrorCode())
                && description != null
                && description.toLowerCase().contains("expired");
    }

    /**
     * Pendant du precedent pour un jeton valide mais insuffisant : un compte
     * ordinaire qui frappe a la porte de l'administration.
     *
     * <p>Le message ne nomme pas ce qui manque. Repondre « il te faudrait le role
     * ADMIN » a qui n'y a pas droit revient a lui indiquer quoi chercher.
     */
    @Component
    public static class ApiAccessDeniedHandler implements AccessDeniedHandler {

        private final ProblemWriter problems;
        private final BearerTokenAccessDeniedHandler delegate = new BearerTokenAccessDeniedHandler();

        public ApiAccessDeniedHandler(ProblemWriter problems) {
            this.problems = problems;
        }

        @Override
        public void handle(HttpServletRequest request,
                           HttpServletResponse response,
                           org.springframework.security.access.AccessDeniedException exception)
                throws IOException {
            delegate.handle(request, response, exception);
            problems.write(request, response, HttpStatus.FORBIDDEN,
                    "Accès refusé",
                    "Ce compte n'a pas les droits nécessaires.",
                    "access-denied");
        }
    }
}
