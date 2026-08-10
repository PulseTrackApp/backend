package com.pulsetrack.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Signale bruyamment, au demarrage, que l'application tourne avec le secret JWT
 * livre pour le developpement.
 *
 * <p>Ce secret est ecrit en clair dans {@code application.yml}, donc dans le
 * depot, donc connu de quiconque a lu le code. Qui le connait peut forger un
 * jeton pour n'importe quel compte : l'authentification entiere ne vaut plus
 * rien. La validation de configuration ne peut pas l'attraper — le secret est
 * bien present et fait bien 32 caracteres — d'ou ce controle separe.
 *
 * <p>Un avertissement et non un refus de demarrer : c'est la valeur par defaut
 * en developpement, et faire echouer {@code spring-boot:run} sur un poste local
 * serait absurde. En production, {@code application-prod.yml} n'a de toute
 * facon aucune valeur par defaut, le demarrage echoue si la variable
 * d'environnement manque ; l'avertissement couvre le cas ou quelqu'un aurait
 * recopie le secret du depot dans cette variable.
 */
@Component
public class DevelopmentSecretWarner {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentSecretWarner.class);

    /**
     * Convention de nommage des secrets d'exemple du depot. Reconnaitre un
     * prefixe plutot qu'une valeur exacte evite que le controle devienne muet
     * le jour ou quelqu'un retouche la valeur de {@code application.yml}.
     */
    static final String DEVELOPMENT_SECRET_PREFIX = "dev-only-";

    private final SecurityProperties properties;

    public DevelopmentSecretWarner(SecurityProperties properties) {
        this.properties = properties;
    }

    /**
     * Journalise apres le demarrage plutot qu'a la construction du bean : le
     * message se retrouve alors en fin de trace, la ou on regarde, et non noye
     * au milieu de l'initialisation de Spring.
     */
    @EventListener(ApplicationReadyEvent.class)
    void warnIfDevelopmentSecret() {
        if (!isDevelopmentSecret(properties.jwt().secret())) {
            return;
        }

        log.warn("""

                ****************************************************************
                * SECRET JWT DE DEVELOPPEMENT ACTIF                            *
                *                                                              *
                * Ce secret figure en clair dans le depot. N'importe qui peut  *
                * donc signer un jeton valide pour n'importe quel compte.      *
                *                                                              *
                * Acceptable en local, JAMAIS en production : definissez-y     *
                * PULSETRACK_JWT_SECRET (openssl rand -base64 48).             *
                ****************************************************************""");
    }

    /**
     * @param secret secret configure
     * @return {@code true} s'il s'agit d'un secret d'exemple du depot
     */
    static boolean isDevelopmentSecret(String secret) {
        return secret != null && secret.startsWith(DEVELOPMENT_SECRET_PREFIX);
    }
}
