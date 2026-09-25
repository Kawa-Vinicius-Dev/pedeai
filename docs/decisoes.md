# Registro de decisões

Formato curto: contexto, decisão, alternativas e consequências. Uma decisão
nova entra no fim da lista. Uma decisão revista ganha uma entrada nova que
aponta para a antiga, sem apagar a antiga.

## D01 · Monolito modular

- **Contexto:** o sistema tem uma equipe pequena, precisa operar sem parar em
  horário de pico, e a escala é modesta (centenas de pedidos por loja por dia).
- **Decisão:** uma aplicação Spring Boot com módulos de fronteira explícita,
  verificados por ArchUnit.
- **Alternativas:** microserviços (custo operacional alto, falhas distribuídas
  sem necessidade).
- **Consequências:** um deploy e um banco. Um módulo pode virar serviço no
  futuro, se houver motivo, porque a fronteira já existe.

## D02 · PostgreSQL como fila

- **Contexto:** impressão, eventos de marketplace e ações de status precisam de
  fila confiável.
- **Decisão:** tabelas de trabalho (`print_job`, `inbound_event`,
  `outbound_action`) com reserva por `UPDATE` condicional.
- **Alternativas:** RabbitMQ, Kafka, Redis (mais uma peça para operar e
  monitorar, e a escala não pede).
- **Consequências:** o trabalho é gravado na mesma transação do negócio. Há um
  limite de vazão, muito acima do necessário. Se um dia for preciso, a troca
  fica atrás de interfaces.

## D03 · Efeitos colaterais por listeners na mesma transação e workers

- **Contexto:** confirmar pedido precisa gerar impressão e sincronização **com
  garantia**.
- **Decisão:** eventos de domínio do Spring. Listeners síncronos só gravam
  linhas de trabalho: não chamam rede e não lançam erro por configuração.
  Workers processam essas linhas. O aviso para as telas (SSE) sai após o
  commit.
- **Alternativas:** registro de publicação de eventos do Spring Modulith (bom,
  mas exige expor pacotes internos como *named interfaces* e briga com a
  estrutura em camadas da convenção); `@Async` após o commit (perde trabalho se
  o servidor cair).
- **Consequências:** o mecanismo é simples e explícito, e fácil de testar com H2.
  A regra "listener síncrono não chama rede" precisa ser seguida à risca.

## D04 · Tempo real com SSE

- **Contexto:** o quadro de pedidos, a cozinha e o agente precisam saber das
  mudanças na hora.
- **Decisão:** SSE, com o evento como aviso e o REST como fonte da verdade. O
  agente também consulta a fila a cada 30 s.
- **Alternativas:** WebSocket/STOMP (bidirecional sem necessidade, mais
  complexo atrás de proxy); polling curto (atraso ou carga).
- **Consequências:** reconectar é só recarregar. Com mais de uma instância da
  API, o SSE ganha `LISTEN/NOTIFY` do PostgreSQL.

## D05 · Impressão por agente local com conexão de saída

- **Contexto:** a nuvem não alcança a impressora. O navegador tem limites
  (diálogo, WebUSB no Windows, permissão de rede local desde o Chrome 142) e
  não imprime com a aba fechada.
- **Decisão:** um agente instalado no restaurante busca trabalhos por HTTPS e
  imprime por TCP 9100 ou pelo spooler do Windows. A impressão pelo navegador
  fica como contingência manual.
