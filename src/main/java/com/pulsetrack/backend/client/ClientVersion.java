package com.pulsetrack.backend.client;

import java.util.Optional;

/**
 * Version d'une application cliente, comparable a une autre.
 *
 * <p>Format {@code majeur.mineur.correctif}, les deux derniers etant facultatifs :
 * {@code 2}, {@code 2.1} et {@code 2.1.3} sont tous acceptes et valent
 * respectivement 2.0.0, 2.1.0 et 2.1.3.
 *
 * <p>Un suffixe apres un {@code -} ou un {@code +} est ignore ({@code 1.4.0-beta},
 * {@code 1.4.0+42}) : un canal de pre-publication ne doit pas se faire refuser
 * pour une raison de format.
 *
 * <p><strong>La comparaison est numerique, jamais lexicographique.</strong>
 * Comparer des chaines ferait passer « 1.10.0 » pour anterieur a « 1.9.0 », et le
 * verrou laisserait entrer exactement les versions qu'il doit arreter.
 */
public record ClientVersion(int major, int minor, int patch) implements Comparable<ClientVersion> {

    /** Valeur retenue quand aucun minimum n'est configure : tout passe. */
    public static final ClientVersion ZERO = new ClientVersion(0, 0, 0);

    /**
     * @param raw chaine annoncee par le client ; {@code null} ou illisible rend
     *            un {@link Optional} vide plutot qu'une exception. Une version
     *            qu'on ne sait pas lire est traitee comme une version absente,
     *            et c'est l'appelant qui decide ce que cela vaut
     */
    public static Optional<ClientVersion> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        String cleaned = raw.trim();
        int suffix = indexOfAny(cleaned, '-', '+');
        if (suffix >= 0) {
            cleaned = cleaned.substring(0, suffix);
        }

        String[] parts = cleaned.split("\\.");
        if (parts.length == 0 || parts.length > 3) {
            return Optional.empty();
        }

        try {
            int major = Integer.parseInt(parts[0].trim());
            int minor = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;
            if (major < 0 || minor < 0 || patch < 0) {
                return Optional.empty();
            }
            return Optional.of(new ClientVersion(major, minor, patch));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    /** Lit une version de configuration, en tolerant l'absence de valeur. */
    public static ClientVersion parseOrZero(String raw) {
        return parse(raw).orElse(ZERO);
    }

    public boolean isAtLeast(ClientVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(ClientVersion other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    private static int indexOfAny(String value, char first, char second) {
        int a = value.indexOf(first);
        int b = value.indexOf(second);
        if (a < 0) {
            return b;
        }
        return b < 0 ? a : Math.min(a, b);
    }
}
