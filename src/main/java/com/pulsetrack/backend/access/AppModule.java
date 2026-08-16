package com.pulsetrack.backend.access;

import java.util.Optional;

/**
 * Fonctionnalite que l'administrateur peut ouvrir ou fermer compte par compte.
 *
 * <p>Nomme {@code AppModule} et non {@code Module} pour ne pas entrer en
 * collision avec {@link java.lang.Module}, importe implicitement dans toute
 * classe : un {@code Module} non qualifie designerait le type du JDK et la
 * confusion se paierait a la premiere lecture.
 *
 * <p>Le prefixe de route vit ici plutot que dans une table de correspondance a
 * part : le jour ou une route bouge, le compilateur ramene le lecteur a
 * l'endroit exact ou le module est defini, au lieu de laisser une association
 * silencieusement fausse dans un fichier de configuration.
 *
 * <p>L'ordre de declaration est celui d'affichage dans l'application
 * d'administration. Le renommer casserait le contrat : ces identifiants
 * circulent tels quels dans l'API.
 */
public enum AppModule {

    WORKOUTS("Seances et parcours", "/api/v1/workouts"),
    ROUTES("Parcours enregistres", "/api/v1/me/routes"),
    CHALLENGES("Defis chronometres", "/api/v1/me/challenges"),
    BODY_CHECKINS("Pesees", "/api/v1/me/body-checkins"),
    GOALS("Objectifs", "/api/v1/me/goals"),
    RATING("Note et encouragements", "/api/v1/me/rating"),
    STATS("Statistiques", "/api/v1/me/stats"),
    WEEKLY_SUMMARY("Bilan hebdomadaire", "/api/v1/me/weekly-summary"),
    // Le fournisseur n'est nomme nulle part dans ce qui sort du serveur, y
    // compris dans le catalogue d'administration : c'est un choix de produit.
    COACH("Assistant de coaching", "/api/v1/me/coach"),
    EXPORT("Export des donnees personnelles", "/api/v1/me/export"),
    PUSH("Notifications push", "/api/v1/me/device-tokens");

    private final String label;
    private final String pathPrefix;

    AppModule(String label, String pathPrefix) {
        this.label = label;
        this.pathPrefix = pathPrefix;
    }

    /** @return libelle lisible, destine a l'ecran d'administration */
    public String label() {
        return label;
    }

    public String pathPrefix() {
        return pathPrefix;
    }

    /**
     * Retrouve le module qui protege une route.
     *
     * <p>La correspondance exige que le prefixe soit suivi d'une fin de chaine
     * ou d'un {@code /}. Un simple {@code startsWith} ferait correspondre
     * {@code /api/v1/me/statistiques-publiques} au module {@code STATS} et
     * verrouillerait une route qui ne lui appartient pas.
     *
     * @param path chemin de la requete, sans la chaine de requete
     * @return le module concerne, ou vide si la route n'est protegee par aucun
     *         module — c'est le cas du noyau (authentification, profil) que
     *         personne ne doit pouvoir fermer
     */
    public static Optional<AppModule> forPath(String path) {
        if (path == null) {
            return Optional.empty();
        }
        for (AppModule module : values()) {
            if (matches(path, module.pathPrefix)) {
                return Optional.of(module);
            }
        }
        return Optional.empty();
    }

    private static boolean matches(String path, String prefix) {
        if (!path.startsWith(prefix)) {
            return false;
        }
        return path.length() == prefix.length() || path.charAt(prefix.length()) == '/';
    }
}
