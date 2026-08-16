-- Motivation : parcours rejouables, defis chronometres et trophees.
--
-- Trois tables nouvelles et deux colonnes sur les seances. La logique commune
-- aux trois : ce qui se calcule a la lecture n'est pas stocke (les records
-- courants, les classements), ce qui date un evenement l'est (un trophee tombe
-- un jour precis et ne doit pas changer de sens quand la formule evolue).

-- ---------------------------------------------------------------------------
-- Parcours enregistres
-- ---------------------------------------------------------------------------

create table saved_routes (
    id                    uuid             primary key,
    user_id               uuid             not null references users (id) on delete cascade,
    name                  varchar(120)     not null,
    sport_type            varchar(16)      not null,
    -- Distance de la seance d'origine, telle que le filtre de Kalman l'a
    -- estimee. Elle n'est PAS recalculee depuis les points simplifies : ceux-ci
    -- servent a dessiner une carte, pas a mesurer.
    distance_meters       double precision not null,
    elevation_gain_meters double precision not null,
    -- Vrai quand l'arrivee est a moins de cent metres du depart : c'est ce qui
    -- distingue un circuit d'un aller simple, et cela change le libelle affiche.
    is_loop               boolean          not null,
    point_count           integer          not null,
    -- La seance d'origine peut etre supprimee sans emporter le parcours : une
    -- fois nomme et repris, le circuit a une vie propre.
    source_workout_id     uuid             references workout_sessions (id) on delete set null,
    created_at            timestamptz      not null,
    updated_at            timestamptz      not null
);

-- Deux parcours du meme nom chez la meme personne rendraient l'ecran de choix
-- indechiffrable. La casse est ignoree : « Boucle du barrage » et « boucle du
-- barrage » designent le meme circuit.
create unique index ux_saved_routes_user_name on saved_routes (user_id, lower(name));

-- Index de lecture de la liste, du plus recent au plus ancien.
create index ix_saved_routes_user_created on saved_routes (user_id, created_at desc);

-- Points du trace simplifie. Meme raisonnement que pour `gps_points` : ces
-- lignes se comptent par centaines, ne sont jamais adressees individuellement,
-- et une sequence permet l'ecriture par lots la ou une identite imposerait un
-- aller-retour par ligne.
create table saved_route_points (
    id                         bigint           primary key,
    route_id                   uuid             not null references saved_routes (id) on delete cascade,
    position                   integer          not null,
    latitude                   double precision not null,
    longitude                  double precision not null,
    altitude                   double precision,
    -- Distance parcourue depuis le depart, pour afficher « tu es au km 3,2 »
    -- sans que le client ait a sommer quoi que ce soit.
    cumulative_distance_meters double precision not null
);

-- Le pas de 50 doit rester egal a `allocationSize` cote entite : un ecart entre
-- les deux produirait des cles en double.
create sequence saved_route_points_seq increment by 50 start with 1;

create index ix_saved_route_points_route on saved_route_points (route_id, position);

-- ---------------------------------------------------------------------------
-- Defis
-- ---------------------------------------------------------------------------

create table challenges (
    id                        uuid             primary key,
    user_id                   uuid             not null references users (id) on delete cascade,
    title                     varchar(120)     not null,
    sport_type                varchar(16)      not null,
    target_distance_meters    double precision not null,
    target_duration_seconds   bigint           not null,
    -- Defi pose sur un circuit connu. Le parcours peut disparaitre sans que le
    -- defi perde son sens : distance et duree suffisent a le juger.
    route_id                  uuid             references saved_routes (id) on delete set null,
    -- DRAFT, ACTIVE, SUCCEEDED, FAILED, ABANDONED, EXPIRED.
    status                    varchar(16)      not null,
    -- Date limite pour TENTER le defi, distincte de l'echeance du chronometre.
    expires_on                date,
    created_at                timestamptz      not null,
    started_at                timestamptz,
    -- started_at + target_duration_seconds, fige au depart : recalculer cette
    -- borne a chaque lecture la ferait glisser si la duree cible etait modifiee.
    deadline_at               timestamptz,
    completed_at              timestamptz,
    workout_id                uuid             references workout_sessions (id) on delete set null,
    achieved_distance_meters  double precision,
    achieved_duration_seconds bigint,
    succeeded                 boolean
);

