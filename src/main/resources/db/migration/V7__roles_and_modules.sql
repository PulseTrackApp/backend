-- Roles et modules verrouillables.
--
-- Jusqu'ici l'API n'avait qu'un seul niveau de privilege : connecte ou non. Il
-- devient necessaire de distinguer un administrateur, et surtout d'ouvrir ou de
-- fermer une fonctionnalite compte par compte depuis l'application desktop
-- d'administration.

-- `default 'USER'` et non une colonne nullable : un compte sans role n'a aucun
-- sens, et la valeur par defaut evite d'avoir a retoucher les lignes existantes
-- dans un second temps.
alter table users add column role varchar(16) not null default 'USER';

-- Liste d'autorisation : une ligne par module ACCORDE. L'absence de ligne vaut
-- refus. C'est plus sur qu'une liste d'interdiction, ou l'oubli d'une ligne
-- ouvrirait un acces au lieu de le fermer.
--
-- Cle primaire technique plutot que composite (user_id, module) : c'est la
-- convention de toutes les autres tables du schema, et l'unicite du couple est
-- garantie par l'index unique ci-dessous.
create table user_modules (
    id         uuid        primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    -- Chaine et non enum PostgreSQL : ajouter un module ne doit pas exiger une
    -- migration de type, seulement une nouvelle valeur cote Java.
    module     varchar(32) not null,
    -- Trace de l'octroi. Un ecran d'administration qui modifie des droits sans
    -- laisser d'horodatage rend toute enquete ulterieure impossible.
    granted_at timestamptz not null
);

-- Unique : accorder deux fois le meme module au meme compte n'a pas de sens et
-- fausserait le comptage. Sert aussi d'index de lecture, `user_id` etant en
-- tete — c'est la requete faite a chaque appel authentifie.
create unique index ux_user_modules_user_module on user_modules (user_id, module);

-- Les comptes existants gardent tout ce qu'ils avaient : le verrouillage est
-- une restriction que l'administrateur pose sciemment, pas un parcours
-- d'activation impose retroactivement. Sans ce remplissage, la migration
-- couperait l'application a tout le monde d'un coup.
insert into user_modules (id, user_id, module, granted_at)
select gen_random_uuid(), u.id, m.module, now()
from users u
cross join (values
    ('WORKOUTS'),
    ('BODY_CHECKINS'),
    ('GOALS'),
    ('STATS'),
    ('WEEKLY_SUMMARY'),
    ('COACH'),
    ('EXPORT'),
    ('PUSH')
) as m (module);
