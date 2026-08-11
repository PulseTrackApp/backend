-- Reinitialisation de mot de passe.
--
-- Sans cette table, un utilisateur qui oublie son mot de passe n'a aucun
-- recours : rien dans l'API ne permet d'en changer sans connaitre l'ancien.

create table password_reset_tokens (
    id         uuid        primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    -- Empreinte SHA-256 en hexadecimal du code envoye par courriel, jamais le
    -- code lui-meme : une copie de la base ne permet donc de reinitialiser
    -- aucun compte. Meme raisonnement que pour les jetons de renouvellement.
    token_hash varchar(64) not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    -- Renseigne a la consommation. La ligne est conservee jusqu'a son
    -- expiration plutot que supprimee : c'est ce qui permet de distinguer un
    -- code deja utilise d'un code inconnu, et de refuser le second usage.
    used_at    timestamptz
);

create unique index ux_password_reset_tokens_hash on password_reset_tokens (token_hash);

-- Sert a invalider les demandes precedentes quand une nouvelle est faite : deux
-- codes valides en meme temps pour un meme compte doublent la surface d'attaque
-- sans rendre service a personne.
create index ix_password_reset_tokens_user on password_reset_tokens (user_id);