- **Alternativas:** `window.print`, kiosk printing, WebUSB, agente em
  `localhost` chamado pelo navegador, QZ Tray. Comparação em
  [04 · Impressão](04-impressao.md#por-que-não-imprimir-direto-do-navegador).
- **Consequências:** exige instalar um programa (um MSI e um código de
  pareamento). Em troca, imprime sozinho, detecta falhas e roteia por setor.

## D06 · Layout e ESC/POS gerados no servidor

- **Decisão:** o servidor gera os bytes para o perfil de cada impressora
  (colunas, tabela de caracteres, corte). O agente só entrega os bytes.
- **Alternativas:** o agente renderiza a partir de um JSON (mais lógica no
  computador do restaurante e mais atualizações para distribuir).
- **Consequências:** layout e correções mudam num deploy só. Os testes usam
  golden files no backend.

## D07 · Agente em Java 21 empacotado com jpackage

- **Decisão:** Java puro, sem Spring. Instalador MSI com o runtime embutido.
- **Alternativas:** Go (binário menor, mas outra linguagem na equipe); .NET
  (bom no Windows, outra pilha); Electron (pesado).
- **Consequências:** o instalador tem cerca de 50 MB, e a equipe fica numa
  linguagem só. A assinatura de código do instalador fica para o lançamento
  comercial.

## D08 · Multi-loja por coluna `store_id`

- **Decisão:** esquema compartilhado. `store_id` em todas as tabelas de negócio,
  vindo sempre do usuário autenticado. Busca por `id` e loja.
- **Alternativas:** um schema ou um banco por loja (migrações e operação
  multiplicadas); Row Level Security do PostgreSQL (boa defesa extra, mas não
  roda no H2 dos testes; pode entrar depois).
- **Consequências:** exige disciplina em toda consulta. Por isso existe um teste
  de IDOR em todo controller.

## D09 · JWT curto e refresh token rotativo

- **Decisão:** token de acesso de 15 min e refresh token de 30 dias, guardado
  como hash, rotativo e revogável por dispositivo. O refresh token só existe num
  cookie HttpOnly e SameSite=Strict, restrito a `/api/auth`, e o token de acesso
  fica só na memória da aba. Reusar um refresh token já trocado derruba todas as
  sessões da pessoa, porque indica cópia. A exceção são os 30 s seguintes à troca:
  aí é corrida benigna (duas abas renovando juntas, ou resposta perdida na rede).
  Os agentes usam token de dispositivo próprio.
- **Alternativas:** sessão com cookie (simples, mas frágil com frontend e API em
  domínios diferentes e com o SSE); refresh token no `localStorage` (qualquer XSS
  o levaria).
- **Consequências:** o SSE do frontend usa um cliente baseado em `fetch`, que
  envia o header `Authorization`.

## D10 · Dinheiro em centavos

- **Decisão:** `BIGINT` em centavos no banco. `Money` com `long` no Java.
  Inteiro em centavos no JSON.
- **Alternativas:** `NUMERIC` e `BigDecimal` (corretos no Java, mas o JavaScript
  soma decimais em ponto flutuante).
- **Consequências:** o frontend só formata. Os adaptadores de marketplace
  convertem os decimais recebidos com checagem de escala.

## D11 · UUID gerado na aplicação e número diário legível

- **Decisão:** PK `UUID` v7. Para as pessoas, "Pedido 42", que recomeça a cada
  dia operacional (virada configurável, padrão 05:00).
- **Consequências:** IDs não enumeráveis na API e números curtos no balcão e na
  cozinha.

## D12 · Comanda agrupa rodadas

- **Contexto:** no salão, a conta fica aberta por horas e a cozinha recebe itens
  em várias levas.
- **Decisão:** `Tab` (comanda) é a cobrança. Cada envio de itens é um `Order`
  `DINE_IN` ligado à comanda, com seu próprio status de produção.
- **Alternativas:** um pedido único que cresce (o status de produção fica
  confuso quando uma parte está pronta e outra acabou de ser pedida).
- **Consequências:** a cozinha e a impressão tratam a rodada como qualquer
  pedido. O pagamento aponta para o pedido ou para a comanda.

## D13 · Status no nível do pedido (por enquanto)

- **Decisão:** no MVP, o status é do pedido inteiro. A tela da cozinha filtra
  itens por setor.
- **Alternativas:** um ticket de produção por setor, com status próprio.
- **Consequências:** o modelo fica mais simples agora. Como o setor já está
  gravado em cada item, adicionar `production_ticket` depois não quebra nada.

## D14 · 99Food pelo padrão Open Delivery

- **Contexto:** a 99Food concluiu a adesão ao Open Delivery em novembro de 2025,
  assim como o Keeta.
- **Decisão:** um adaptador `opendelivery` genérico, com a 99Food como
  configuração.
- **Alternativas:** um adaptador só para a API própria da 99Food.
- **Consequências:** outras plataformas aderentes saem mais baratas. Antes da
  Etapa 6, confirmar no portal oficial qual API a 99Food recomenda para PDV.

## D15 · Código em inglês, interface em português

- **Decisão:** classes, tabelas, enums e rotas da API em inglês, como no
  Denguinho. Telas, mensagens e documentação em português. Um glossário faz a
  ponte.
- **Consequências:** termos sem tradução boa ficam registrados no
  [glossário](02-arquitetura.md#glossário).

## D16 · Spring Boot 4.1

- **Contexto:** a convenção cita Spring Boot 3.x, mas a linha 3.5 saiu do
  suporte open source em 30/06/2026. O Denguinho já usa a 4.0.
- **Decisão:** Spring Boot 4.1.x, a linha atual.
- **Consequências:** vale atualizar a convenção para "Spring Boot 4.x".

## D17 · H2 nos testes, SQL portável e smoke test em PostgreSQL

- **Contexto:** a convenção usa H2 em memória nos testes.
- **Decisão:** manter o H2 (modo PostgreSQL) e escrever migrações portáveis: sem
  `JSONB`, sem índice parcial, sem `ON CONFLICT`. O CI também roda as migrações
  e um smoke test contra PostgreSQL real.
- **Alternativas:** Testcontainers em todos os testes de integração (mais fiel,
  porém mais lento e fora da convenção).
- **Consequências:** algumas regras (por exemplo, "uma comanda aberta por mesa")
  ficam no serviço em vez de numa constraint. Se o H2 começar a esconder bugs,
  reavaliar com Testcontainers.
