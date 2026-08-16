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
├── workout/                       Domaine « séances »
│   ├── WorkoutController.java
│   ├── WorkoutService.java
│   ├── WorkoutMetricsCalculator   Le cœur métier : distance, allure, calories
│   ├── WorkoutSession.java        Entité JPA (table workout_sessions)
│   ├── GpsPoint.java              Entité JPA (table gps_points)
│   └── dto/
│
├── motivation/                    Vocabulaire partagé de l'encouragement
│   ├── Appreciation.java          Verdict + message + un conseil, prêts à afficher
│   ├── AppreciationTier.java      EXCELLENT → AT_RISK, jamais accusateur
│   └── Wording.java               Mise en français des grandeurs (6,3 km, 5:30/km)
│
├── achievement/                   Records battus et trophées
│   ├── AchievementDetector.java   Qui décide qu'un record tombe, marges anti-bruit
│   ├── SportBests.java            Records COURANTS, recalculés à la lecture
│   └── WorkoutAchievement.java    Un record TOMBÉ tel jour : un fait, il ne change plus
│
├── route/                         Parcours rejouables
│   ├── TrackSimplifier.java       Douglas-Peucker : dessiner, jamais mesurer
│   ├── SavedRoute.java            Le circuit nommé, il survit à sa séance d'origine
│   └── RouteService.java          Classement des passages, comparaison à l'arrivée
│
├── challenge/                     Défis chronométrés
│   ├── ChallengePlanner.java      Le plan joué hors ligne : jalons et alertes
│   ├── ChallengeEvaluator.java    Suivi en cours d'effort, puis verdict
│   └── DifficultyAssessor.java    L'avis rendu AVANT l'effort : vises-tu juste ?
│
└── rating/                        Note de l'utilisateur
    ├── RatingCalculator.java      Régularité, volume, objectifs, progression
    └── RatingTier.java            NEW → ATHLETE ; un compte neuf n'a pas zéro

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
| `POST` | `/auth/login` | non | `200` / `403` | Connexion ; `403` si l'adresse doit être confirmée |
| `POST` | `/auth/forgot-password` | non | `204` | Demande un code de réinitialisation |
| `POST` | `/auth/reset-password` | non | `204` / `400` | Choisit un nouveau mot de passe avec le code |
| `POST` | `/auth/verify-email` | non | `204` / `400` | Confirme l'adresse avec le code reçu |
| `POST` | `/auth/resend-verification` | non | `204` | Renvoie un code de confirmation |
| `POST` | `/me/password` | oui | `200` / `422` | Change le mot de passe et rend une session neuve |
| `DELETE` | `/me` | oui | `204` / `422` | Supprime définitivement le compte |
| `GET` | `/me/profile` | oui | `200` / `404` | Lit le profil sportif |
| `PUT` | `/me/profile` | oui | `200` | Crée ou **remplace** le profil (efface ce qui manque) |
| `PATCH` | `/me/profile` | oui | `200` / `422` | Modifie les seuls champs fournis |
| `GET` | `/client/requirements` | **non** | `200` | Version minimale d'application acceptée |
| `GET` | `/billing/plans` | oui | `200` | Catalogue des offres (« à venir ») |
| `GET` | `/me/subscription` | oui | `200` | Droit d'usage du compte courant |
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
| `GET` | `/workouts/records` | oui | `200` | Records courants, sport par sport (`?sport=RUN`) |
| `GET` | `/me/weekly-summary` | oui | `200` | Bilan de la semaine, jour par jour, avec appréciation |
| `POST` | `/me/routes` | oui | `201` / `422` | Enregistre un parcours à partir d'une séance tracée |
| `GET` | `/me/routes` | oui | `200` | Parcours enregistrés, paginés, sans le tracé |
| `GET` | `/me/routes/{id}` | oui | `200` / `404` | Détail d'un parcours, tracé compris |
| `PUT` | `/me/routes/{id}` | oui | `200` / `409` | Renomme un parcours |
| `DELETE` | `/me/routes/{id}` | oui | `204` / `404` | Supprime le parcours, pas les séances |
| `GET` | `/me/routes/{id}/attempts` | oui | `200` | Classement des passages sur le circuit |
| `POST` | `/me/challenges` | oui | `201` / `422` | Se fixe un défi : telle distance en tel temps |
| `GET` | `/me/challenges` | oui | `200` | Défis paginés (`?status=DRAFT,ACTIVE`) |
| `GET` | `/me/challenges/{id}` | oui | `200` / `404` | Détail d'un défi |
| `POST` | `/me/challenges/{id}/start` | oui | `200` / `409` | Arme le chronomètre et rend le plan |
| `POST` | `/me/challenges/{id}/progress` | oui | `200` | Point d'étape ; n'écrit rien |
| `POST` | `/me/challenges/{id}/complete` | oui | `200` / `409` | Règle le défi et rend le verdict |
| `POST` | `/me/challenges/{id}/abandon` | oui | `200` / `409` | Renonce et libère la place |
| `DELETE` | `/me/challenges/{id}` | oui | `204` / `404` | Supprime le défi |
| `GET` | `/me/rating` | oui | `200` | Note de l'utilisateur sur 28 jours et encouragement |
| `GET` | `/me/coach/settings` | oui | `200` | Réglages de l'assistant |
| `PUT` | `/me/coach/settings` | oui | `200` | Ton, bilan hebdo, alertes |
| `PUT` | `/me/coach/settings/api-key` | oui | `200` | Dépose la clé Gemini (chiffrée) |
| `DELETE` | `/me/coach/settings/api-key` | oui | `200` | Supprime la clé et désactive |
| `POST` | `/me/coach/weekly-review` | oui | `200` | Bilan IA (`?refresh=true` pour régénérer) |
| `POST` | `/me/coach/ask` | oui | `200` | Question libre au coach |
| `GET` | `/me/coach/latest` | oui | `200` / `204` | Dernier conseil, sans appeler Gemini |
| `PUT` | `/me/device-tokens` | oui | `204` | Enregistre l'appareil pour les notifications |
| `DELETE` | `/me/device-tokens/{token}` | oui | `204` / `404` | Retire l'appareil |
| `PUT` | `/admin/users/{id}/subscription` | admin | `200` | Accorde, retire ou constate un droit d'usage |
| `POST` | `/admin/workouts/recompute-metrics` | admin | `200` | Rejoue le calcul des métriques sur l'historique |
| `GET` | `/actuator/health` | non | `200` | Sonde de santé |

