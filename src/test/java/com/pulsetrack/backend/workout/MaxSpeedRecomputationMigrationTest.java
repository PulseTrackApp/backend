package com.pulsetrack.backend.workout;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.AbstractApiIntegrationTest;
import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.workout.dto.GpsPointRequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie que le recalcul du pic de vitesse ecrit en SQL (migration V9) donne
 * exactement ce que produit {@link WorkoutMetricsCalculator}.
 *
 * <p>C'est le seul controle qui vaille : la migration reecrit des donnees
 * historiques qu'on ne pourra pas comparer a un original, et elle rejoue en SQL
 * une regle ecrite en Java. Une divergence entre les deux — un arrondi, une
 * troncature de seconde, un point compte de travers — remplacerait un chiffre
 * faux par un autre chiffre faux, sans que rien ne le signale.
 *
 * <p>Le test execute le fichier de migration lui-meme, lu depuis le classpath,
 * plutot qu'une copie de son contenu : une copie divergerait au premier
 * ajustement, et le test continuerait de passer en validant du SQL qui ne
 * tourne nulle part.
 */
class MaxSpeedRecomputationMigrationTest extends AbstractApiIntegrationTest {

    /** Valeur du pic telle que l'ancienne formule l'avait figee, en km/h. */
    private static final double PIC_FANTOME = 23.5;

    private static final double POIDS_KG = 75;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Le cas reel : une marche ou un point mal localise a produit un saut de
     * vingt metres en trois secondes, alors que sa precision annoncee etait de
     * 22,8 metres. Le deplacement tient dans l'incertitude, il ne prouve donc
     * aucun mouvement.
     */
    @Test
    void ramene_le_pic_fantome_a_ce_que_calcule_le_code_java() {
        List<GpsPointRequest> trace = marcheAvecSautDeGps();
        WorkoutMetrics attendu = metriquesJava(trace);
        UUID seance = insereSeance(trace, attendu, PIC_FANTOME);

        executeLaMigration();

        // Le pic corrige doit valoir ce que le calculateur Java produit
        // aujourd'hui, au bit pres.
        assertThat(picEnBase(seance)).isEqualTo(attendu.maxSpeedKmh());
        // Et il doit vraiment avoir baisse : sans cela le test passerait aussi
        // avec une migration qui ne fait rien.
        assertThat(picEnBase(seance)).isLessThan(PIC_FANTOME);
    }

    /**
     * Un trace franc, ou chaque segment depasse largement l'incertitude : le
     * pic doit venir des segments eux-memes, et la migration doit retrouver la
     * meme valeur que Java.
     */
    @Test
    void retrouve_le_meme_pic_sur_un_trace_sans_bruit() {
        List<GpsPointRequest> trace = courseFranche();
        WorkoutMetrics attendu = metriquesJava(trace);
        UUID seance = insereSeance(trace, attendu, PIC_FANTOME);

        executeLaMigration();

        assertThat(picEnBase(seance)).isEqualTo(attendu.maxSpeedKmh());
    }

    /**
     * Capteur muet : le pic retombe sur les positions, et la migration doit
     * suivre le meme repli que le code Java. Une migration qui se contenterait
     * de lire le capteur ecrirait ici la vitesse moyenne, et toutes les seances
     * enregistrees sans capteur perdraient leur pic d'un coup.
     */
    @Test
    void retombe_sur_les_positions_quand_le_capteur_se_tait() {
        List<GpsPointRequest> trace = courseSansCapteur();
        WorkoutMetrics attendu = metriquesJava(trace);
        UUID seance = insereSeance(trace, attendu, PIC_FANTOME);

        executeLaMigration();

        assertThat(picEnBase(seance)).isEqualTo(attendu.maxSpeedKmh());
        // Le repli doit vraiment produire un pic, pas se replier sur la moyenne.
        assertThat(picEnBase(seance)).isGreaterThan(attendu.averageSpeedKmh());
    }

