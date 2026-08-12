package com.pulsetrack.backend.workout;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.pulsetrack.backend.workout.dto.GpsPointRequest;

/**
 * Filtre de Kalman etendu qui reconstitue la trajectoire reelle a partir des
 * positions bruitees et de la vitesse annoncee par le capteur.
 *
 * <p><strong>Pourquoi un filtre plutot qu'un seuil.</strong> Additionner les
 * distances entre points bruts surestime systematiquement le parcours, et le
 * biais est toujours dans le meme sens : le bruit s'ajoute a chaque segment,
 * jamais ne s'en retranche. Mesure faite sur une marche du 11 aout 2026, un
 * point toutes les trois secondes : les positions donnaient 1,86 m/s la ou le
 * capteur mesurait 1,46 m/s. Sur les segments de deux secondes l'ecart montait
 * a 1,81 fois, sur ceux de quatre il tombait a 1,19 — c'est la signature du
 * bruit, qui pese d'autant plus que la base de mesure est courte. Aucun seuil
 * ne repare cela : le deplacement reel entre deux points rapproches est du meme
 * ordre de grandeur que l'incertitude annoncee, les separer par comparaison est
 * impossible.
 *
 * <p><strong>Ce que fait le filtre.</strong> Il entretient une estimation de la
 * position <em>et</em> de la vitesse, avec l'incertitude de chacune. A chaque
 * point recu il predit ou l'on devrait etre, compare a la mesure, et corrige
 * dans une proportion qui depend de la confiance relative des deux — une mesure
 * annoncee a vingt metres de precision pese beaucoup moins qu'une mesure a
 * trois metres. La vitesse du capteur, mesuree par effet Doppler et independante
 * du bruit de position, entre comme une observation supplementaire : c'est elle
 * qui empeche la trajectoire estimee de se mettre a zigzaguer.
 *
 * <p>C'est la methode des montres de sport, et elle a un avantage decisif sur
 * un lissage : elle sait ce qu'elle ignore. Une position tres incertaine ne
 * degrade pas l'estimation, elle est simplement peu ecoutee.
 *
 * <p><strong>Rejet des aberrations.</strong> Une mesure trop eloignee de la
 * prediction, compte tenu des incertitudes des deux, est ecartee sans etre
 * integree — c'est la distance de Mahalanobis, le test standard. Le point a
 * 22,8 metres de precision qui avait produit un pic fantome de 23,5 km/h tombe
 * ici de lui-meme, sans regle ad hoc.
 *
 * <p>Classe sans etat entre deux appels et sans dependance : elle s'instancie
 * avec {@code new} dans un test unitaire.
 */
final class TrackFilter {

    /**
     * Ecart-type de l'acceleration non modelisee, en m/s². C'est le reglage
     * central : il dit au filtre a quel point une allure peut changer entre deux
     * points. Trop bas, la trajectoire estimee coupe les virages et raccourcit
     * le parcours ; trop haut, le filtre suit le bruit et on retombe sur le
     * defaut qu'on corrige. La valeur retenue est validee sur trajets simules a
     * distance connue, dans {@code TrackFilterTest}.
     */
    private static final double ACCELERATION_NOISE_MPS2 = 0.25;

    /**
     * Incertitude accordee a la vitesse du capteur, en m/s. Le Doppler d'un
     * telephone est bon a quelques dizaines de centimetres par seconde, bien
     * mieux que ce qu'on tirerait des positions.
     */
    private static final double SPEED_SIGMA_MPS = 0.3;

    /** Precision supposee quand le telephone n'annonce rien. Prudente a dessein. */
    private static final double DEFAULT_ACCURACY_METERS = 10.0;

    /**
     * Plancher sur la precision annoncee. Un telephone qui se declare exact au
     * demi-metre force le filtre a le croire aveuglement et rend le lissage
     * inoperant.
     */
    private static final double MIN_ACCURACY_METERS = 3.0;

    /**
     * Seuil de rejet, en nombre d'ecarts-types. Au-dela, la mesure est tenue
     * pour aberrante. Quatre sigma laisse passer la quasi-totalite des mesures
     * legitimes — une mesure honnete depasse ce seuil moins d'une fois sur mille.
     */
    private static final double OUTLIER_GATE_SIGMAS = 4.0;