### Cinq points de contrat à connaître

**Le relevé physique est un `PUT`, pas un `POST`.** L'opération est identifiée par
la date : la rejouer corrige la valeur du jour au lieu de dédoubler la courbe. Un
mobile qui réessaie après une coupure réseau ne casse rien.

**Enregistrer un relevé met à jour le poids du profil**, qui sert au calcul des
calories. Sans cela, quelqu'un qui se pèse chaque semaine mais ne rouvre jamais
son profil verrait ses calories calculées à vie avec le poids de l'inscription.
Le report se fait toujours depuis le relevé le plus récent : rattraper un oubli de
la semaine passée n'écrase pas le poids d'aujourd'hui.

**Les métriques d'une séance ne sont pas lues sur les positions brutes.** Distance,
temps en mouvement et pic de vitesse viennent d'un filtre de Kalman
(`TrackFilter`) qui fusionne les positions et la vitesse Doppler du capteur, puis
intègre la vitesse estimée. Additionner les distances entre points bruts
surestime le parcours d'autant plus que les points sont rapprochés — mesuré à
+88 % sur une marche simulée échantillonnée toutes les trois secondes avec quatre
mètres de précision. Le filtre est validé sur des trajets simulés à distance
connue (`TrackFilterTest`) : marche, course, parcours courbe, immobilité, point
aberrant, capteur muet.

Les métriques restent **figées à l'enregistrement**. Après toute correction de
formule, il faut donc réparer l'historique :
`POST /api/v1/admin/workouts/recompute-metrics`, qui rejoue le calculateur sur
chaque séance et ne réécrit que ce qui change. Ne jamais refaire ce recalcul en
SQL dans une migration : il faudrait y réécrire tout le calculateur dans un
second langage, sans garantie qu'il dise la même chose.

