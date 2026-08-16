package com.pulsetrack.backend.motivation;

/**
 * Met en francais les grandeurs sportives destinees a etre lues.
 *
 * <p>Le formatage vit cote serveur parce que les messages d'encouragement y sont
 * rediges : une phrase composee ici et des chiffres formates ailleurs finiraient
 * par ne plus s'accorder. Toutes les plateformes affichent alors exactement le
 * meme texte.
 *
 * <p>Le formatage est fait a la main plutot que par {@code NumberFormat} : les
 * classes de la plateforme inserent, selon la version du JDK et la locale
 * installee, une espace insecable ordinaire ou etroite comme separateur de
 * milliers. Le meme test passerait ici et echouerait sur le serveur. Ici, la
 * chaine rendue ne depend de rien d'exterieur.
 *
 * <p>Classe utilitaire sans etat : elle ne s'instancie pas.
 */
public final class Wording {

    private static final double METERS_PER_KM = 1_000d;

    private Wording() {
    }

    /**
     * Distance lisible : en metres sous le kilometre, en kilometres au-dela.
     *
     * <p>Deux decimales sur les kilometres et non trois : « 6,30 km » se lit,
     * « 6,297 km » donne une precision que le GPS n'a pas.
     */
    public static String distance(double meters) {
        double absolute = Math.abs(meters);
        if (absolute < METERS_PER_KM) {
            return Math.round(absolute) + " m";
        }
        return decimal(absolute / METERS_PER_KM, 2) + " km";
    }

    /**
     * Duree lisible, sans jamais afficher une unite nulle en tete.
     *
     * <p>« 54 min 40 s », « 1 h 12 min », « 45 s ». Les secondes disparaissent
     * au-dela de l'heure : personne ne lit « 1 h 12 min 03 s ».
     */
    public static String duration(long seconds) {
        long total = Math.abs(seconds);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long rest = total % 60;

        if (hours > 0) {
            return minutes == 0 ? hours + " h" : hours + " h " + minutes + " min";
        }
        if (minutes > 0) {
            return rest == 0 ? minutes + " min" : minutes + " min " + rest + " s";
        }
        return rest + " s";
    }

    /**
     * Allure au format du chronometre, « 5:30/km ». Les secondes sont toujours
     * sur deux chiffres : « 5:3/km » ne se lit pas.
     */
    public static String pace(int secondsPerKm) {
        int total = Math.abs(secondsPerKm);
        return "%d:%02d/km".formatted(total / 60, total % 60);
    }

    /** Vitesse a une decimale, « 10,9 km/h ». */
    public static String speed(double kmh) {
        return decimal(kmh, 1) + " km/h";
    }

    /**
     * Nombre a virgule francaise, sans decimale inutile : {@code 4.0} rend
     * « 4 » et non « 4,0 ».
     */
    public static String decimal(double value, int decimals) {
        double rounded = round(value, decimals);
        if (rounded == Math.rint(rounded)) {
            return String.valueOf((long) Math.rint(rounded));
        }
        // Les zeros de queue sont retires : « 6,30 km » donne une precision que le
        // GPS n'a pas, et « 6,3 km » se lit mieux a voix haute.
        String formatted = String.format(java.util.Locale.ROOT, "%." + decimals + "f", rounded);
        while (formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted.replace('.', ',');
    }

    /**
     * Pourcentage entier, « 73 % ». Espace ordinaire et non insecable : le
     * caractere insecable traverse mal les journaux et les notifications, et
     * c'est au client de gerer sa coupure de ligne.
     */
    public static String percent(double value) {
        return Math.round(value) + " %";
    }

    /**
     * Nombre suivi d'un nom accorde : {@code plural(1, "seance", "seances")}
     * rend « 1 seance ». Le pluriel francais commence a deux, mais aussi a zero
     * : « 0 seance », au singulier.
     */
    public static String plural(long count, String singular, String plural) {
        return count > 1 || count < -1 ? count + " " + plural : count + " " + singular;
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
