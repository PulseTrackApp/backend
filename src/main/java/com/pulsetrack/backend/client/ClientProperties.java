package com.pulsetrack.backend.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Version minimale d'application cliente acceptee par l'API.
 *
 * <p><strong>A quoi cela sert vraiment.</strong> Le jour ou l'application devient
 * payante, les APK deja installes doivent cesser de fonctionner — sans quoi il
 * suffit de ne pas mettre a jour pour continuer gratuitement, et le paiement ne
 * s'applique qu'aux nouveaux venus. Ce verrou est le seul point ou cette regle
 * peut etre tenue : le serveur, que personne ne peut contourner.
 *
 * <p><strong>Le mecanisme repose sur une absence.</strong> Les versions publiees
 * jusqu'ici n'envoient aucun en-tete de version. Une requete sans en-tete est
 * donc, par construction, une requete d'un client anterieur a ce dispositif :
 * quand {@code enforced} passe a vrai, elle est refusee. Il n'y a rien a
 * retro-porter dans les APK deja distribues, et c'est precisement ce qui rend le
 * verrou efficace.
 *
 * <p><strong>Ne l'active pas avant que la version a jour soit publiee et
 * installee.</strong> Le passage a vrai ferme l'application a tout le monde
 * simultanement, y compris a ceux qui n'ont pas encore vu la mise a jour dans
 * leur magasin. La sequence sure est : publier, laisser le parc se mettre a jour,
 * puis activer.
 *
 * @param enforced       verrou actif. Faux par defaut : le dispositif se deploie
 *                       et s'observe longtemps avant de refuser quoi que ce soit
 * @param minimumVersion version minimale acceptee, au format
 *                       {@code majeur.mineur.correctif}
 * @param androidStoreUrl ou envoyer quelqu'un dont l'application est trop
 *                        ancienne. Un refus sans porte de sortie est une impasse
 * @param iosStoreUrl    idem pour iOS ; vide tant qu'il n'y a pas d'application
 */
@ConfigurationProperties(prefix = "pulsetrack.client")
public record ClientProperties(boolean enforced,
                               String minimumVersion,
                               String androidStoreUrl,
                               String iosStoreUrl) {

    /** En-tete portant la version de l'application appelante, par exemple {@code 1.5.0}. */
    public static final String VERSION_HEADER = "X-GymFlow-Client-Version";

    /** En-tete portant la plateforme : {@code ANDROID}, {@code IOS}, {@code DESKTOP}, {@code WEB}. */
    public static final String PLATFORM_HEADER = "X-GymFlow-Platform";

    public ClientVersion minimum() {
        return ClientVersion.parseOrZero(minimumVersion);
    }

    /**
     * @return vrai si aucun minimum n'est reellement pose ; le verrou n'a alors
     *         rien a refuser, meme actif
     */
    public boolean hasMinimum() {
        return !ClientVersion.ZERO.equals(minimum());
    }
}