**La confirmation d'adresse n'est pas exigée par défaut.** Une inscription envoie
un code par courriel et la réponse porte `emailVerified: false`, mais le compte
fonctionne normalement. Passer
`pulsetrack.security.email-verification.required` (variable
`PULSETRACK_EMAIL_VERIFICATION_REQUIRED`) à `true` change la règle : toute
connexion — et tout renouvellement de session — d'un compte non confirmé répond
alors `403` avec le type `email-not-verified`. Les comptes créés avant la
migration V8 sont tenus pour confirmés ; ne l'activer qu'une fois l'écran de
saisie du code livré côté mobile, sinon un nouvel inscrit se retrouve devant une
porte close.

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

## 6 quater. Motivation : records, défis, parcours et note

Quatre fonctionnalités partagent une même règle, et c'est elle qu'il faut retenir
avant de toucher au code : **rien de tout cela ne passe par l'assistant.** Une
félicitation ne doit dépendre d'aucune clé tierce, ne rien coûter, et surtout ne
pas faire patienter deux secondes quelqu'un qui vient de franchir sa ligne
d'arrivée. Tous les textes rendus sont composés dans `motivation/Wording.java` et
les classes de messages de chaque domaine, en français et sans accents — la
convention de tout ce que ce serveur publie déjà.

**Records courants et trophées ne sont pas la même chose**, et c'est la
distinction structurante du paquet `achievement/` :

- le **record courant** (`SportBests`) se recalcule à chaque lecture. Une séance
  supprimée doit faire disparaître le record qu'elle détenait, sinon l'écran
  affiche indéfiniment un chiffre que plus rien ne justifie ;
- le **trophée** (`WorkoutAchievement`) enregistre qu'un record est tombé tel
  jour. C'est un fait, il ne change plus. Le conserver sert à deux choses très
  concrètes : le renvoi d'une séance déjà enregistrée, après une coupure réseau
  en fin de course, rend exactement la même liste — les félicitations n'explosent
  pas deux fois et ne se perdent pas ; et l'historique peut badger les séances
  remarquables sans rejouer la chronologie de tous les records.

**Les marges anti-bruit ne sont pas décoratives.** Le GPS tremble. Sans seuil, une
sortie identique à la précédente au mètre près ferait tomber un record une fois
sur deux, et les confettis ne voudraient plus rien dire au bout d'une semaine.
Chaque catégorie exige donc de dépasser le précédent d'une quantité absolue *et*
d'une proportion — la première protège les petites valeurs, la seconde les
grandes. L'allure n'est jamais évaluée sous un kilomètre. Tout est dans
`AchievementKind`, et chaque seuil a son test.

**Le mode défi ne parle pas au serveur pendant l'effort.** C'est la décision de
conception la plus importante du paquet `challenge/`. Les alertes à l'approche de
l'échéance sont ce qui compte le plus dans un défi, et ce sont elles qui
tomberaient en premier : le réseau est mauvais quand on bouge, et une alerte qui
attend une réponse HTTP arrive après l'échéance qu'elle annonce. `ChallengePlanner`
remet donc tout d'avance — jalons, seuils et messages — et le téléphone les joue
seul, même en mode avion. La route `/progress` existe pour un écran de suivi, mais
elle n'écrit rien et personne n'est obligé de l'appeler.

**Les tolérances du verdict sont dissymétriques, à dessein** : un pour cent de
marge sur la distance parce que le GPS ne rend pas 10 000,0 mètres et qu'un défi
refusé pour huit mètres serait vécu comme une injustice ; aucune marge sur le
temps, parce qu'une échéance qui pardonne n'est plus une échéance.

**Un parcours se dessine, il ne se mesure pas.** `TrackSimplifier` réduit la trace
par Douglas-Peucker à cinq mètres de tolérance, pour l'afficher sur une carte. La
distance d'un parcours reste celle de la séance d'origine, estimée par le filtre
de Kalman ; la recalculer sur ces points ramènerait exactement la surestimation
que le filtre corrige. La distance cumulée le long du tracé est *répartie*
proportionnellement de façon que le dernier point vaille la distance officielle.

Le rattachement d'une séance à un parcours est **déclaratif** : le serveur ne
vérifie pas que la trace suit le circuit. Comparer deux traces bruitées demande un
appariement point à point qui coûte cher et se trompe.

