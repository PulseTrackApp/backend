# PulseTrack — Backend

API REST du suivi sportif PulseTrack. Elle stocke les comptes, les profils
sportifs et les séances, et calcule les métriques (distance, allure, calories)
côté serveur.

> Les notes d'architecture détaillées et la spécification produit sont
> conservées hors du dépôt (dossier `docs/`, non suivi par git).

- **Spring Boot** 4.1.0 (Spring Framework 7, Jackson 3)
- **Java** 17
- **PostgreSQL** 17, schéma géré par Flyway
- **Sécurité** JWT stateless (HS256), via OAuth2 Resource Server

---

## 1. Prérequis

| Outil | Version | Vérifier |
|---|---|---|
| JDK | 17 ou plus | `java -version` |
| Docker | pour PostgreSQL et les tests | `docker info` |
| Maven | **inutile** — le projet embarque `mvnw` | `./mvnw -v` |

`mvnw` (le *Maven wrapper*) télécharge la bonne version de Maven au premier
lancement. On l'utilise partout à la place de `mvn`, pour que la build soit
identique sur toutes les machines.

> Sous PowerShell, écrire `.\mvnw.cmd` au lieu de `./mvnw`.

## 2. Démarrer

```bash
cd backend
./mvnw spring-boot:run
```

C'est tout : le module `spring-boot-docker-compose` détecte `compose.yaml`,
**démarre PostgreSQL dans Docker** et branche la datasource dessus. Flyway crée
ensuite le schéma. L'API écoute sur `http://localhost:8080`.

