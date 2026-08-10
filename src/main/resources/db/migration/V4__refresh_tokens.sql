-- Jetons de renouvellement revocables.
--
-- Le jeton d'acces est un JWT : une fois signe, aucun serveur ne peut le
-- rappeler avant son expiration. La revocation d'une session passe donc par ce
-- second jeton, opaque et stocke ici : le supprimer de cette table suffit a
-- couper le renouvellement, et la session s'eteint a l'expiration du jeton
-- d'acces en cours.

create table refresh_tokens (
    id         uuid        primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    -- Empreinte SHA-256 en hexadecimal, jamais le jeton lui-meme : une copie de
    -- la base ne permet donc de se connecter a aucun compte.
    -- SHA-256 et non bcrypt, contrairement aux mots de passe : le jeton est
    -- 256 bits tires au hasard, il n'y a pas de dictionnaire a lui opposer, et
    -- il faut pouvoir le retrouver par index — ce qu'un hachage sale interdit.
    token_hash varchar(64) not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    -- Nul tant que le jeton est actif. Renseigne a la deconnexion et a chaque
    -- rotation : on garde la ligne revoquee jusqu'a son expiration, car c'est
    -- elle qui permet de reconnaitre qu'un jeton vole est rejoue.
    revoked_at timestamptz
);

create unique index ux_refresh_tokens_hash on refresh_tokens (token_hash);

-- Sert a la revocation en masse : au rejeu d'un jeton deja consomme, toutes les
-- sessions du compte sont coupees d'un coup.
create index ix_refresh_tokens_user on refresh_tokens (user_id);