**Aucun verdict n'accuse.** Le pire niveau d'appréciation, `AT_RISK`, constate
qu'un objectif ne sera pas tenu sans effort net ; il ne reproche rien. Un compte
sans aucune séance ne reçoit pas la note zéro mais un accueil (`RatingTier.NEW`,
`score` nul). Celui qui a le plus besoin d'encouragement est précisément celui qui
a le moins couru, et une application de sport qui gronde se désinstalle.

**Le pourcentage d'un objectif hebdomadaire ne dit rien tout seul.** Quarante pour
cent, c'est de l'avance le mardi et du retard le samedi. C'est pour cela que
`GoalProgressCalculator` reçoit la fraction de semaine écoulée et rend
`elapsedPercent`, `onTrack` et une projection : aucun client ne peut faire cette
comparaison seul sans connaître le fuseau de l'utilisateur.

Le contrat complet destiné à l'application mobile est dans
`../CONTRAT-MOTIVATION.md`, à la racine du dépôt parent.

## 6 quinquies. Deux verrous, et l'ordre dans lequel on les arme

Le jour où l'application deviendra payante, deux dispositifs devront agir
ensemble. Ils sont écrits, testés, et **tous deux éteints** : l'API répond
aujourd'hui exactement comme avant.

**Le verrou de version** (`client/`) refuse les applications trop anciennes en
`426`. Son mécanisme repose sur une absence, et c'est ce qu'il faut comprendre
avant de le juger : les APK déjà distribués n'envoient aucun en-tête de version.
Une requête sans `X-GymFlow-Client-Version` est donc, par construction, une
requête d'un client antérieur au dispositif. Il n'y a rien à rétro-porter dans
les applications déjà installées — c'est précisément ce qui rend le verrou
étanche. Le refus s'applique aussi à `/auth/**` : une application périmée ne doit
même pas pouvoir créer un compte, sinon le contournement est trivial.

**Le verrou de paiement** (`billing/`) refuse les routes payantes en `402`, avec
l'offre à afficher dans le corps du refus — relancer une requête pour aller
chercher un prix au moment où tout est refusé serait absurde.

**L'ordre d'activation n'est pas négociable** :
`pulsetrack.client.enforced` **puis** `pulsetrack.billing.enforced`. L'inverse
n'aurait aucun effet : il suffirait de garder une vieille application pour
continuer gratuitement, et le paiement ne s'appliquerait qu'aux nouveaux venus.

**L'essai ne se stocke pas, il se calcule** depuis `users.created_at`. Ce choix
évite d'écrire une ligne à chaque inscription, et surtout d'avoir à en fabriquer
rétroactivement pour tout le parc le jour de la mise en vente. Conséquence
assumée : les comptes déjà anciens seront `EXPIRED` dès l'activation. Une ligne
dans `subscriptions` n'apparaît que lorsqu'un droit est accordé ou retiré à la
main.

**Ce qui reste gratuit quand le paiement est exigé** est une liste explicite, pas
un joker : un endpoint ajouté demain naît payant, ce qui est le bon défaut —
l'oubli inverse, une route sensible restée gratuite, ne se verrait jamais. Y
figurent l'authentification, le profil, les tarifs, l'état de l'abonnement, le
changement de mot de passe, la suppression de compte et **l'export des données**.
Retenir les données de quelqu'un parce qu'il a cessé de payer serait
indéfendable. Un administrateur n'est jamais bloqué non plus, même immunité que
pour les modules.

Les prix vivent en configuration et non en dur, pour se corriger sans recompiler.
**Ce sont des valeurs d'attente, à relire avant toute publication.**

## 6 sexies. Pourquoi le profil a un `PATCH`

`PUT /me/profile` remplace le profil entier. C'est correct pour l'écran d'accueil,
qui saisit tout d'un coup, et c'est le seul moyen de vider un champ facultatif.

Mais un écran qui ne corrige que le poids et rejoue un `PUT` incomplet **efface au
passage la date de naissance et le sexe**. Les champs obligatoires sont protégés
par la validation — un `PUT` amputé est refusé en `400` — donc ce sont exactement
les deux champs optionnels, ceux que l'utilisateur avait pris la peine de
renseigner en plus, qui disparaissaient sans aucun signal.