    /**
     * Garde-fou contre les degats collateraux : une seance deja correcte doit
     * traverser la migration sans bouger. C'est ce qui rend l'operation
     * rejouable sans risque a chaque demarrage.
     */
    @Test
    void ne_touche_pas_une_seance_deja_correcte() {
        List<GpsPointRequest> trace = courseFranche();
        WorkoutMetrics correct = metriquesJava(trace);
        UUID seance = insereSeance(trace, correct, correct.maxSpeedKmh());

        executeLaMigration();
        executeLaMigration();

        assertThat(picEnBase(seance)).isEqualTo(correct.maxSpeedKmh());
    }

    /**
     * Une seance sans trace exploitable n'a aucun segment : son pic vaut sa
     * vitesse moyenne, et la migration n'a rien a en dire. Sans le filtre sur
     * les seances a deux points minimum, elle ecrirait ici une valeur nulle.
     */
    @Test
    void laisse_en_l_etat_une_seance_sans_trace() {
        WorkoutMetrics sansGps = metriquesJava(List.of());
        UUID seance = insereSeance(List.of(), sansGps, sansGps.maxSpeedKmh());

        executeLaMigration();

        assertThat(picEnBase(seance)).isEqualTo(sansGps.maxSpeedKmh());
    }

    // --- construction des traces -------------------------------------------

    /**
     * Marche a 1,4 m/s, un point toutes les trois secondes, precision cinq
     * metres — et au milieu un point annonce a 22,8 metres de precision qui
     * saute de vingt metres. Le capteur, lui, n'a jamais annonce plus de
     * 1,72 m/s (6,2 km/h).
     */
    private List<GpsPointRequest> marcheAvecSautDeGps() {
        List<GpsPointRequest> points = new ArrayList<>();
        Instant depart = Instant.parse("2026-08-11T18:00:00Z");
        double latitude = 12.3714;
        double longitude = -1.5197;

        for (int i = 0; i < 12; i++) {
            boolean sautDeGps = i == 6;
            // 4,2 m par pas de trois secondes, sauf le point aberrant qui saute
            // de vingt metres. 0,000009 degre de latitude vaut environ un metre.
            double avance = sautDeGps ? 20 * 0.000009 : 4.2 * 0.000009;
            latitude += avance;

            points.add(new GpsPointRequest(
                    latitude,
                    longitude,
                    120.0,
                    sautDeGps ? 22.8 : 5.0,
                    sautDeGps ? null : 1.72,
                    depart.plusSeconds(3L * i)));
        }
        return points;
    }

    /**
     * Course a cinquante metres toutes les dix secondes, ou le capteur annonce
     * 4,5 m/s quand les positions en suggerent 5.
     *
     * <p>L'ecart est voulu : il fait echouer le test si la migration derive le
     * pic des positions alors que le capteur couvre le trace. Sans lui, les deux
     * regles donneraient le meme chiffre et le test ne prouverait rien.
     */
    private List<GpsPointRequest> courseFranche() {
        List<GpsPointRequest> points = new ArrayList<>();
        Instant depart = Instant.parse("2026-08-11T06:00:00Z");
        double latitude = 12.4000;

        for (int i = 0; i < 10; i++) {
            latitude += 50 * 0.000009;
            points.add(new GpsPointRequest(
                    latitude, -1.5000, 300.0, 4.0, 4.5, depart.plusSeconds(10L * i)));
        }
        return points;
    }

    /**
     * Meme trace, capteur muet : le pic ne peut alors venir que des positions.
     * C'est le repli, et il doit rester exact.
     */
    private List<GpsPointRequest> courseSansCapteur() {
        List<GpsPointRequest> points = new ArrayList<>();
        Instant depart = Instant.parse("2026-08-11T07:00:00Z");
        double latitude = 12.4500;

        for (int i = 0; i < 10; i++) {
            // Une pointe franche au milieu — 80 m au lieu de 50 — pour que le
            // pic se distingue de la moyenne : sans elle, une allure constante
            // rendrait les deux egaux et le test ne dirait plus rien.
            latitude += (i == 5 ? 80 : 50) * 0.000009;
            points.add(new GpsPointRequest(
                    latitude, -1.5000, 300.0, 4.0, null, depart.plusSeconds(10L * i)));
        }
        return points;
    }

