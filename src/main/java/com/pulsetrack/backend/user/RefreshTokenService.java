package com.pulsetrack.backend.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.common.error.InvalidRefreshTokenException;
import com.pulsetrack.backend.config.SecurityProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emission, rotation et revocation des jetons de renouvellement.
 *
 * <p>Le jeton est une valeur opaque tiree au hasard, pas un JWT : sa validite ne
 * se lit pas dans son contenu mais dans la base, et c'est precisement ce qui le
 * rend revocable.
 *
 * <p>Chaque renouvellement consomme le jeton presente et en emet un neuf
 * (<em>rotation</em>). Un jeton n'est donc utilisable qu'une fois, ce qui borne
 * la fenetre d'exploitation d'une copie volee — et rend cette copie detectable :
 * si le jeton derobe est utilise puis que le proprietaire legitime presente le
 * sien, le second passage tombe sur un jeton deja consomme.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /** 256 bits d'entropie : hors de portee d'une recherche exhaustive. */
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository tokens;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens, SecurityProperties properties) {
        this.tokens = tokens;
        this.ttl = properties.refreshToken().ttl();
    }

    /**
     * Emet un jeton pour un compte et purge au passage ses jetons perimes.
     *
     * @return la valeur en clair, seule occasion ou elle existe cote serveur :
     *         seule son empreinte est conservee
     */
    @Transactional
    public IssuedToken issueFor(UUID userId) {
        Instant now = Instant.now();
        tokens.deleteExpiredFor(userId, now);

        String value = randomToken();
        Instant expiresAt = now.plus(ttl);
        tokens.save(new RefreshToken(userId, hash(value), now, expiresAt));
        return new IssuedToken(value, expiresAt, ttl.toSeconds());
    }

    /**
     * Verifie un jeton presente et le consomme.
     *
     * <p>Presenter un jeton deja consomme signe soit un vol, soit un client qui
     * a rejoue une requete. On ne peut pas distinguer les deux, et l'hypothese
     * prudente coute peu : toutes les sessions du compte sont coupees, son
     * proprietaire n'a qu'a se reconnecter.
     *
     * @param presented valeur en clair recue du client
     * @return le compte auquel le jeton appartenait
     * @throws InvalidRefreshTokenException si le jeton est inconnu, expire ou
     *                                      deja consomme
     */
    // `noRollbackFor` est ici essentiel : au rejeu, la methode revoque les
    // sessions du compte *puis* refuse la requete. Sans cela, l'exception
    // annulerait la transaction et donc la revocation que l'on vient de
    // decider — la detection du vol serait purement decorative. L'appelant ne
    // doit pas non plus ouvrir de transaction englobante, sinon c'est elle qui
    // deciderait de l'annulation.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public UUID consume(String presented) {
        Instant now = Instant.now();
        RefreshToken token = tokens.findByTokenHash(hash(presented))
                .orElseThrow(() -> new InvalidRefreshTokenException("Jeton de renouvellement inconnu."));

        if (token.isRevoked()) {
            log.warn("Jeton de renouvellement deja consomme presente pour le compte {} : "
                    + "toutes ses sessions sont revoquees par precaution.", token.getUserId());
            revokeAllFor(token.getUserId(), now);
            throw new InvalidRefreshTokenException("Jeton de renouvellement deja utilise.");
        }
        if (token.hasExpiredAt(now)) {
            throw new InvalidRefreshTokenException("Jeton de renouvellement expire.");
        }

        token.revokeAt(now);
        return token.getUserId();
    }

    /**
     * Revoque le jeton presente, a la deconnexion.
     *
     * <p>Volontairement idempotent et silencieux : un client qui se deconnecte
     * deux fois, ou dont le jeton a deja expire, a obtenu ce qu'il voulait. Lui
     * renvoyer une erreur ne ferait que compliquer son code sans rien proteger.
     *
     * @param userId compte authentifie par le jeton d'acces ; un jeton
     *               appartenant a quelqu'un d'autre est ignore, pour qu'on ne
     *               puisse pas deconnecter autrui en devinant sa valeur
     */
    @Transactional
    public void revoke(String presented, UUID userId) {
        tokens.findByTokenHash(hash(presented))
                .filter(token -> token.getUserId().equals(userId))
                .ifPresent(token -> token.revokeAt(Instant.now()));
    }

    /** Coupe toutes les sessions actives d'un compte. */
    @Transactional
    public void revokeAllFor(UUID userId) {
        revokeAllFor(userId, Instant.now());
    }

    private void revokeAllFor(UUID userId, Instant now) {
        List<RefreshToken> active = tokens.findByUserIdAndRevokedAtIsNull(userId);
        active.forEach(token -> token.revokeAt(now));
    }

    /**
     * Encodage URL-safe sans remplissage : le jeton voyage dans du JSON, mais
     * rien n'interdit a un client de le mettre ailleurs, et les {@code +}, {@code /}
     * et {@code =} du Base64 classique s'y font mal echapper.
     */
    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 plutot que bcrypt, contrairement aux mots de passe : la valeur est
     * tiree au hasard sur 256 bits, aucun dictionnaire ne lui est opposable, et
     * il faut pouvoir la retrouver par index — ce qu'un hachage sale interdit.
     */
    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est exige de toute implementation Java : si elle manque,
            // l'environnement d'execution est casse, pas la requete.
            throw new IllegalStateException("SHA-256 indisponible sur cette JVM", e);
        }
    }

    /**
     * @param value            valeur a transmettre au client, jamais rejouable
     *                         cote serveur une fois l'empreinte enregistree
     * @param expiresAt        expiration absolue
     * @param expiresInSeconds duree restante, pour epargner au client le calcul
     */
    public record IssuedToken(String value, Instant expiresAt, long expiresInSeconds) {
    }
}