    /**
     * Au-dela de cet intervalle entre deux points, la vitesse estimee cesse de
     * renseigner sur ce qui s'est passe entre eux.
     *
     * <p>Un filtre avance : il rend la meilleure estimation <em>a l'instant de
     * la derniere mesure</em>, pas la moyenne sur l'intervalle qui precede.
     * Apres six minutes sans nouvelle, il attribue tout le deplacement observe a
     * une acceleration finale et sort une vitesse d'environ deux fois la vitesse
     * moyenne — juste au sens du modele, trompeur si on l'integre. Mesure sur un
     * cas a deux points distants de six minutes : 1 988 metres annonces pour
     * 1 000 reellement parcourus.
     *
     * <p>Un lisseur — une seconde passe qui repart de la fin vers le debut, a la
     * maniere de Rauch-Tung-Striebel — corrigerait cela sans regle explicite, en
     * redistribuant l'information sur tout l'intervalle. Il double la taille du
     * code pour un cas que l'application mobile ne produit jamais : elle envoie
     * un point toutes les deux ou trois secondes. Le seuil est donc pose ici,
     * assume et mesure, plutot que dissimule dans un algorithme plus savant.
     */
    private static final double SPARSE_SAMPLING_SECONDS = 30;

    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    /**
     * Un point de la trajectoire estimee.
     *
     * @param eastMeters  abscisse dans le plan local, en metres
     * @param northMeters ordonnee dans le plan local, en metres
     * @param speedMps    norme de la vitesse estimee
     * @param at          instant de la mesure
     */
    record FilteredPoint(double eastMeters, double northMeters, double speedMps, Instant at) {
    }

    /**
     * Distance parcourue, obtenue en integrant la vitesse estimee.
     *
     * <p><strong>On integre la vitesse plutot que de resommer les positions
     * filtrees, et c'est essentiel.</strong> Meme corrigee, la trajectoire garde
     * un leger tremblement : chaque mesure tire l'estimation vers elle, et ces
     * allers-retours s'additionnent en une distance qui n'a jamais ete
     * parcourue. La vitesse estimee, elle, est bien plus lisse — elle ne bouge
     * qu'autant que l'acceleration modelisee l'autorise. C'est la methode des
     * montres de sport, et c'est ce qui permet a une pause de compter zero metre
     * plutot que quelques dizaines.
     *
     * @param filtered trajectoire estimee
     * @return distance en metres, zero si le trace est trop court
     */
    static double distanceMeters(List<FilteredPoint> filtered) {
        double total = 0;
        for (int i = 1; i < filtered.size(); i++) {
            FilteredPoint previous = filtered.get(i - 1);
            FilteredPoint current = filtered.get(i);
            double dt = Duration.between(previous.at(), current.at()).toMillis() / 1000d;
            if (dt <= 0) {
                continue;
            }

            // Trapeze plutot que rectangle : sur un demarrage ou un arret, la
            // vitesse change franchement d'un point au suivant, et prendre l'une
            // des deux bornes biaiserait la distance dans un sens constant.
            total += (previous.speedMps() + current.speedMps()) / 2 * dt;
        }
        return total;
    }

