# Image de production de l'API PulseTrack.
#
# Deux etapes : la premiere compile avec un JDK complet et le cache Maven, la
# seconde ne garde qu'un JRE et le jar. L'image finale ne contient donc ni
# compilateur, ni sources, ni depot Maven — moins de surface d'attaque et
# quelques centaines de megaoctets en moins a pousser a chaque deploiement.

# ---------------------------------------------------------------------------
# Etape 1 — compilation
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build

WORKDIR /build

# Le wrapper et le pom d'abord, seuls : tant que les dependances ne changent
# pas, Docker reutilise la couche de telechargement meme si le code a change.
# Copier tout le projet d'un bloc invaliderait ce cache a chaque commit.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
# Les tests ne tournent pas ici : ils demarrent un PostgreSQL via Testcontainers,
# donc un Docker dans le Docker. Ils sont joues par `./mvnw verify` avant de
# construire l'image, pas pendant.
RUN ./mvnw -B -ntp -DskipTests package

# ---------------------------------------------------------------------------
# Etape 2 — execution
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

# Un compte sans privileges : si l'application est compromise, l'attaquant
# n'est pas root dans le conteneur.
RUN addgroup -S pulsetrack && adduser -S -G pulsetrack pulsetrack

WORKDIR /app
COPY --from=build --chown=pulsetrack:pulsetrack /build/target/*.jar /app/app.jar

USER pulsetrack

EXPOSE 8080

# Empreinte memoire bornee a 640 Mo, la limite fixee au conteneur dans
# docker-compose.prod.yaml. Le detail du calcul :
#
#   tas               384 Mo  (-Xmx)
#   metaspace     <= 160 Mo  (un contexte Spring complet en occupe ~110)
#   memoire directe  32 Mo  (tampons reseau du client HTTP)
#   piles + code + structures de la JVM  ~60 Mo
#   ------------------------------------------
#   total au pic     ~636 Mo
#
# SerialGC plutot que G1 : sous ~500 Mo de tas, G1 reserve des structures de
# regions dont on n'a aucun besoin, et c'est autant de memoire perdue.
# ExitOnOutOfMemoryError fait tomber le conteneur au lieu de le laisser vivoter
# en echouant sur une requete sur deux : l'orchestrateur le redemarre.
ENV JAVA_OPTS="-Xmx384m -XX:MaxMetaspaceSize=160m -XX:MaxDirectMemorySize=32m -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

# La sonde de disponibilite d'Actuator, pas `/actuator/health` : elle repond 503
# tant que Flyway et le pool de connexions ne sont pas prets, ce qui evite
# qu'un proxy route du trafic vers une instance qui demarre encore.
# `wget` est fourni par busybox, aucun paquet a installer.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1

# `exec` pour que la JVM soit le PID 1 et recoive SIGTERM directement : sans
# lui, le shell l'intercepterait et l'arret se ferait par SIGKILL apres delai.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