-- Un seul defi arme a la fois. Deux echeances simultanees ne veulent rien dire,
-- et l'ecran de course n'en affiche qu'une. L'index partiel fait respecter la
-- regle en base plutot que de la confier a une verification applicative, qui
-- lacherait au premier double appui sur le bouton « demarrer ».
create unique index ux_challenges_one_active on challenges (user_id) where status = 'ACTIVE';

create index ix_challenges_user_created on challenges (user_id, created_at desc);

-- Balayage quotidien des rappels d'echeance : il ne lit que les defis encore
-- ouverts qui portent une date limite.
create index ix_challenges_pending_expiry on challenges (expires_on)
    where expires_on is not null and status in ('DRAFT', 'ACTIVE');

-- ---------------------------------------------------------------------------
-- Trophees
-- ---------------------------------------------------------------------------

-- Un trophee est un evenement date, pas un etat. Les records COURANTS, eux, se
-- recalculent a la lecture depuis l'historique : sans quoi la suppression d'une
-- seance laisserait un record fantome que plus aucune sortie ne justifie.
--
-- Conserver les trophees a deux raisons concretes : le renvoi d'une seance deja
-- enregistree doit rendre exactement la meme liste, sinon les felicitations
-- explosent deux fois ou pas du tout apres une coupure reseau ; et l'ecran
-- d'historique peut badger les seances remarquables sans recalculer l'ordre
-- chronologique des records a chaque affichage.
create table workout_achievements (
    id             uuid             primary key,
    workout_id     uuid             not null references workout_sessions (id) on delete cascade,
    -- Duplique depuis la seance : toutes les lectures filtrent sur le
    -- proprietaire, et une jointure de plus a chaque affichage d'historique
    -- serait payee pour rien.
    user_id        uuid             not null references users (id) on delete cascade,
    kind           varchar(32)      not null,
    sport_type     varchar(16)      not null,
    unit           varchar(8)       not null,
    -- Nul pour un premier evenement : une premiere seance n'a pas de precedent.
    previous_value double precision,
    new_value      double precision not null,
    achieved_at    timestamptz      not null
);

-- Une seance ne bat un record donne qu'une fois. Sans cette contrainte, un
-- rejeu d'enregistrement ecrirait les memes trophees en double.
create unique index ux_workout_achievements_workout_kind on workout_achievements (workout_id, kind);

create index ix_workout_achievements_user on workout_achievements (user_id, achieved_at desc);

-- ---------------------------------------------------------------------------
-- Rattachement des seances
-- ---------------------------------------------------------------------------

-- `on delete set null` des deux cotes : supprimer un parcours ou un defi ne doit
-- jamais faire disparaitre une seance reellement courue.
alter table workout_sessions
    add column route_id     uuid references saved_routes (id) on delete set null,
    add column challenge_id uuid references challenges (id) on delete set null;

-- Classement des tentatives sur un circuit. Partiel : l'immense majorite des
-- seances n'est rattachee a aucun parcours et n'a rien a faire dans cet index.
create index ix_workout_sessions_route on workout_sessions (route_id, moving_duration_seconds)
    where route_id is not null;

-- ---------------------------------------------------------------------------
-- Ouverture des trois nouveaux modules
-- ---------------------------------------------------------------------------

-- Meme raisonnement qu'en V7 : un module qui apparait ne doit pas se traduire
-- par une rubrique fermee chez les comptes existants. Le verrouillage est un
-- geste que l'administrateur pose sciemment, jamais un effet de bord de
-- migration.
--
-- `on conflict do nothing` rend le remplissage rejouable : Flyway ne repasse
-- pas, mais cette migration a ete appliquee a la main en production par le
-- passe et l'habitude merite d'etre tenue.
insert into user_modules (id, user_id, module, granted_at)
select gen_random_uuid(), u.id, m.module, now()
from users u
cross join (values
    ('CHALLENGES'),
    ('ROUTES'),
    ('RATING')
) as m (module)
on conflict (user_id, module) do nothing;
