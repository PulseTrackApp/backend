-- Deuxieme passe sur le pic de vitesse : le capteur prime sur les positions.
--
-- La migration V9 avait ecarte le gros bruit — les sauts de position plus
-- courts que la precision annoncee. Elle ne suffit pas. Mesure faite sur la
-- marche du 11 aout 2026, une fois V9 appliquee : le pic tombait de 23,5 a
-- 11,1 km/h, alors que le capteur du telephone n'avait jamais annonce plus de
-- 6,2 km/h sur aucun des 541 points.
--
-- La raison tient a l'echantillonnage. Un point toutes les deux secondes, une
-- precision de quatre metres : marcher deplace de 2,8 metres, et trois metres
-- de tremblement suffisent a doubler la vitesse apparente. Le deplacement reel
-- est du meme ordre que le bruit, aucun seuil raisonnable ne les separe.
--
-- La vitesse du capteur, elle, vient de l'effet Doppler et ne depend d'aucune
-- difference de position. Quand elle couvre le trace, c'est elle qui fait foi.
-- Les positions restent le repli quand le telephone se tait, et reprennent la
-- main si le capteur n'a jamais annonce le moindre mouvement — certains
-- appareils renvoient zero en permanence, et les croire effacerait le pic d'une
-- seance qui a pourtant eu lieu.
--
-- Meme regle que `WorkoutMetricsCalculator` apres correctif, meme perimetre que
-- V9 : seul `max_speed_kmh` est touche, et l'operation est idempotente.

with segment as (
    select
        p.workout_session_id                                            as session_id,
        2 * 6371000 * asin(least(1, sqrt(
            power(sin(radians(p.latitude - lag(p.latitude) over trace) / 2), 2)
            + cos(radians(lag(p.latitude) over trace)) * cos(radians(p.latitude))
              * power(sin(radians(p.longitude - lag(p.longitude) over trace) / 2), 2)
        )))                                                             as meters,
        floor(extract(epoch from (p.recorded_at - lag(p.recorded_at) over trace))) as seconds,
        greatest(
            coalesce(lag(p.accuracy) over trace, 10.0),
            coalesce(p.accuracy, 10.0),
            0.0)                                                        as noise_floor,
        p.speed                                                         as sensor_speed,
        lag(p.recorded_at) over trace                                   as previous_at
    from gps_points p
    window trace as (partition by p.workout_session_id order by p.position)
),
measured as (
    select
        session_id,
        count(*)                                                        as segments,
        count(sensor_speed)                                             as sensor_samples,
        max(sensor_speed)                                               as sensor_max_mps,
        max(case when seconds > 0 and meters > noise_floor then meters / seconds end) as position_max_mps
    from segment
    where previous_at is not null
    group by session_id
),
recomputed as (
    select
        session_id,
        case
            -- Quatre cinquiemes des points munis d'une vitesse, et une vitesse
            -- non nulle quelque part : le capteur suffit.
            when sensor_samples >= 0.8 * segments and sensor_max_mps > 0
                then sensor_max_mps
            else greatest(sensor_max_mps, position_max_mps)
        end as max_speed_mps
    from measured
)
update workout_sessions s
set max_speed_kmh = round(
        greatest(r.max_speed_mps * 3.6, s.average_speed_kmh)::numeric, 2)::double precision
from recomputed r
where r.session_id = s.id
  and round(greatest(r.max_speed_mps * 3.6, s.average_speed_kmh)::numeric, 2)::double precision
      is distinct from s.max_speed_kmh;
