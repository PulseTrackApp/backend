-- Suspension d'un compte, sans le supprimer.
--
-- POURQUOI PAS UN SIMPLE `delete`. La suppression existe deja, elle est
-- definitive et efface tout par cascade. Elle ne convient pas au cas courant :
-- fermer l'acces a quelqu'un en attendant d'y voir clair, sans detruire ses
-- donnees ni empecher de revenir en arriere. Ce sont deux gestes differents et
-- ils meritent deux mecanismes differents.
--
-- POURQUOI UNE DATE ET NON UN BOOLEEN. « Depuis quand » est la premiere question
-- qu'on se pose devant un compte ferme, et un booleen ne sait pas y repondre.
-- La date porte les deux informations ; `null` signifie « actif ».
--
-- LA RAISON EST OBLIGATOIRE EN PRATIQUE, meme si la colonne l'autorise vide :
-- une suspension sans explication devient inexplicable six mois plus tard, et
-- c'est le genre de decision qu'il faut pouvoir justifier. C'est le service qui
-- l'exige, pour pouvoir rendre un message clair plutot qu'une violation de
-- contrainte.

alter table users
    add column disabled_at     timestamptz,
    add column disabled_reason varchar(500);

-- Les comptes existants restent actifs : la colonne nait nulle partout, ce qui
-- est exactement l'etat « aucun compte n'est suspendu ».

-- L'ecran d'administration filtre et compte les comptes suspendus ; ils sont
-- par nature une petite minorite, d'ou un index partiel plutot qu'un index
-- complet qui indexerait surtout des `null`.
create index ix_users_disabled on users (disabled_at) where disabled_at is not null;
