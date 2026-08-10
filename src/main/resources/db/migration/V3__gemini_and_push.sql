-- Assistant Gemini et notifications push.

create table gemini_settings (
    user_id                 uuid        primary key references users (id) on delete cascade,
    enabled                 boolean     not null,
    -- Clé API de l'utilisateur, CHIFFREE (AES-256-GCM). Jamais en clair, jamais
    -- renvoyee par l'API : le client apprend seulement qu'une cle existe.
    -- Le champ est large car le chiffrement encode en hexadecimal, ce qui double
    -- la taille, et le sel plus le vecteur d'initialisation s'y ajoutent.
    encrypted_api_key       varchar(1024),
    coaching_tone           varchar(20) not null,
    weekly_review_enabled   boolean     not null,
    effort_warnings_enabled boolean     not null,
    created_at              timestamptz not null,
    updated_at              timestamptz not null
);

create table coach_messages (
    id           uuid         primary key,
    user_id      uuid         not null references users (id) on delete cascade,
    kind         varchar(30)  not null,
    -- Lundi de la semaine analysee, pour les bilans hebdomadaires. Nul pour une
    -- question libre, qui ne se rattache a aucune semaine.
    week_start   date,
    prompt       text         not null,
    content      text         not null,
    model        varchar(60)  not null,
    created_at   timestamptz  not null
);

-- Un seul bilan par semaine et par personne : le dashboard le relit sans
-- rappeler l'API Gemini, ce qui preserve le quota de l'utilisateur.
-- Index partiel car `week_start` est nul pour les questions libres, qui elles
-- peuvent se repeter autant qu'on veut.
create unique index ux_coach_messages_weekly
    on coach_messages (user_id, kind, week_start)
    where week_start is not null;

create index ix_coach_messages_user_created on coach_messages (user_id, created_at desc);

create table device_tokens (
    id           uuid         primary key,
    user_id      uuid         not null references users (id) on delete cascade,
    -- Jeton d'enregistrement FCM. Unique globalement : un meme telephone reinstalle
    -- avec un autre compte doit changer de proprietaire, pas etre duplique.
    token        varchar(512) not null,
    platform     varchar(20)  not null,
    created_at   timestamptz  not null,
    last_seen_at timestamptz  not null
);

create unique index ux_device_tokens_token on device_tokens (token);
create index ix_device_tokens_user on device_tokens (user_id);
