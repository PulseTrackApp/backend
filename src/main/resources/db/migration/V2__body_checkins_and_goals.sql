-- Suivi de l'evolution physique et objectifs de l'utilisateur.

create table body_checkins (
    id                   uuid             primary key,
    user_id              uuid             not null references users (id) on delete cascade,
    checkin_date         date             not null,
    weight_kg            double precision not null,
    waist_cm             double precision,
    chest_cm             double precision,
    hips_cm              double precision,
    -- Energie ressentie de 1 (epuise) a 5 (en pleine forme).
    energy_level         integer,
    average_sleep_hours  double precision,
    note                 varchar(2000),
    created_at           timestamptz      not null
);

-- Un seul releve par jour et par personne : deux pesees le meme jour decrivent
-- le meme etat, et la seconde doit corriger la premiere, pas s'y ajouter.
-- Cet index sert aussi aux lectures ordonnees par date (Postgres le parcourt
-- en sens inverse), inutile d'en creer un second.
create unique index ux_body_checkins_user_date on body_checkins (user_id, checkin_date);

-- La spec produit prevoyait une colonne `period` a cote du `type`. Elle est
-- volontairement absente : la periode est deja portee par le type
-- (WEEKLY_DISTANCE est hebdomadaire par definition), et deux champs pouvant se
-- contredire finissent toujours par se contredire.
create table goals (
    id           uuid             primary key,
    user_id      uuid             not null references users (id) on delete cascade,
    type         varchar(40)      not null,
    target_value double precision not null,
    start_date   date             not null,
    end_date     date,
    active       boolean          not null,
    created_at   timestamptz      not null,
    updated_at   timestamptz      not null
);

-- Index partiel : l'unicite ne porte que sur les objectifs actifs. On garde donc
-- l'historique des anciens objectifs de distance hebdomadaire, tout en
-- garantissant qu'un seul est en cours a la fois. Sans le `where`, archiver un
-- objectif deviendrait impossible sans le supprimer.
create unique index ux_goals_user_type_active on goals (user_id, type) where active;

create index ix_goals_user_active on goals (user_id, active);
