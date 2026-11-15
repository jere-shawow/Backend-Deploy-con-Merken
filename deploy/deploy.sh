#!/usr/bin/env bash
# Despliegue en el EC2 (lo ejecuta GitHub Actions por SSH; también sirve a mano).
#   ./deploy.sh <versión>
# 1. Guarda la imagen actual como "previous".
# 2. Construye la nueva imagen con el jar recién copiado y la levanta.
# 3. Espera a que responda /api/settings; si no responde, vuelve a la versión anterior.
set -euo pipefail
cd "$(dirname "$0")"

VERSION="${1:-manual}"
IMAGE="astronaut-backend"
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/api/settings}"
ATTEMPTS="${HEALTH_ATTEMPTS:-36}"   # 36 x 5 s = 3 minutos

if [ ! -f .env ]; then
  echo "ERROR: falta el archivo .env en $(pwd) (credenciales de MySQL / AWS). Ver .env.example" >&2
  exit 1
fi
if [ ! -f astronaut-backend.jar ]; then
  echo "ERROR: falta astronaut-backend.jar en $(pwd)" >&2
  exit 1
fi
mkdir -p data

if docker image inspect "$IMAGE:latest" >/dev/null 2>&1; then
  docker tag "$IMAGE:latest" "$IMAGE:previous"
  echo "Versión anterior guardada como $IMAGE:previous"
fi

echo "==> Construyendo y levantando la versión $VERSION"
docker compose build --pull
docker compose up -d --remove-orphans

echo "==> Esperando a que el backend responda en $HEALTH_URL"
for i in $(seq 1 "$ATTEMPTS"); do
  if curl -fsS -o /dev/null "$HEALTH_URL"; then
    echo "$VERSION" > .deployed-version
    docker image prune -f >/dev/null || true
    echo "==> Despliegue OK ($VERSION)"
    curl -fsS http://localhost:8080/api/reports/storage && echo
    exit 0
  fi
  sleep 5
done

echo "==> El backend no respondió. Últimos logs:" >&2
docker compose logs --tail=120 backend >&2 || true

if docker image inspect "$IMAGE:previous" >/dev/null 2>&1; then
  echo "==> Volviendo a la versión anterior" >&2
  docker tag "$IMAGE:previous" "$IMAGE:latest"
  docker compose up -d --no-build --force-recreate
fi
exit 1
