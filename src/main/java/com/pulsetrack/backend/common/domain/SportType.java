package com.pulsetrack.backend.common.domain;

/**
 * Sports suivis par PulseTrack. Vocabulaire partage entre le profil (sports
 * pratiques) et les seances, d'ou sa place dans le paquet commun.
 */
public enum SportType {
    RUN("course a pied"),
    RIDE("velo"),
    WALK("marche"),
    OTHER("seance");

    private final String label;

    SportType(String label) {
        this.label = label;
    }

    /**
     * Nom du sport tel qu'il s'ecrit dans une phrase : « ton record en course a
     * pied ». Minuscule et sans article, pour se glisser dans n'importe quel
     * message.
     *
     * <p>Le libelle vit ici et non dans un fichier de traduction : l'application
     * est mono-langue, et une table de correspondance separee finirait par
     * oublier une valeur ajoutee a l'enumeration. Jackson serialise toujours la
     * constante par son nom, ce libelle ne change donc rien au contrat d'API.
     */
    public String label() {
        return label;
    }
}