Pour vérifier :

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP",...}
```

Documentation interactive : <http://localhost:8080/swagger-ui.html>

### Autres commandes utiles

| Commande | Effet |
|---|---|
| `./mvnw test` | joue les 34 tests (démarre un Postgres jetable) |
| `./mvnw spring-boot:test-run` | lance l'app sur une base jetable Testcontainers, sans `compose.yaml` |
| `./mvnw package` | produit `target/backend-0.0.1-SNAPSHOT.jar` |
| `docker compose down -v` | supprime la base locale et ses données |

## 3. Structure du projet

Le code est organisé **par domaine fonctionnel**, pas par type technique. On ne
trouvera donc pas de dossier `controllers/` global : tout ce qui concerne les
séances vit dans `workout/`. C'est ce qui permet de comprendre — ou de
supprimer — une fonctionnalité sans parcourir tout le projet.

```text
src/main/java/com/pulsetrack/backend/
├── BackendApplication.java        Point d'entrée (méthode main)
│
├── config/                        Configuration technique transverse
│   ├── SecurityConfig.java        Chaîne de filtres, JWT, CORS, mots de passe
│   ├── SecurityProperties.java    Config typée et validée au démarrage
│   └── OpenApiConfig.java         Description Swagger
│
├── common/                        Briques partagées entre domaines
│   ├── domain/SportType.java      Vocabulaire commun (course, vélo, marche)
│   ├── error/                     Exceptions métier + traduction en HTTP
│   └── security/AuthenticatedUser Extraction de l'utilisateur courant du jeton
│
├── user/                          Domaine « compte »
│   ├── AuthController.java        HTTP : /api/v1/auth/**
│   ├── AuthService.java           Logique : inscription, connexion
│   ├── TokenService.java          Émission des JWT
│   ├── User.java                  Entité JPA (table users)
│   ├── UserRepository.java        Accès base
│   └── dto/                       Objets d'entrée/sortie de l'API
│
├── profile/                       Domaine « profil sportif »
│   └── (même découpage : Controller, Service, Entity, Repository, dto/)
│
└── workout/                       Domaine « séances »
    ├── WorkoutController.java
    ├── WorkoutService.java
    ├── WorkoutMetricsCalculator   Le cœur métier : distance, allure, calories
    ├── WorkoutSession.java        Entité JPA (table workout_sessions)
    ├── GpsPoint.java              Entité JPA (table gps_points)
    └── dto/

src/main/resources/
├── application.yml                Config par défaut (développement)
├── application-prod.yml           Surcharges production (secrets par variables d'env)
└── db/migration/V1__init.sql      Schéma de base, versionné
```

## 4. Comment une requête traverse l'application

C'est le schéma le plus important à avoir en tête. Prenons
`POST /api/v1/workouts` :

```text
   Requête HTTP + en-tête Authorization: Bearer eyJ...
              │
              ▼
   ┌──────────────────────────┐
   │  SecurityFilterChain     │  Valide la signature du JWT, son expiration et
   │  (SecurityConfig)        │  son émetteur. Sans jeton valide → 401, la
   └──────────┬───────────────┘  requête n'atteint jamais le controller.
              ▼
   ┌──────────────────────────┐
   │  WorkoutController       │  Traduit HTTP ↔ domaine. @Valid déclenche la
   │                          │  validation du corps → 400 si un champ cloche.
   └──────────┬───────────────┘  Aucune logique métier ici.
              ▼
   ┌──────────────────────────┐
   │  WorkoutService          │  Ouvre la transaction (@Transactional), applique
   │   ├─ ProfileService      │  les règles métier, appelle le calculateur,
   │   └─ MetricsCalculator   │  transforme entités ↔ DTO.
   └──────────┬───────────────┘
              ▼
   ┌──────────────────────────┐
   │  WorkoutSessionRepository│  Interface Spring Data : le SQL est généré.
   └──────────┬───────────────┘
              ▼
        PostgreSQL

   En cas d'exception métier, ApiExceptionHandler la traduit en réponse
   ProblemDetail (RFC 9457) avant qu'elle ne remonte au client.
```

Trois règles qui découlent de ce schéma :

1. **Le controller ne fait rien d'autre que traduire.** Pas de `if` métier, pas
   de requête base.
2. **Le service porte la transaction.** C'est lui qui décide ce qui est atomique.
3. **Les entités JPA ne sortent jamais de l'API.** On expose des `record` (DTO).
   Sinon le contrat d'API serait collé au schéma de base, et une migration
   casserait l'application mobile.

## 5. Contrat d'API

Toutes les routes sont préfixées par `/api/v1`. Sauf mention contraire, elles
exigent l'en-tête `Authorization: Bearer <jeton>`.

| Méthode | Route | Auth | Réponse | Rôle |
|---|---|---|---|---|
| `POST` | `/auth/register` | non | `201` | Crée un compte, renvoie un jeton |
| `POST` | `/auth/login` | non | `200` | Connexion |
| `GET` | `/me/profile` | oui | `200` / `404` | Lit le profil sportif |
| `PUT` | `/me/profile` | oui | `200` | Crée ou remplace le profil |
| `POST` | `/workouts` | oui | `201` | Enregistre une séance |
| `GET` | `/workouts` | oui | `200` | Historique paginé |
| `GET` | `/workouts/{id}` | oui | `200` / `404` | Détail + trace GPS |
| `DELETE` | `/workouts/{id}` | oui | `204` / `404` | Supprime la séance |
| `PUT` | `/me/body-checkins` | oui | `200` | Enregistre le relevé physique d'un jour |
| `GET` | `/me/body-checkins` | oui | `200` | Historique paginé des relevés |
| `GET` | `/me/body-checkins/progress` | oui | `200` | Courbe complète + tendance + IMC |
| `DELETE` | `/me/body-checkins/{id}` | oui | `204` / `404` | Supprime un relevé |
| `GET` | `/me/goals` | oui | `200` | Objectifs (`?activeOnly=false` pour l'historique) |
| `POST` | `/me/goals` | oui | `201` / `409` | Fixe un objectif |
| `PUT` | `/me/goals/{id}` | oui | `200` | Modifie la cible ou les dates |
| `POST` | `/me/goals/{id}/archive` | oui | `200` | Archive sans effacer |
| `DELETE` | `/me/goals/{id}` | oui | `204` / `404` | Supprime un objectif |
| `GET` | `/me/weekly-summary` | oui | `200` | Bilan de la semaine pour le dashboard |
| `GET` | `/me/coach/settings` | oui | `200` | Réglages de l'assistant |
| `PUT` | `/me/coach/settings` | oui | `200` | Ton, bilan hebdo, alertes |
| `PUT` | `/me/coach/settings/api-key` | oui | `200` | Dépose la clé Gemini (chiffrée) |
| `DELETE` | `/me/coach/settings/api-key` | oui | `200` | Supprime la clé et désactive |
| `POST` | `/me/coach/weekly-review` | oui | `200` | Bilan IA (`?refresh=true` pour régénérer) |
| `POST` | `/me/coach/ask` | oui | `200` | Question libre au coach |
| `GET` | `/me/coach/latest` | oui | `200` / `204` | Dernier conseil, sans appeler Gemini |
| `PUT` | `/me/device-tokens` | oui | `204` | Enregistre l'appareil pour les notifications |
| `DELETE` | `/me/device-tokens/{token}` | oui | `204` / `404` | Retire l'appareil |
| `GET` | `/actuator/health` | non | `200` | Sonde de santé |

### Trois points de contrat à connaître

**Le relevé physique est un `PUT`, pas un `POST`.** L'opération est identifiée par
la date : la rejouer corrige la valeur du jour au lieu de dédoubler la courbe. Un
mobile qui réessaie après une coupure réseau ne casse rien.

**Enregistrer un relevé met à jour le poids du profil**, qui sert au calcul des
calories. Sans cela, quelqu'un qui se pèse chaque semaine mais ne rouvre jamais
son profil verrait ses calories calculées à vie avec le poids de l'inscription.
Le report se fait toujours depuis le relevé le plus récent : rattraper un oubli de
la semaine passée n'écrase pas le poids d'aujourd'hui.

**Le bilan hebdomadaire attend un fuseau.** `GET /me/weekly-summary?zone=Africa/Ouagadougou`
— sans cette information, le serveur ne sait pas où commencent les journées de
l'utilisateur, et une course de 00h30 tomberait le mauvais jour. Par défaut UTC.
Le paramètre `weekStart` accepte n'importe quel jour de la semaine voulue et le
ramène à son lundi (ISO 8601).

### Parcours complet en curl

```bash
# 1. Créer un compte — récupérer accessToken dans la réponse
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"nico@exemple.com","password":"motdepasse123"}'

TOKEN="coller-le-accessToken-ici"

# 2. Renseigner le profil (obligatoire : le poids sert au calcul des calories)
curl -X PUT http://localhost:8080/api/v1/me/profile \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
        "displayName": "Nicolas",
        "heightCm": 178,
        "currentWeightKg": 72.5,
        "birthDate": "1995-04-12",
        "sex": "MALE",
        "primaryGoal": "IMPROVE_ENDURANCE",
        "fitnessLevel": "INTERMEDIATE",
        "preferredSports": ["RUN", "WALK"]
      }'

# 3. Enregistrer une séance : 1 km en 6 minutes
curl -X POST http://localhost:8080/api/v1/workouts \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
        "sportType": "RUN",
        "startedAt": "2026-08-10T06:00:00Z",
        "endedAt":   "2026-08-10T06:06:00Z",
        "perceivedEffort": 6,
        "feeling": "GOOD",
        "gpsPoints": [
          {"latitude": 48.8566,   "longitude": 2.3522, "altitude": 35.0,
           "recordedAt": "2026-08-10T06:00:00Z"},
          {"latitude": 48.8655931,"longitude": 2.3522, "altitude": 35.0,
           "recordedAt": "2026-08-10T06:06:00Z"}
        ]
      }'

# 4. Relire l'historique, filtré et paginé
curl "http://localhost:8080/api/v1/workouts?sport=RUN&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

### Ce que le client n'envoie pas

Le mobile envoie ce qu'il a **observé** : le sport, la fenêtre de temps, le
trace GPS, le ressenti. Distance, allure, vitesses, dénivelé et calories sont
**calculés par le serveur** et refusés en entrée. Deux raisons : tous les
clients affichent les mêmes chiffres, et corriger une formule ne demande pas de
publier une nouvelle version sur les stores.

Séance sans GPS (tapis, salle) : envoyer `distanceMeters` à la place du trace.

### Format des erreurs

Toutes les erreurs suivent la RFC 9457 (`application/problem+json`) :

```json
{
  "type": "https://pulsetrack.app/problems/validation",
  "title": "Requête invalide",
  "status": 400,
  "detail": "Un ou plusieurs champs sont invalides.",
  "instance": "/api/v1/me/profile",
  "errors": { "heightCm": "doit être supérieur ou égal à 80" }
}
```

| Code | Quand |
|---|---|
| `400` | Corps mal formé ou champ invalide (le détail est dans `errors`) |
| `401` | Jeton absent, invalide ou expiré ; identifiants faux |
| `404` | Ressource inexistante **ou appartenant à un autre compte** |
| `409` | Conflit (email déjà pris) |
| `422` | Règle métier violée (séance qui finit avant de commencer) |

## 6. Sécurité

Le modèle est **stateless** : aucune session serveur, le jeton porte l'identité.

1. `POST /auth/login` vérifie le mot de passe (bcrypt) et émet un JWT signé en
   HS256, dont le `subject` est l'identifiant du compte.
2. Chaque requête suivante porte ce jeton. La validation (signature, expiration,
   émetteur) est faite par le **OAuth2 Resource Server** de Spring Security, et
   non par un filtre maison — c'est là que se logent la plupart des failles JWT.
3. Les controllers lisent l'utilisateur courant via `AuthenticatedUser.idOf(jwt)`.
   **L'identité ne vient jamais du corps ni de l'URL de la requête.**

Trois choix à connaître :

- **Fermé par défaut** : `anyRequest().authenticated()`. On ouvre explicitement
  `/auth/**`, `/actuator/health` et la doc.
- **404 plutôt que 403** sur la ressource d'autrui : répondre « interdit »
  confirmerait son existence et permettrait d'énumérer les identifiants.
- **Chaque requête base filtre sur `userId`.** L'isolation entre comptes est
  garantie par la requête elle-même, pas par une vérification qu'on pourrait
  oublier d'écrire.

### Le secret JWT

En développement, `application.yml` contient un secret bidon, suffisant pour
travailler. **En production il n'y a aucune valeur par défaut** : si
`PULSETRACK_JWT_SECRET` est absent, l'application refuse de démarrer. Un
démarrage qui échoue vaut mieux qu'une API qui signe ses jetons avec un secret
public.

## 6 bis. L'assistant Gemini

La clé API est **celle de l'utilisateur**, pas celle du serveur : son quota, sa
facture, sa décision. Trois règles en découlent.

**Elle est chiffrée au repos** (AES-256-GCM, clé dérivée par PBKDF2 du secret de
configuration). Contrairement à un mot de passe, on doit pouvoir la relire pour
appeler le service : on la chiffre, on ne la hache pas. Le secret vivant hors de
la base, une copie de la base seule ne livre aucune clé exploitable.

**Elle n'est jamais renvoyée**, pas même tronquée. La réponse dit seulement
`apiKeyStored: true`. Ce qu'une API ne renvoie pas ne peut pas fuiter — un test
le vérifie explicitement.

**Le bilan hebdomadaire est mis en cache.** Un bilan déjà produit pour la semaine
est relu sans rappeler Gemini : ouvrir son dashboard cinq fois ne doit pas être
facturé cinq fois. `?refresh=true` force une nouvelle génération.

Les garde-fous de la spec produit — pas de diagnostic médical, pas d'effort
disproportionné, invitation à consulter un professionnel en cas de douleur,
liberté de refuser le conseil — sont écrits une seule fois, dans
`CoachPromptBuilder.systemInstruction`, et **vérifiés par des tests**. Une
réécriture du prompt qui les ferait disparaître casse la build.

L'application reste utilisable sans Gemini : aucune autre fonctionnalité ne
dépend de ce module, et sans clé les endpoints du coach répondent 422 avec un
message actionnable.

| Erreur remontée | Code | Cause |
|---|---|---|
| Clé refusée par Google | `422` | Clé erronée ou révoquée |
| Quota épuisé | `429` | Limite de la clé atteinte |
| Gemini injoignable | `502` | Timeout ou panne côté Google |
| Assistant non configuré | `422` | Aucune clé enregistrée |

## 6 ter. Notifications push (FCM)

**Désactivé par défaut.** Sans projet Firebase, l'application démarre normalement
et `LoggingPushSender` journalise les envois au lieu de les faire — toute la
chaîne de rappels se développe et se teste sans compte Google.

Pour activer :

```yaml
pulsetrack:
  push:
    fcm:
      enabled: true
      project-id: mon-projet-firebase
      credentials-location: file:/chemin/service-account.json
```

Le compte de service se télécharge depuis la console Firebase. S'il est illisible,
**le démarrage échoue** : découvrir le problème dimanche 19h, au moment où le
rappel devait partir, serait bien pire.

Deux rappels sont planifiés :

| Rappel | Quand | Condition |
|---|---|---|
| Pesée hebdomadaire | dimanche 19h | dernière pesée il y a plus de 6 jours |
| Objectif en retard | dimanche 10h | un objectif sous 60 % et non atteint |

L'alerte d'effort est calculée **par le backend, sans appeler Gemini** : une
notification ne doit dépendre ni d'une clé tierce ni d'un quota.

> **Limite structurelle à connaître.** Ces rappels ne partent que si le backend
> tourne à l'heure prévue. Lancé sur un poste de travail éteint le soir, le
> rappel du dimanche 19h n'existera pas. Tenir cette promesse demande un serveur
> allumé en permanence. `pulsetrack.reminders.enabled=false` coupe proprement
> toute la mécanique.

L'endpoint d'enregistrement est un `PUT` idempotent : l'application l'appelle à
chaque démarrage et à chaque renouvellement de jeton, sans créer de doublon. Un
jeton déjà connu pour un autre compte **change de propriétaire** — c'est le cas
du téléphone reconnecté avec un compte différent, et sans cela l'ancien compte
continuerait de recevoir les notifications du nouveau.

## 7. Base de données et migrations

Le schéma est du **code versionné**, dans `src/main/resources/db/migration/`.
Flyway applique au démarrage les scripts qui manquent, et note ce qu'il a fait
dans la table `flyway_schema_history`.

Deux règles :

- **Ne jamais modifier une migration déjà appliquée.** Flyway en garde une
  empreinte et refusera de démarrer. Pour changer quelque chose : ajouter
  `V2__ma_modification.sql`.
- **`ddl-auto: validate`**, jamais `update`. Hibernate compare les entités au
  schéma réel et fait échouer le démarrage en cas d'écart. C'est ce filet qui
  attrape l'oubli d'une migration — et le test `BackendApplicationTests` le
  déclenche à chaque build.

Se connecter à la base locale :

```bash
docker exec -it pulsetrack-postgres psql -U pulsetrack -d pulsetrack
```

## 8. Les tests

119 tests, sur trois niveaux :

| Niveau | Exemple | Ce qu'il valide | Durée |
|---|---|---|---|
| **Unitaire** | `WorkoutMetricsCalculatorTest`, `GoalProgressCalculatorTest`, `BodyProgressCalculatorTest`, `ActivityStreakCalculatorTest`, `ReminderDeciderTest`, `CoachPromptBuilderTest` | La logique métier, sans Spring | ~0,1 s |
| **Démarrage** | `BackendApplicationTests` | Câblage, config, migrations vs entités | ~20 s |
| **Intégration** | `*ApiIntegrationTest` | Sécurité, JSON, transactions, vraie base | ~2 s chacun |

Les six calculateurs sont des classes **sans état ni dépendance** : chacun
s'instancie avec `new` dans son test. C'est ce qui permet de couvrir les cas
limites (série vide, division par zéro, poids qui s'éloigne de la cible, rappel
au sixième jour) en quelques millisecondes, sans démarrer Spring ni Docker.

Deux choix de test à connaître :

- **`GeminiClient` est remplacé par un mock** (`@MockitoBean`) dans
  `CoachApiIntegrationTest`. On valide notre logique — chiffrement, cache,
  traduction des erreurs — sans appeler l'API de Google, ce qui rendrait la suite
  lente, payante et dépendante du réseau.
- **Les rappels planifiés sont coupés en test**
  (`pulsetrack.reminders.enabled=false`). Un job qui se déclenche au milieu d'une
  suite la rendrait non reproductible ; sa logique est testée séparément dans
  `ReminderDeciderTest`.

Les tests d'intégration tournent sur un **vrai PostgreSQL** démarré par
Testcontainers, pas sur H2 : une base en mémoire accepte du SQL que Postgres
refuse, et laisserait passer des migrations cassées jusqu'en production.

Le contexte Spring est mis en cache entre les classes de test : le conteneur ne
démarre qu'une fois pour toute la suite.

```bash
./mvnw test                                   # tout
./mvnw test -Dtest=WorkoutMetricsCalculatorTest   # une classe (rapide, sans Docker)
```

## 9. Configuration et profils

| Fichier | Usage |
|---|---|
| `application.yml` | Valeurs par défaut, développement |
| `application-prod.yml` | Production ; tout ce qui est sensible vient de l'environnement |

Activer la production : `SPRING_PROFILES_ACTIVE=prod`, avec les variables
`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`,
`PULSETRACK_JWT_SECRET`, `PULSETRACK_CORS_ORIGINS`,
`PULSETRACK_ENCRYPTION_PASSWORD`, `PULSETRACK_ENCRYPTION_SALT` (hexadécimal), et
si besoin `PULSETRACK_FCM_ENABLED`, `PULSETRACK_FCM_PROJECT_ID`,
`PULSETRACK_FCM_CREDENTIALS`.

> Changer le couple de chiffrement rend **illisibles les clés Gemini déjà
> stockées** : les utilisateurs devront les ressaisir. Ce n'est pas une perte de
> données grave, mais ce n'est pas transparent non plus.

La configuration de sécurité est typée dans `SecurityProperties` et **validée au
démarrage** : un secret trop court fait échouer le lancement immédiatement,
plutôt que de produire des jetons faibles.

## 10. Ajouter une fonctionnalité

La recette, dans l'ordre — par exemple pour le check-in hebdomadaire
(`BodyCheckIn`) de la spec :

1. **Migration** : `V2__create_body_checkins.sql`.
2. **Package** `bodycheckin/` avec l'entité, le repository, le service, le
   controller et ses DTO.
3. **DTO en `record`** avec les contraintes de validation.
4. **Filtrer sur `userId`** dans chaque méthode du repository.
5. **Test unitaire** si la feature contient un calcul ; **test d'intégration**
   pour le contrat HTTP, sans oublier le cas « ressource d'un autre compte ».
6. **Javadoc** sur le service, et commentaires sur les choix non évidents.
7. `./mvnw test` — le vert inclut la vérification schéma ↔ entités.

## 11. Ce qui reste à faire

Les phases 1 à 3 de la roadmap produit sont couvertes : comptes, profils,
séances, évolution physique, objectifs, agrégats du dashboard, assistant Gemini
et notifications push. Il reste :

- **Objectif de performance** (`PERFORMANCE` dans la spec : meilleur temps sur une
  distance). Volontairement absent de `GoalType` plutôt que livré à moitié : sa
  progression demande de retrouver le meilleur temps sur une distance donnée, ce
  qui n'est pas un simple cumul hebdomadaire
- **Export et suppression** des données personnelles
- **Refresh tokens** — le jeton dure 24 h et n'est pas renouvelable
- **Ménage des jetons d'appareil** — `lastSeenAt` est renseigné mais aucun
  traitement ne purge encore les appareils muets depuis des mois
- **Suggestion de semaine d'entraînement** — la spec la mentionne ; aujourd'hui
  le coach répond au coup par coup, il ne construit pas de plan structuré