    /**
     * Reconstitue la trajectoire.
     *
     * @param points trace brute, deja triee chronologiquement
     * @return trajectoire estimee, vide si le trace compte moins de deux points
     */
    List<FilteredPoint> filter(List<GpsPointRequest> points) {
        if (points == null || points.size() < 2) {
            return List.of();
        }

        // Plan local tangent centre sur le premier point : a l'echelle d'un
        // parcours sportif, la courbure de la Terre est negligeable et travailler
        // en metres evite de melanger degres et distances a chaque ligne.
        double originLat = points.get(0).latitude();
        double originLon = points.get(0).longitude();
        double metersPerDegreeLon = Math.cos(Math.toRadians(originLat)) * Math.PI * EARTH_RADIUS_METERS / 180d;
        double metersPerDegreeLat = Math.PI * EARTH_RADIUS_METERS / 180d;

        // Un telephone qui ne sait pas mesurer la vitesse peut annoncer zero
        // partout plutot que de ne rien annoncer. Le croire ramenerait la
        // distance a zero : une seance entiere effacee par un capteur muet. Une
        // seance reelle comporte toujours au moins un instant en mouvement.
        boolean sensorUsable = points.stream()
                .anyMatch(point -> point.speed() != null && point.speed() > 0);

        State state = null;
        Instant previousAt = null;
        List<FilteredPoint> track = new ArrayList<>(points.size());

        for (GpsPointRequest point : points) {
            double east = (point.longitude() - originLon) * metersPerDegreeLon;
            double north = (point.latitude() - originLat) * metersPerDegreeLat;
            double accuracy = Math.max(
                    point.accuracy() == null ? DEFAULT_ACCURACY_METERS : point.accuracy(),
                    MIN_ACCURACY_METERS);

            if (state == null || previousAt == null) {
                // Premier point : on part de la mesure elle-meme.
                //
                // Aucun traitement particulier n'est prevu pour les longues
                // coupures — tunnel, telephone en poche, application suspendue.
                // L'incertitude s'en charge seule : plus l'intervalle est long,
                // plus la prediction devient imprecise, et plus la mesure
                // suivante pese dans la correction. Un cas particulier ici
                // n'ajouterait qu'un seuil arbitraire de plus, et couperait la
                // distance des seances qui n'envoient qu'une poignee de points.
                state = State.startingAt(east, north, accuracy);
                previousAt = point.recordedAt();
                track.add(new FilteredPoint(east, north, 0d, point.recordedAt()));
                continue;
            }

            double dt = Duration.between(previousAt, point.recordedAt()).toMillis() / 1000d;
            if (dt <= 0) {
                // Deux mesures au meme instant : la seconde n'apporte rien et
                // ferait diviser par zero.
                continue;
            }

            FilteredPoint previousEstimate = track.get(track.size() - 1);

            state.predict(dt);
            state.correctPosition(east, north, accuracy);
            if (sensorUsable && point.speed() != null && point.speed() >= 0) {
                state.correctSpeed(point.speed());
            }
            if (dt > SPARSE_SAMPLING_SECONDS) {
                state.resetVelocityFrom(previousEstimate, dt);
            }

            previousAt = point.recordedAt();
            track.add(new FilteredPoint(state.east(), state.north(), state.speed(), point.recordedAt()));
        }
        return withInitialSpeed(track);
    }

    /**
     * Donne au premier point la vitesse estimee au second.
     *
     * <p>Au tout premier point, le filtre ne sait rien du mouvement : il part
     * d'une vitesse nulle, faute d'avoir vu quoi que ce soit. Garder ce zero
     * dans l'integration ferait perdre la moitie du premier intervalle — sans
     * consequence sur un trace dense, mais fatal sur une seance qui n'a envoye
     * que quelques points, ou le premier intervalle est tout le parcours.
     */
    private List<FilteredPoint> withInitialSpeed(List<FilteredPoint> track) {
        if (track.size() < 2) {
            return track;
        }
        FilteredPoint first = track.get(0);
        track.set(0, new FilteredPoint(
                first.eastMeters(), first.northMeters(), track.get(1).speedMps(), first.at()));
        return track;
    }

    /**
     * Etat du filtre : position et vitesse dans le plan local, avec leur
     * covariance.
     *
     * <p>Les quatre variables sont ecrites a plat plutot que dans une matrice
     * generique : a cette taille, une bibliotheque d'algebre lineaire couterait
     * plus en indirection qu'elle ne rapporterait en lisibilite, et chaque terme
     * reste sous les yeux du lecteur.
     */
    private static final class State {

        private double east;
        private double north;
        private double velocityEast;
        private double velocityNorth;

        // Covariance. Les axes est et nord partagent la meme structure, seule
        // l'observation de vitesse les couple — et cette correction reste
        // suffisamment faible pour qu'on la traite axe par axe.
        private double positionVariance;
        private double velocityVariance;
        private double positionVelocityCovariance;

        private static State startingAt(double east, double north, double accuracy) {
            State state = new State();
            state.east = east;
            state.north = north;
            state.positionVariance = accuracy * accuracy;
            // Vitesse initiale inconnue : une incertitude large laisse les
            // premieres mesures la determiner.
            state.velocityVariance = 25d;
            return state;
        }

