package com.pulsetrack.backend.push;

/**
 * Utilitaires sur les jetons d'appareil.
 */
public final class PushTokens {

    private PushTokens() {
    }

    /**
     * Masque un jeton pour la journalisation.
     *
     * <p>Un jeton FCM identifie un appareil et permet de lui envoyer des
     * notifications : il n'a rien a faire en clair dans un fichier de log. On
     * garde les extremites, de quoi le reconnaitre en cas de doute.
     */
    public static String masked(String token) {
        if (token == null || token.length() < 12) {
            return "***";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }
}
