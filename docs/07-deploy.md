# 07 · Deploy

O frontend fica na Vercel. A API (Java) e o PostgreSQL precisam de um **processo sempre ligado**: o tempo real, a
fila de impressão e o polling do iFood (que mantém a loja online no iFood) não funcionam em serviço que "dorme".

## Onde rodar a API

| Opção | Custo | Quem cuida do servidor | Arquivo pronto |
| --- | --- | --- | --- |
| **Servidor próprio** (ex.: VM "Always Free" da Oracle Cloud, ARM, sempre ligada) | Grátis na Oracle; ou uma VPS pequena | Você: atualizações do sistema e disco | [`deploy/compose.prod.yaml`](../deploy/compose.prod.yaml) |
| **Railway** | Pago por uso (tem crédito de teste; confira o preço atual) | A Railway | [`backend/railway.toml`](../backend/railway.toml) |

Serviços que "dormem" sem acesso (como o plano grátis do Render) **não servem**: a impressão e o iFood param.

### Servidor próprio (Docker)

1. Crie a VM (Ubuntu), instale o Docker e abra as portas 80 e 443.
2. Aponte um domínio (registro A) para o IP da VM, por exemplo `api.seudominio.com.br`.
3. No servidor:

   ```bash
   git clone <repositório> pedeai && cd pedeai/deploy
   ```

   ```bash
   cp .env.prod.example .env
   ```

   Preencha o `.env` (senhas geradas com `openssl rand -base64 48`, domínio, endereço da Vercel).

   ```bash
   docker compose -f compose.prod.yaml up -d --build
   ```

4. O Caddy pede o certificado HTTPS sozinho. Confira `https://api.seudominio.com.br/actuator/health`.
5. **Backup:** o serviço `backup` grava um dump por dia em `deploy/backups/` e guarda 7 dias. Copie essa pasta
   para fora do servidor (outro disco, nuvem). Para restaurar:

   ```bash
   docker compose -f compose.prod.yaml exec -T postgres pg_restore -U pedeai -d pedeai --clean < backups/<arquivo>.dump
   ```

### Railway

1. Crie um projeto com dois serviços: **PostgreSQL** (da própria Railway) e a API, pelo repositório, com
   **Root Directory = `backend`** (o `railway.toml` já configura o Dockerfile e o health check).
2. Na API, configure as variáveis: `DB_URL` (`jdbc:postgresql://<host>:<porta>/<banco>` a partir dos dados do
   PostgreSQL da Railway), `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `REFRESH_COOKIE_SECURE=true`,
   `SIGNUP_ENABLED=false`, `APP_CORS_ALLOWED_ORIGINS` (endereço da Vercel) e, quando houver, `SENTRY_DSN` e as do
   iFood.
3. Gere um domínio público para a API nas configurações do serviço.

## Ligar a Vercel à API

O navegador fala só com o domínio do app, e a Vercel repassa `/api/*` para a API: assim o cookie de sessão
funciona sem CORS. Em [`frontend/vercel.json`](../frontend/vercel.json), antes da regra do `index.html`:

```json
{ "source": "/api/:path*", "destination": "https://api.seudominio.com.br/api/:path*" }
```

> ⚠️ Conexões longas (o tempo real e o agente) passam pelo repasse da Vercel, que tem tempo máximo por
> requisição. O app e o agente reconectam sozinhos. Se isso incomodar, o agente pode apontar direto para o
> domínio da API (ele não usa cookie).

## Checklist antes de abrir para o piloto

- [ ] `JWT_SECRET` e senha do banco gerados, fortes e só no `.env` / variáveis do provedor.
- [ ] `REFRESH_COOKIE_SECURE=true` e `SIGNUP_ENABLED=false`.
- [ ] `https://.../actuator/health` responde `UP`.
- [ ] Backup do dia gerado e copiado para fora do servidor; restauração testada uma vez.
- [ ] Sentry recebendo eventos (`SENTRY_DSN` na API e `VITE_SENTRY_DSN` na Vercel).
- [ ] Login, pedido de balcão e delivery testados no domínio final, no computador do caixa.
- [ ] Agente pareado apontando para o endereço final e página de teste impressa.
- [ ] Impressão e aceite automáticos de **outros programas** desligados (evita pedido duplicado).
