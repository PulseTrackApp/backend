# GymFlow Backend

API REST de GymFlow, une application de suivi sportif. Elle gère les comptes, les séances et leurs parcours GPS, les objectifs, les défis, les abonnements et les notifications. Les métriques (distance, allure, calories) sont calculées côté serveur à partir des points GPS.

*English: the Spring Boot REST API behind the GymFlow mobile app: accounts, workouts with GPS routes, goals, challenges, subscriptions, push notifications and an admin API.*

Projet personnel, conçu et développé de bout en bout. Le code est visible à titre de démonstration.

## Fonctionnalités

- **Comptes** : inscription, connexion, renouvellement de session, vérification de l'e-mail, réinitialisation du mot de passe, suspension d'un compte.
- **Séances** : enregistrement d'une séance avec ses points GPS, calcul de la distance, de l'allure, de la vitesse maximale et des calories.
- **Suivi** : évolution physique, objectifs, statistiques par période, bilan hebdomadaire, export des données.
- **Motivation** : records, défis, succès, parcours enregistrés, note de l'application.
- **Coach** : bilans et conseils générés par un assistant IA, avec une clé chiffrée au repos.
- **Abonnements** : offres, période d'essai, accès par module.
- **Notifications** : envoi aux appareils des utilisateurs (Firebase Cloud Messaging).
- **Administration** : API réservée aux administrateurs, pour gérer les comptes, les offres, les abonnements et envoyer des notifications.
- **Assistance** : formulaire d'aide, avec envoi par e-mail.

## Technologies

| Domaine | Choix |
|---|---|
| Langage et framework | Java et Spring Boot |
| Base de données | PostgreSQL, migrations versionnées avec Flyway |
| Authentification | jetons JWT sans état, Spring Security |
| Documentation | OpenAPI et Swagger UI |
| Tests | JUnit, Spring Test, Testcontainers (base PostgreSQL réelle) |
| Conteneurs | Docker et Docker Compose |

## Architecture

Le code est organisé par domaine métier. Chaque domaine regroupe son contrôleur, son service, ses entités et ses objets d'échange.

```
src/main/java/.../backend/
  user, profile        comptes et profils sportifs
  workout, route       séances et parcours GPS
  stats, summary       statistiques et bilans
  goal, challenge,
  achievement,
  motivation, rating   objectifs, défis, succès
  bodycheckin          évolution physique
  coach                assistant IA
  billing, access      abonnements et accès par module
  push, reminder       notifications
  admin, support       administration et assistance
  common, config       erreurs, sécurité, configuration
src/main/resources/db/migration/   migrations SQL numérotées
```

Choix de conception :
- les calculs métier sont faits sur le serveur, pour que toutes les applications clientes affichent les mêmes chiffres ;
- les erreurs suivent un format unique et lisible par la machine ;
- le schéma de la base ne change que par migration, jamais à la main ;
- les tests d'intégration tournent sur une vraie base PostgreSQL jetable, pas sur un simulacre.

## Lancer le projet

Prérequis : Java 17 ou plus et Docker. Maven n'est pas nécessaire, le projet embarque `mvnw`.

```bash
./mvnw spring-boot:run
```

Spring Boot démarre PostgreSQL dans Docker, Flyway crée le schéma, et l'API écoute sur `http://localhost:8080`.

```bash
curl http://localhost:8080/actuator/health
```

La documentation interactive de l'API est disponible sur `http://localhost:8080/swagger-ui.html`.

Sous PowerShell, remplacer `./mvnw` par `.\mvnw.cmd`.

## Tests

```bash
./mvnw test
```

Plus de 500 tests, dont des tests d'intégration sur PostgreSQL via Testcontainers (Docker doit être démarré).

## Configuration

Toute la configuration sensible passe par des variables d'environnement. Le fichier [.env.example](.env.example) liste celles qui sont attendues ; le vrai `.env` n'est jamais versionné. Les valeurs de développement incluses dans le dépôt sont des exemples et ne conviennent pas à un déploiement réel.

## Déploiement

Une image Docker est fournie (`Dockerfile`), ainsi qu'un `docker-compose.prod.yaml`. L'API tourne sur un service d'hébergement de conteneurs, avec une base PostgreSQL managée.

## Dépôts liés

- [GymFlow Mobile](https://github.com/PulseTrackApp/mobile) : application Flutter qui consomme cette API.

## Auteur

Ouettéré Nicolas MILLOGO, ingénieur logiciel.
nicolasmillogo3@gmail.com · [Portfolio](https://nicolas-millogo.vercel.app)

## Licence

Propriétaire, tous droits réservés. Voir [LICENSE](LICENSE).
