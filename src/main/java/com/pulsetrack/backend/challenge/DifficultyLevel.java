package com.pulsetrack.backend.challenge;

/**
 * A quel point le defi sort de l'ordinaire de son auteur.
 *
 * <p>C'est une appreciation portee <strong>avant</strong> l'effort, la
 * contrepartie de celle qui suit le resultat : elle repond a « est-ce que je
 * vise juste ? », question que personne ne peut trancher seul en se fixant un
 * chiffre au hasard.
 *
 * <p>Le serveur <strong>n'interdit jamais</strong> un defi, meme
 * {@link #HORS_DE_PORTEE}. Quelqu'un a le droit de viser trop haut, et un refus
 * serait pris pour un jugement.
 */
public enum DifficultyLevel {

    /** Plus lent que l'allure habituelle. */
    ACCESSIBLE,

    /** Jusqu'a 5 % plus rapide que l'habitude. */
    REALISTE,

    /** De 5 a 15 % plus rapide : le bon cran, celui qui fait progresser. */
    AMBITIEUX,

    /** Plus de 15 % au-dessus du meilleur jamais realise. On avertit, on n'empeche pas. */
    HORS_DE_PORTEE,

    /** Moins de trois seances dans ce sport : ne rien affirmer vaut mieux qu'inventer. */
    INCONNU;

    /** D'ou sort la comparaison. Le dire rend l'avis credible. */
    public enum ReferenceBasis {

        /** Allure moyenne des dix dernieres seances du sport. */
        AVERAGE_LAST_10,

        /** Meilleure allure jamais realisee, quand la moyenne est deja depassee. */
        BEST_EVER,

        /** Pas assez d'historique pour comparer quoi que ce soit. */
        NONE
    }
}
