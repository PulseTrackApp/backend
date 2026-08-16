-- Droits d'usage, en prevision de la mise en vente de l'application.
--
-- Rien n'est facture a ce jour : la table existe pour qu'un droit puisse etre
-- accorde, retire ou constate a la main, et pour que l'application mobile
-- construise son ecran de paiement contre un contrat reel plutot que contre une
-- promesse.
--
-- ATTENTION a ce que cette table NE contient PAS : les comptes en periode
-- d'essai n'y figurent pas. L'essai se calcule depuis `users.created_at`, ce qui
-- evite d'ecrire une ligne a chaque inscription et surtout d'avoir a en
-- fabriquer retroactivement pour tout le parc le jour de la mise en vente. Une
-- ligne n'apparait donc que lorsqu'un droit est accorde ou retire volontairement.

create table subscriptions (
    id                 uuid        primary key,
    user_id            uuid        not null references users (id) on delete cascade,
    -- TRIAL, ACTIVE, EXPIRED, NONE. Chaine et non enum PostgreSQL : ajouter un
    -- etat ne doit pas exiger une migration de type.
    status             varchar(16) not null,
    -- Offre souscrite. Le catalogue vit en configuration, pas en base : les prix
    -- doivent pouvoir bouger sans migration. On ne stocke donc que le code.
    plan_code          varchar(40),
    -- Dernier jour INCLUS de validite. Nul pour un droit sans echeance : compte
    -- offert a vie, ou compte suspendu.
    current_period_end date,
    -- Pourquoi ce droit a ete pose. Un acces accorde sans explication devient
    -- inexplicable six mois plus tard.
    note               varchar(500),
    created_at         timestamptz not null,
    updated_at         timestamptz not null
);

-- Un seul droit par compte. Deux lignes concurrentes rendraient indecidable la
-- question « ce compte a-t-il acces », ce qui est la seule question posee ici.
create unique index ux_subscriptions_user on subscriptions (user_id);
