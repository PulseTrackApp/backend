package com.pulsetrack.backend.user;

import java.time.Instant;

import com.pulsetrack.backend.config.SecurityProperties;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Emet les jetons d'acces. Seul endroit ou la forme du JWT est decidee, pour que
 * le {@code subject} attendu par {@code AuthenticatedUser} reste coherent.
 */
@Service
public class TokenService {

    /**
     * Nom de la revendication portant le role. Partage avec la configuration de
     * securite, qui la convertit en autorite {@code ROLE_*} : une chaine
     * dupliquee des deux cotes se desynchroniserait au premier renommage, et
     * l'espace d'administration s'ouvrirait ou se fermerait sans que rien ne le
     * signale.
     */
    public static final String ROLE_CLAIM = "role";

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;

    public TokenService(JwtEncoder jwtEncoder, SecurityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    /**
     * @param user compte a authentifier
     * @return jeton signe dont le {@code subject} est l'identifiant du compte
     */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .issuedAt(now)
                .expiresAt(now.plus(properties.jwt().accessTokenTtl()))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                // Le role voyage dans le jeton, contrairement aux modules : il
                // ne change qu'exceptionnellement, alors que les droits par
                // module sont faits pour etre ajustes a tout moment et ne
                // survivraient pas aux vingt-quatre heures de validite.
                .claim(ROLE_CLAIM, user.getRole().name())
                .build();

        // L'en-tete doit etre explicite : par defaut l'encodeur viserait RS256,
        // incompatible avec une cle secrete symetrique.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenTtlSeconds() {
        return properties.jwt().accessTokenTtl().toSeconds();
    }
}
