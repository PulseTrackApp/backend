-- Catalogue de tarifs, deplace de la configuration vers la base.
--
-- POURQUOI CE DEPLACEMENT. Jusqu'ici les offres vivaient dans
-- `application.yml`, ce qui suffisait tant que personne ne devait les changer :
-- corriger un prix demandait de modifier une variable d'environnement et de
-- redeployer. A partir du moment ou l'application d'administration doit gerer la
-- tarification, ce n'est plus tenable — un ecran ne peut pas ecrire dans un
-- fichier de configuration.
--
-- LA CONFIGURATION RESTE, en amorce seulement : au premier demarrage, si cette
-- table est vide, les offres declarees dans `pulsetrack.billing.plans` y sont
-- recopiees. Ensuite la base fait foi et la configuration n'est plus relue. Cela
-- evite deux ennuis : un catalogue vide au premier lancement, et un catalogue
-- corrige a l'ecran puis silencieusement ecrase au redemarrage suivant.
--
-- CE QUI RESTE EN CONFIGURATION, et pourquoi : `enforced` et `trial-days`. Ce ne
-- sont pas des tarifs mais des interrupteurs de deploiement, et `enforced`
-- surtout ne doit pas pouvoir se basculer d'un clic — il ferme l'application a
-- tous ceux qui n'ont pas de droit, et il n'a de sens qu'apres le verrou de
-- version. Un tel geste merite de passer par l'hebergeur, pas par un bouton.

create table billing_plans (
    -- Le code est la cle : c'est lui que `subscriptions.plan_code` reference, et
    -- lui qui circule dans l'API. Une cle technique en plus n'apporterait rien
    -- et laisserait croire qu'un code peut se renommer sans consequence.
    code          varchar(40)  primary key,
    name          varchar(120) not null,
    description   varchar(500) not null default '',
    -- Montant dans l'unite courante de la devise. Le franc CFA n'a pas de
    -- centime, d'ou un entier ; une devise a centimes se stockera en centimes.
    price_amount  bigint       not null,
    currency      varchar(10)  not null,
    -- MONTHLY, YEARLY, LIFETIME.
    period        varchar(20)  not null,
    -- COMING_SOON, AVAILABLE, RETIRED. Chaines et non enums PostgreSQL : ajouter
    -- un etat ne doit pas exiger une migration de type.
    availability  varchar(20)  not null,
    highlighted   boolean      not null default false,
    -- Avantages, un par ligne. Une table fille pour trois puces se paierait a
    -- chaque lecture et a chaque ecriture, alors que l'ecran d'administration
    -- edite precisement un bloc de texte multiligne.
    features      text         not null default '',
    -- Ordre d'affichage a l'ecran de tarifs. Le prix ne suffit pas : c'est un
    -- choix de presentation, pas une consequence du montant.
    display_order integer      not null default 0,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null
);

-- Une seule offre mise en avant a la fois. Contrainte partielle plutot que
-- regle applicative seule : deux offres « recommandees » rendraient indecidable
-- celle que le refus de paiement doit proposer.
create unique index ux_billing_plans_highlighted
    on billing_plans (highlighted)
    where highlighted;
