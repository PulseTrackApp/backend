-- Recalcule le pic de vitesse des seances deja enregistrees.
--
-- Les metriques d'une seance sont figees a l'enregistrement : corriger la
-- formule cote Java ne repare donc rien de ce qui est deja en base. Or le
-- calcul du pic retenait des segments dont le deplacement tenait entierement
-- dans l'incertitude annoncee par le GPS. Un seul point mal localise suffit :
-- une marche du 11 aout 2026 affiche 23,5 km/h alors que le capteur du
-- telephone n'a jamais depasse 6,2 km/h — un point a 22,8 metres de precision
-- avait produit un saut de vingt metres en trois secondes. Un maximum garde le
-- pire echantillon pour toujours.
--
-- Cette migration rejoue exactement la regle de `WorkoutMetricsCalculator`
-- apres correctif, et rien d'autre :
--
--   * un segment ne compte que si son deplacement depasse la pire des deux
--     precisions annoncees (10 m par defaut quand le capteur se tait) ;
--   * la vitesse annoncee par le capteur est retenue telle quelle, elle ne
--     souffre pas du bruit de position ;
--   * le resultat ne peut pas descendre sous la vitesse moyenne.
--
-- Seul `max_speed_kmh` est touche. La distance, la duree en mouvement, le
-- denivele et les calories reposent sur des formules inchangees : les
-- recalculer ne ferait que risquer de les abimer.
--
-- Les seances de moins de deux points n'ont aucun segment et sont laissees en
-- l'etat : leur pic vaut deja leur vitesse moyenne, ce que la formule produit.
--
-- Une nuance assumee : le plancher applique ici est la vitesse moyenne
-- *enregistree*, deja arrondie au centieme, la ou le code Java compare a la
-- valeur non arrondie. L'ecart possible vaut un demi-centieme de km/h, et
-- seulement dans le cas ou aucun segment ne sort du bruit — sinon le pic domine
-- largement la moyenne. Recalculer la moyenne ici demanderait de rejouer la
-- distance et le temps en mouvement, donc de risquer d'abimer deux valeurs
-- justes pour affiner un cas ou l'affichage ne bougerait pas.
--
-- L'operation est idempotente : la rejouer sur des donnees deja corrigees ne
-- change rien.

with segment as (
    select
        p.workout_session_id                                            as session_id,
        -- Haversine, identique a la formule Java (rayon terrestre 6 371 km).
        2 * 6371000 * asin(least(1, sqrt(
            power(sin(radians(p.latitude - lag(p.latitude) over trace) / 2), 2)
            + cos(radians(lag(p.latitude) over trace)) * cos(radians(p.latitude))
              * power(sin(radians(p.longitude - lag(p.longitude) over trace) / 2), 2)
        )))                                                             as meters,
        -- Tronque a la seconde entiere, comme `Duration.getSeconds()` cote Java.
        floor(extract(epoch from (p.recorded_at - lag(p.recorded_at) over trace))) as seconds,
        -- Plancher de bruit : la pire des deux precisions annoncees.
        greatest(
            coalesce(lag(p.accuracy) over trace, 10.0),
            coalesce(p.accuracy, 10.0),
            0.0)                                                        as noise_floor,
        p.speed                                                         as sensor_speed,
        lag(p.recorded_at) over trace                                   as previous_at
    from gps_points p
    -- `position` et non `recorded_at` : c'est l'ordre dans lequel le trace a ete
    -- envoye, donc celui que le calcul d'origine a parcouru.
    window trace as (partition by p.workout_session_id order by p.position)
),
recomputed as (
    select
        session_id,
        greatest(
            -- Segments dont le deplacement sort du bruit.
            max(case when seconds > 0 and meters > noise_floor then meters / seconds end),
            -- Vitesse du capteur, qui ne depend d'aucune difference de position.
            max(case when sensor_speed > 0 then sensor_speed end)
        ) as max_speed_mps
    from segment
    -- La premiere ligne de chaque trace n'a pas de precedent : ce n'est pas un
    -- segment. Cote Java, la boucle demarre elle aussi au deuxieme point.
    where previous_at is not null
    group by session_id
)
update workout_sessions s
set max_speed_kmh = round(
        greatest(r.max_speed_mps * 3.6, s.average_speed_kmh)::numeric, 2)::double precision
from recomputed r
where r.session_id = s.id
  -- N'ecrit que ce qui change reellement : une migration qui reecrit toutes les
  -- lignes rend illisible le journal de ce qu'elle a corrige.
  and round(greatest(r.max_speed_mps * 3.6, s.average_speed_kmh)::numeric, 2)::double precision
      is distinct from s.max_speed_kmh;
