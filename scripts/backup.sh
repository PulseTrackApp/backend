#!/usr/bin/env bash
#
# Sauvegarde complete de la base PulseTrack.
#
# Une sauvegarde qui vit sur la meme machine que la base n'en est pas une :
# copiez le fichier produit ailleurs (disque externe, cloud, autre machine).
#
# Usage :
#   ./scripts/backup.sh [dossier_de_destination]
#
# Restauration :
#   gunzip -c pulsetrack-2026-08-10-1130.sql.gz | docker exec -i pulsetrack-postgres \
#     psql -U pulsetrack -d pulsetrack

set -euo pipefail

CONTAINER="${PULSETRACK_DB_CONTAINER:-pulsetrack-postgres}"
DB_NAME="${PULSETRACK_DB_NAME:-pulsetrack}"
DB_USER="${PULSETRACK_DB_USER:-pulsetrack}"
DEST_DIR="${1:-./backups}"

# Nombre de sauvegardes conservees. Au-dela, les plus anciennes sont effacees.
# 30 sauvegardes quotidiennes = un mois de marge pour s'apercevoir d'un probleme.
KEEP="${PULSETRACK_BACKUP_KEEP:-30}"

mkdir -p "$DEST_DIR"

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
    echo "Erreur : le conteneur '$CONTAINER' ne tourne pas." >&2
    echo "Demarrez la base (docker compose up -d) puis relancez." >&2
    exit 1
fi

STAMP="$(date +%Y-%m-%d-%H%M)"
TARGET="$DEST_DIR/pulsetrack-$STAMP.sql.gz"

echo "Sauvegarde de '$DB_NAME' vers $TARGET"

# --clean --if-exists : le dump peut etre rejoue sur une base existante sans
# conflit. On ecrit d'abord dans un fichier temporaire, renomme seulement en cas
# de succes : une sauvegarde tronquee qui porte le bon nom est un piege.
if docker exec "$CONTAINER" pg_dump \
        --username="$DB_USER" \
        --dbname="$DB_NAME" \
        --clean --if-exists --no-owner \
    | gzip > "$TARGET.partial"; then
    mv "$TARGET.partial" "$TARGET"
else
    rm -f "$TARGET.partial"
    echo "Erreur : la sauvegarde a echoue, aucun fichier conserve." >&2
    exit 1
fi

SIZE="$(du -h "$TARGET" | cut -f1)"
echo "Sauvegarde terminee : $TARGET ($SIZE)"

# Rotation : on ne garde que les $KEEP plus recentes.
COUNT="$(find "$DEST_DIR" -maxdepth 1 -name 'pulsetrack-*.sql.gz' | wc -l)"
if [ "$COUNT" -gt "$KEEP" ]; then
    find "$DEST_DIR" -maxdepth 1 -name 'pulsetrack-*.sql.gz' \
        | sort \
        | head -n "$((COUNT - KEEP))" \
        | while read -r old; do
            echo "Suppression de l'ancienne sauvegarde : $old"
            rm -f "$old"
        done
fi

echo
echo "Rappel : copiez ce fichier hors de cette machine."