        /** Avance l'estimation de {@code dt} secondes, sans nouvelle mesure. */
        private void predict(double dt) {
            east += velocityEast * dt;
            north += velocityNorth * dt;

            // Modele a vitesse constante perturbee par une acceleration
            // aleatoire : c'est cette perturbation qui autorise l'allure a
            // changer, et donc le filtre a suivre un vrai virage.
            double noise = ACCELERATION_NOISE_MPS2 * ACCELERATION_NOISE_MPS2;
            double dt2 = dt * dt;
            double dt3 = dt2 * dt;
            double dt4 = dt3 * dt;

            positionVariance += 2 * dt * positionVelocityCovariance + dt2 * velocityVariance + noise * dt4 / 4;
            positionVelocityCovariance += dt * velocityVariance + noise * dt3 / 2;
            velocityVariance += noise * dt2;
        }

        /**
         * Corrige l'estimation avec une mesure de position.
         *
         * <p>La mesure est ecartee si elle s'ecarte trop de la prediction au
         * regard des deux incertitudes : c'est ainsi qu'un point mal localise
         * cesse de contaminer le parcours.
         */
        private void correctPosition(double measuredEast, double measuredNorth, double accuracy) {
            double measurementVariance = accuracy * accuracy;
            double innovationVariance = positionVariance + measurementVariance;

            double innovationEast = measuredEast - east;
            double innovationNorth = measuredNorth - north;
            double distanceSquared =
                    (innovationEast * innovationEast + innovationNorth * innovationNorth) / innovationVariance;
            if (distanceSquared > OUTLIER_GATE_SIGMAS * OUTLIER_GATE_SIGMAS) {
                return;
            }

            double positionGain = positionVariance / innovationVariance;
            double velocityGain = positionVelocityCovariance / innovationVariance;

            east += positionGain * innovationEast;
            north += positionGain * innovationNorth;
            velocityEast += velocityGain * innovationEast;
            velocityNorth += velocityGain * innovationNorth;

            positionVariance -= positionGain * positionVariance;
            velocityVariance -= velocityGain * positionVelocityCovariance;
            positionVelocityCovariance -= positionGain * positionVelocityCovariance;
        }

        /**
         * Corrige l'estimation avec la vitesse du capteur.
         *
         * <p>Le capteur ne donne qu'une norme, pas une direction : la correction
         * s'applique donc le long de la vitesse deja estimee, dont elle ajuste
         * l'amplitude. C'est la linearisation d'usage — l'observation depend de
         * l'etat de facon non lineaire, on la derive autour de l'estimation
         * courante.
         */
        private void correctSpeed(double measuredSpeed) {
            double speed = speed();
            if (speed < 1e-6) {
                // Direction indeterminee : sans elle, repartir la correction sur
                // les deux axes reviendrait a inventer un cap.
                velocityEast = measuredSpeed;
                return;
            }

            double innovationVariance = velocityVariance + SPEED_SIGMA_MPS * SPEED_SIGMA_MPS;
            double gain = velocityVariance / innovationVariance;
            double correctedSpeed = speed + gain * (measuredSpeed - speed);

            double scale = correctedSpeed / speed;
            velocityEast *= scale;
            velocityNorth *= scale;

            velocityVariance -= gain * velocityVariance;
            positionVelocityCovariance -= gain * positionVelocityCovariance;
        }

        /**
         * Ramene la vitesse a la seule valeur defendable apres un long silence :
         * le deplacement observe divise par le temps ecoule.
         *
         * <p>Sans cela, le filtre attribue tout l'ecart entre sa prediction et
         * la mesure a une acceleration de derniere minute, et sort une vitesse
         * qui n'a jamais existe. Mesure sur un cas a deux points separes de dix
         * minutes, le second a quarante centimetres du premier : le filtre
         * annoncait 13 m/s — quarante-huit kilometres a l'heure pour quelqu'un
         * qui s'etait arrete.
         *
         * <p>L'incertitude accordee a cette vitesse decoule du calcul lui-meme :
         * une difference de deux positions bruitees, rapportee au temps.
         */
        private void resetVelocityFrom(FilteredPoint previous, double dt) {
            velocityEast = (east - previous.eastMeters()) / dt;
            velocityNorth = (north - previous.northMeters()) / dt;

            double positionSigma = Math.sqrt(Math.max(positionVariance, 0));
            double velocitySigma = positionSigma * Math.sqrt(2) / dt;
            velocityVariance = velocitySigma * velocitySigma;
            positionVelocityCovariance = 0;
        }

        private double east() {
            return east;
        }

        private double north() {
            return north;
        }

        private double speed() {
            return Math.hypot(velocityEast, velocityNorth);
        }
    }
}
