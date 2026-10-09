#!/usr/bin/env bash
# Instala a API do PedeAí numa VM Ubuntu (por exemplo, a "Always Free" da Oracle Cloud), com o banco no Supabase.
# Na VM, como root:
#   curl -fsSL https://raw.githubusercontent.com/Kawa-Vinicius-Dev/pedeai/main/deploy/instalar-oracle.sh \
#     | sudo DB_PASSWORD='senha do papel pedeai no Supabase' bash
# Fotos (opcional): acrescente R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, R2_BUCKET e R2_PUBLIC_URL depois do sudo.
# Rodar de novo atualiza o código e reinicia a API, mantendo o .env (e o JWT_SECRET) que já existem.
set -euo pipefail

: "${DB_PASSWORD:?Informe DB_PASSWORD (senha do papel pedeai no Supabase).}"
SUPABASE_REF="${SUPABASE_REF:-vphomwajbjyxeqskzyis}"
SUPABASE_REGION="${SUPABASE_REGION:-sa-east-1}"
APP_ORIGIN="${APP_ORIGIN:-https://pedeai-phi.vercel.app}"
SIGNUP_ENABLED="${SIGNUP_ENABLED:-true}"
REPO="${REPO:-https://github.com/Kawa-Vinicius-Dev/pedeai.git}"
DIR=/opt/pedeai

[ "$(id -u)" -eq 0 ] || { echo "Rode como root (sudo)."; exit 1; }
log() { printf '\n==> %s\n' "$*"; }

log "Memória de troca (o build da API precisa de folga em VM pequena)"
if ! swapon --show | grep -q .; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

log "Docker e Git"
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sh
fi
command -v git >/dev/null || { apt-get update -q && apt-get install -yq git; }

log "Portas 80 e 443 (a imagem Ubuntu da Oracle bloqueia tudo no iptables, além da lista de segurança da VCN)"
for port in 80 443; do
  iptables -C INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT 2>/dev/null \
    || iptables -I INPUT 1 -p tcp --dport "$port" -m state --state NEW -j ACCEPT
done
command -v netfilter-persistent >/dev/null && netfilter-persistent save || true

log "Código"
if [ -d "$DIR/.git" ]; then git -C "$DIR" pull --ff-only; else git clone "$REPO" "$DIR"; fi
cd "$DIR/deploy"

log "Endereço do pooler do Supabase"
POOLER=""
for prefix in aws-0 aws-1 aws-2; do
  host="$prefix-$SUPABASE_REGION.pooler.supabase.com"
  if docker run --rm -e PGPASSWORD="$DB_PASSWORD" postgres:17-alpine \
      psql "host=$host port=5432 dbname=postgres user=pedeai.$SUPABASE_REF sslmode=require connect_timeout=10" \
      -tAc 'select 1' >/dev/null 2>&1; then
    POOLER="$host"; break
  fi
done
[ -n "$POOLER" ] || { echo "Não conectou no Supabase. Confira a senha e se o projeto está ativo."; exit 1; }
echo "Banco: $POOLER"

if [ ! -f .env ]; then
  log "Configuração (.env)"
  IP="$(curl -fsS https://api.ipify.org)"
  API_DOMAIN="${API_DOMAIN:-api-${IP//./-}.sslip.io}"
  umask 077
  cat > .env <<ENV
DB_URL=jdbc:postgresql://$POOLER:5432/postgres?sslmode=require&currentSchema=pedeai
DB_USERNAME=pedeai.$SUPABASE_REF
DB_PASSWORD=$DB_PASSWORD
PGHOST=$POOLER
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
API_DOMAIN=$API_DOMAIN
APP_CORS_ALLOWED_ORIGINS=$APP_ORIGIN
SIGNUP_ENABLED=$SIGNUP_ENABLED
ENV
fi
API_DOMAIN="$(grep '^API_DOMAIN=' .env | cut -d= -f2)"

# Fotos no Cloudflare R2 (opcional): as variáveis R2_* passadas no comando entram no .env, também ao rodar de novo.
for name in R2_ACCOUNT_ID R2_ACCESS_KEY_ID R2_SECRET_ACCESS_KEY R2_BUCKET R2_PUBLIC_URL; do
  value="${!name:-}"
  if [ -n "$value" ]; then
    sed -i "/^$name=/d" .env
    echo "$name=$value" >> .env
  fi
done

log "Subindo (o primeiro build leva alguns minutos)"
docker compose -f compose.supabase.yaml up -d --build

log "Esperando a API responder em https://$API_DOMAIN"
for _ in $(seq 1 60); do
  if curl -fsS "https://$API_DOMAIN/actuator/health" 2>/dev/null | grep -q UP; then
    echo; echo "PRONTO: https://$API_DOMAIN"
    echo "Mande esse endereço para configurar a Vercel."
    exit 0
  fi
  sleep 10
done
echo "A API não respondeu em 10 minutos. Veja: docker compose -f $DIR/deploy/compose.supabase.yaml logs --tail 100 api caddy"
exit 1
