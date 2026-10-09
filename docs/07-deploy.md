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

### Oracle Cloud com o banco no Supabase (o caminho em uso)

O banco fica no projeto `pedeai` do Supabase (região São Paulo), num papel e schema próprios (`pedeai`): a API não usa
o usuário `postgres`. Na VM só rodam a API, o Caddy (HTTPS) e o backup diário
([`deploy/compose.supabase.yaml`](../deploy/compose.supabase.yaml)).

1. Na Oracle Cloud, crie a instância: **Ubuntu 22.04 ou 24.04**, forma **VM.Standard.A1.Flex** (ARM, Always Free;
   2 OCPU e 12 GB bastam). Guarde a chave SSH que ela oferece. Se der "Out of capacity" (comum em São Paulo),
   a **VM.Standard.E2.1.Micro** (1 GB, também Always Free) serve: o instalador baixa a imagem pronta da API, que o
   CI publica em `ghcr.io/kawa-vinicius-dev/pedeai-api`, em vez de compilar na VM.
2. Na **lista de segurança da VCN** da instância, adicione regras de entrada TCP para as portas **80** e **443**
   (origem `0.0.0.0/0`).
3. Entre na VM (pelo botão **Cloud Shell** do console ou por SSH) e rode, com a senha do papel `pedeai`:

   ```bash
   curl -fsSL https://raw.githubusercontent.com/Kawa-Vinicius-Dev/pedeai/main/deploy/instalar-oracle.sh \
     | sudo DB_PASSWORD='<senha do papel pedeai>' bash
   ```

   O script instala o Docker, abre as portas no firewall da imagem Ubuntu, acha o pooler do Supabase, gera o
   `JWT_SECRET`, sobe tudo e espera o HTTPS. No fim mostra o endereço da API, no formato
   `https://api-<ip-com-traços>.sslip.io` (o [sslip.io](https://sslip.io) dá um nome ao IP, então não precisa de
   domínio próprio). Rodar o script de novo atualiza o código e mantém o `.env`.
4. Ponha esse endereço no `frontend/vercel.json` (seção abaixo).
5. Depois de criar a sua loja, desligue o cadastro aberto: em `/opt/pedeai/deploy/.env`, `SIGNUP_ENABLED=false`, e
   `docker compose -f compose.supabase.yaml up -d`.

O Supabase grátis pausa projeto sem uso por uma semana; com a API ligada (ela consulta o banco o tempo todo) isso
não acontece. O plano grátis também não tem backup para baixar: o serviço `backup` da VM grava o dump diário em
`/opt/pedeai/deploy/backups/`.

### Railway

1. Crie um projeto com dois serviços: **PostgreSQL** (da própria Railway) e a API, pelo repositório, com
   **Root Directory = `backend`** (o `railway.toml` já configura o Dockerfile e o health check).
2. Na API, configure as variáveis: `DB_URL` (`jdbc:postgresql://<host>:<porta>/<banco>` a partir dos dados do
   PostgreSQL da Railway), `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `REFRESH_COOKIE_SECURE=true`,
   `SIGNUP_ENABLED=false`, `APP_CORS_ALLOWED_ORIGINS` (endereço da Vercel) e, quando houver, `SENTRY_DSN`, as do
   iFood e as `R2_*` das fotos.
3. Gere um domínio público para a API nas configurações do serviço.

## Ligar a Vercel à API

O navegador fala só com o domínio do app, e a Vercel repassa `/api/*` para a API: assim o cookie de sessão
funciona sem CORS. Em [`frontend/vercel.json`](../frontend/vercel.json), antes da regra do `index.html`:

```json
{ "source": "/api/:path*", "destination": "https://api.seudominio.com.br/api/:path*" }
```

O cardápio digital do cliente é a página `menu.html`, servida em `/loja/<endereço>`: o `vercel.json` já tem a
regra. O link de cada loja aparece em **Configurações > Loja**.

> ⚠️ Conexões longas (o tempo real e o agente) passam pelo repasse da Vercel, que tem tempo máximo por
> requisição. O app e o agente reconectam sozinhos. Se isso incomodar, o agente pode apontar direto para o
> domínio da API (ele não usa cookie).

## Fotos dos produtos

As fotos ficam no **Cloudflare R2** (sem custo de saída; até 10 GB grátis). Sem as variáveis abaixo, a API funciona
e só o envio de foto avisa que está desligado.

1. No painel da Cloudflare: **R2 > Create bucket** (ex.: `pedeai-fotos`).
2. No bucket: **Settings > Public access** ligue o endereço público `r2.dev` ou um domínio seu
   (ex.: `fotos.seudominio.com.br`). Esse endereço é o `R2_PUBLIC_URL`.
3. **R2 > Manage API tokens > Create API token** com permissão **Object Read & Write** só nesse bucket.
4. Nas variáveis da API (nunca no repositório): `R2_ACCOUNT_ID` (o id da conta, no painel do R2),
   `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET` e `R2_PUBLIC_URL`.

A tela reduz a foto antes de mandar (lado maior 1200 px, JPEG); a API aceita JPEG ou PNG até 2 MB e confere o
formato pelo conteúdo. Trocar a foto apaga a antiga.

## Checklist antes de abrir para o piloto

- [ ] `JWT_SECRET` e senha do banco gerados, fortes e só no `.env` / variáveis do provedor.
- [ ] `REFRESH_COOKIE_SECURE=true` e `SIGNUP_ENABLED=false`.
- [ ] `https://.../actuator/health` responde `UP`.
- [ ] Backup do dia gerado e copiado para fora do servidor; restauração testada uma vez.
- [ ] Sentry recebendo eventos (`SENTRY_DSN` na API e `VITE_SENTRY_DSN` na Vercel).
- [ ] Login, pedido de balcão e delivery testados no domínio final, no computador do caixa.
- [ ] Agente pareado apontando para o endereço final e página de teste impressa.
- [ ] Impressão e aceite automáticos de **outros programas** desligados (evita pedido duplicado).
- [ ] iFood, quando houver credenciais: `IFOOD_ENABLED=true`, `IFOOD_CLIENT_ID` e `IFOOD_CLIENT_SECRET` nas variáveis
      do provedor, e o webhook cadastrado no iFood Developer como `https://api.seudominio.com.br/api/integrations/ifood/webhook`
      (direto na API, sem passar pela Vercel). O polling segue ligado como contingência.
- [ ] 99Food, quando houver credenciamento como integradora: `NINETYNINE_BASE_URL`, `NINETYNINE_CLIENT_ID`,
      `NINETYNINE_CLIENT_SECRET` e `NINETYNINE_APP_ID`, e o webhook cadastrado como
      `https://api.seudominio.com.br/api/integrations/opendelivery/webhook`. Outro app Open Delivery usa as variáveis
      `OPENDELIVERY_*`.
- [ ] Fotos: variáveis `R2_*` configuradas e uma foto enviada pela tela do produto aparecendo no cardápio digital.
- [ ] Versão do agente: `AGENT_LATEST_VERSION` e `AGENT_DOWNLOAD_URL` apontando para o instalador publicado, para a
      tela de impressão marcar computadores desatualizados.
