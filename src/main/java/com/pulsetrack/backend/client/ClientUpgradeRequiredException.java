package com.pulsetrack.backend.client;

/**
 * L'application appelante est trop ancienne pour cette API.
 *
 * <p>Porte de quoi construire un ecran utile : la version exigee, celle qui
 * appelle, et l'adresse du magasin. Un refus qui se contente de dire non laisse
 * l'utilisateur sans rien a faire.
 *
 * @param currentVersion version annoncee par le client ; {@code null} quand il
 *                       n'en annonce aucune — c'est le cas des applications
 *                       publiees avant ce dispositif
 */
public class ClientUpgradeRequiredException extends RuntimeException {

    private final String minimumVersion;
    private final String currentVersion;
    private final String storeUrl;

    public ClientUpgradeRequiredException(String minimumVersion, String currentVersion, String storeUrl) {
        super("Cette version de l'application n'est plus acceptee. Mets-la a jour pour continuer.");
        this.minimumVersion = minimumVersion;
        this.currentVersion = currentVersion;
        this.storeUrl = storeUrl;
    }

    public String minimumVersion() {
        return minimumVersion;
    }

    public String currentVersion() {
        return currentVersion;
    }

    public String storeUrl() {
        return storeUrl;
    }
}
