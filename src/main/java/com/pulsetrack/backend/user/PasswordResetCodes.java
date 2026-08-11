package com.pulsetrack.backend.user;

import java.security.SecureRandom;

/**
 * Fabrique les codes de reinitialisation.
 *
 * <p>Un code court et lisible, non un jeton opaque : l'application mobile n'a
 * pas de liens profonds, l'utilisateur recopie donc ce qu'il lit dans son
 * courriel. Quarante caracteres de base64 seraient recopies de travers une fois
 * sur deux.
 *
 * <p>L'alphabet exclut {@code I}, {@code O}, {@code 0} et {@code 1}, qu'on
 * confond a la lecture. Il reste 32 symboles sur 8 positions, soit environ
 * mille milliards de combinaisons — hors de portee d'une recherche exhaustive,
 * a plus forte raison avec une validite de trente minutes et une limitation de
 * debit sur l'endpoint.
 */
final class PasswordResetCodes {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordResetCodes() {
    }

    /** @return code en majuscules, sans separateur */
    static String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    /**
     * Ramene une saisie utilisateur a la forme comparable : majuscules, sans
     * espaces. Sans cela, un code recopie avec une espace ou en minuscules
     * serait refuse alors qu'il est bon.
     */
    static String normalize(String code) {
        return code == null ? "" : code.replaceAll("\\s", "").toUpperCase(java.util.Locale.ROOT);
    }
}
