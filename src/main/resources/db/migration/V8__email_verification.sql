-- Verification de l'adresse email.
--
-- Jusqu'ici rien ne prouvait qu'une adresse existait vraiment. C'est genant
-- pour un produit dont le seul recours en cas d'oubli de mot de passe est
-- justement un code envoye a cette adresse : une faute de frappe a
-- l'inscription rend le compte irrecuperable, sans que personne s'en apercoive
-- avant le jour ou il faut s'en servir.

-- `default false` : un compte neuf n'est pas verifie tant qu'il n'a pas prouve
-- qu'il lit sa boite aux lettres.
alter table users add column email_verified boolean not null default false;

-- Les comptes deja en base sont tenus pour verifies. Ils sont anterieurs a la
-- fonctionnalite : les marquer comme non verifies leur imposerait
-- retroactivement une formalite qu'on ne leur a jamais demandee, et les
-- couperait net le jour ou la verification devient obligatoire.
update users set email_verified = true;

-- Meme forme que password_reset_tokens, et pour les memes raisons : l'empreinte
-- plutot que le code, et la ligne conservee jusqu'a expiration pour distinguer
-- un code deja consomme d'un code inconnu.
create table email_verification_tokens (
    id         uuid        primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    token_hash varchar(64) not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    used_at    timestamptz
);

create unique index ux_email_verification_tokens_hash on email_verification_tokens (token_hash);

-- Sert a invalider les demandes precedentes quand une nouvelle arrive.
create index ix_email_verification_tokens_user on email_verification_tokens (user_id);