`PATCH` ne modifie que ce qui est présent. Un piège s'y cache, et il est gardé par
un test : `UserProfile.update` vide la collection des sports avant de la remplir.
Lui passer la collection du profil lui-même la viderait, puis la recopierait
depuis le vide — les sports pratiqués disparaîtraient à chaque modification
partielle qui ne les mentionne pas. D'où la copie défensive dans `ProfileService`.

## 6 septies. Un refus d'authentification a désormais un corps

Par défaut, un jeton expiré produit un `401` au corps vide, la cause n'étant que
dans l'en-tête `WWW-Authenticate`. Le client ne peut alors pas distinguer « ta
session a expiré » d'une panne réseau, et affiche « une erreur est survenue » dans
les deux cas — la pire réponse possible, puisque l'utilisateur n'a rien d'autre à
faire que se reconnecter.

`ApiAuthenticationEntryPoint` habille la réponse standard d'un corps RFC 9457,
sans la remplacer : l'en-tête exigé par la RFC 6750 reste posé. Trois types de
problème sont distingués — `token-expired`, `unauthenticated`, `access-denied` —
et c'est sur eux que le client route sa réaction, jamais sur le code HTTP.

Le détail reste volontairement pauvre pour les jetons illisibles : expliquer ce
qui cloche dans une signature aiderait à en fabriquer une. L'expiration, elle, ne
se cache pas — c'est une information que le porteur du jeton possède déjà.

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

478 tests, sur trois niveaux :

| Niveau | Exemple | Ce qu'il valide | Durée |
|---|---|---|---|
| **Unitaire** | `WorkoutMetricsCalculatorTest`, `GoalProgressCalculatorTest`, `AchievementDetectorTest`, `ChallengeEvaluatorTest`, `ChallengePlannerTest`, `DifficultyAssessorTest`, `RatingCalculatorTest`, `TrackSimplifierTest`, `WeeklyAppreciatorTest`, `ReminderDeciderTest` | La logique métier, sans Spring | ~0,1 s |
| **Démarrage** | `BackendApplicationTests` | Câblage, config, migrations vs entités | ~20 s |
| **Intégration** | `*ApiIntegrationTest` | Sécurité, JSON, transactions, vraie base | ~2 s chacun |

Tous les calculateurs sont des classes **sans état ni dépendance** : chacun
s'instancie avec `new` dans son test. C'est ce qui permet de couvrir les cas
limites (série vide, division par zéro, poids qui s'éloigne de la cible, rappel
au sixième jour, record noyé dans le bruit du GPS, défi manqué de dix secondes)
en quelques millisecondes, sans démarrer Spring ni Docker.

C'est aussi ce qui rend les **seuils** discutables et révisables. Les marges
anti-bruit d'un record, les niveaux d'alerte d'un défi, le barème d'une note :
tous sont des constantes nommées, documentées par un commentaire qui dit pourquoi
cette valeur-là, et éprouvées par un test qui échoue si on les bouge sans le
vouloir. Les changer est un geste délibéré, pas un effet de bord.

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
séances, évolution physique, objectifs, agrégats du dashboard, assistant Gemini,
notifications push, et depuis le 15 août 2026 les fonctions de motivation
(records, défis, parcours rejouables, note de l'utilisateur — section 6 quater).
Il reste :

- **Objectif de performance** (`PERFORMANCE` dans la spec : meilleur temps sur une
  distance). Toujours absent de `GoalType`, mais le besoin est désormais couvert
  autrement : un défi porte exactement cette promesse — telle distance en tel
  temps — et un parcours enregistré donne le meilleur temps sur un circuit. Le
  jour où l'on voudra en faire un objectif suivi semaine après semaine, c'est
  `SportBests` qu'il faudra interroger, pas un nouveau cumul hebdomadaire
- **Vérifier qu'une séance suit vraiment le parcours déclaré.** Le rattachement
  est déclaratif aujourd'hui. Un appariement point à point coûte cher et se
  trompe ; si le besoin apparaît, commencer par une comparaison grossière des
  boîtes englobantes plutôt que par un algorithme d'alignement
- **Ménage des jetons d'appareil** — `lastSeenAt` est renseigné mais aucun
  traitement ne purge encore les appareils muets depuis des mois
- **Suggestion de semaine d'entraînement** — la spec la mentionne ; aujourd'hui
  le coach répond au coup par coup, il ne construit pas de plan structuré
