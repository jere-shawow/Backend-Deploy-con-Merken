#!/usr/bin/env bash
# Preparación del EC2 (Amazon Linux 2023), UNA sola vez:
#   scp -i backendRegistro.pem deploy/setup-ec2.sh ec2-user@IP:~
#   ssh -i backendRegistro.pem ec2-user@IP 'bash setup-ec2.sh'
# Después: cerrar sesión y volver a entrar (para usar docker sin sudo).
set -euo pipefail

sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"

# Plugin "docker compose"
COMPOSE_DIR=/usr/local/lib/docker/cli-plugins
sudo mkdir -p "$COMPOSE_DIR"
sudo curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" \
  -o "$COMPOSE_DIR/docker-compose"
sudo chmod +x "$COMPOSE_DIR/docker-compose"

# Plugin "buildx" (docker compose build lo necesita en versiones recientes)
BUILDX_VERSION=$(curl -fsSL https://api.github.com/repos/docker/buildx/releases/latest | grep -m1 '"tag_name"' | cut -d'"' -f4)
ARCH=$(uname -m); [ "$ARCH" = "x86_64" ] && ARCH=amd64; [ "$ARCH" = "aarch64" ] && ARCH=arm64
sudo curl -fsSL "https://github.com/docker/buildx/releases/download/${BUILDX_VERSION}/buildx-${BUILDX_VERSION}.linux-${ARCH}" \
  -o "$COMPOSE_DIR/docker-buildx"
sudo chmod +x "$COMPOSE_DIR/docker-buildx"

mkdir -p ~/backend/data
echo "Listo. Crea ~/backend/.env (ver .env.example) y vuelve a entrar por SSH."
docker --version || true