    // --- utilitaires -------------------------------------------------------

    private WorkoutMetrics metriquesJava(List<GpsPointRequest> trace) {
        Instant debut = trace.isEmpty()
                ? Instant.parse("2026-08-11T18:00:00Z")
                : trace.get(0).recordedAt();
        Instant fin = trace.isEmpty()
                ? debut.plus(30, ChronoUnit.MINUTES)
                : trace.get(trace.size() - 1).recordedAt();

        return new WorkoutMetricsCalculator()
                .calculate(SportType.WALK, debut, fin, trace, 1000d, POIDS_KG);
    }

    /**
     * Ecrit la seance directement en SQL, avec un pic impose : c'est la seule
     * facon de reproduire une ligne telle que l'ancienne formule l'avait
     * laissee, l'API refusant par construction d'enregistrer autre chose que ce
     * que le calculateur produit.
     */
    private UUID insereSeance(List<GpsPointRequest> trace, WorkoutMetrics metriques, double picEnregistre) {
        UUID utilisateur = UUID.randomUUID();
        jdbc.update("""
                insert into users (id, email, password_hash, created_at, role, email_verified)
                values (?, ?, '{noop}peu-importe', now(), 'USER', true)
                """, utilisateur, "recalcul-" + utilisateur + "@pulsetrack.test");

        UUID seance = UUID.randomUUID();
        Instant debut = trace.isEmpty()
                ? Instant.parse("2026-08-11T18:00:00Z")
                : trace.get(0).recordedAt();
        Instant fin = debut.plusSeconds(metriques.durationSeconds());

        jdbc.update("""
                insert into workout_sessions (
                    id, user_id, sport_type, started_at, ended_at,
                    duration_seconds, moving_duration_seconds, distance_meters,
                    average_pace_seconds_per_km, average_speed_kmh, max_speed_kmh,
                    elevation_gain_meters, calories_burned, created_at)
                values (?, ?, 'WALK', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
                seance, utilisateur,
                java.sql.Timestamp.from(debut), java.sql.Timestamp.from(fin),
                metriques.durationSeconds(), metriques.movingDurationSeconds(), metriques.distanceMeters(),
                metriques.averagePaceSecondsPerKm(), metriques.averageSpeedKmh(), picEnregistre,
                metriques.elevationGainMeters(), metriques.caloriesBurned());

        for (int i = 0; i < trace.size(); i++) {
            GpsPointRequest point = trace.get(i);
            jdbc.update("""
                    insert into gps_points (
                        workout_session_id, position, latitude, longitude,
                        altitude, accuracy, speed, recorded_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    seance, i, point.latitude(), point.longitude(),
                    point.altitude(), point.accuracy(), point.speed(),
                    java.sql.Timestamp.from(point.recordedAt()));
        }
        return seance;
    }

    /**
     * Rejoue les fichiers de migration tels qu'ils partent en production, dans
     * leur ordre de version — c'est exactement ce qu'une base existante a subi.
     */
    private void executeLaMigration() {
        jdbc.update(sqlDeLaMigration("V9__recompute_max_speed.sql"));
        jdbc.update(sqlDeLaMigration("V10__max_speed_from_sensor.sql"));
    }

    private String sqlDeLaMigration(String fichier) {
        try {
            return new String(new ClassPathResource("db/migration/" + fichier)
                    .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Migration " + fichier + " introuvable dans le classpath", e);
        }
    }

    private double picEnBase(UUID seance) {
        Double pic = jdbc.queryForObject(
                "select max_speed_kmh from workout_sessions where id = ?", Double.class, seance);
        return pic == null ? 0d : pic;
    }
}
