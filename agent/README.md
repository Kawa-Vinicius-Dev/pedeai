# Agente de impressão

Roda num computador da loja, busca a fila de impressão na API e manda os bytes ESC/POS para as impressoras
térmicas, pela rede (porta 9100) ou pelo spooler do Windows ([04 · Impressão](../docs/04-impressao.md#o-agente)).
Também imprime a **página de teste**, que descobre em cada impressora a tabela de caracteres, as colunas, a
fonte dupla e o corte.

Java puro, sem Spring. Só o Jackson (para o JSON da API), embutido no jar.

## Instalar e ligar à loja

1. Baixe o instalador `PedeAiAgente-msi` nos artefatos da última execução do CI (GitHub › Actions) e instale no
   computador da loja. Não precisa de Java nem de administrador: instala na pasta do usuário e cria o atalho
   **PedeAi Agente** no menu Iniciar e na área de trabalho.
2. Na tela **Configurações › Impressão**, clique em **Adicionar computador**. A tela mostra o endereço e um
   código de 6 dígitos.
3. Abra o **PedeAi Agente** e digite o endereço e o código. Pronto: ele começa a imprimir e passa a abrir
   sozinho com o Windows.
4. Cadastre as impressoras na mesma tela (com o resultado da página de teste) e escolha a impressora de cada
   setor.

Pela linha de comando dá para fazer o mesmo:

```bash
java -jar pedeai-agent.jar parear https://endereco-do-pedeai 123456 --nome "Caixa"
```

```bash
java -jar pedeai-agent.jar rodar
```

Para o agente não abrir mais com o Windows: `nao-iniciar-com-windows`.

O pareamento fica em `%APPDATA%\PedeAi\agente.properties` e o diário das impressões em
`%APPDATA%\PedeAi\diario.log` (últimos 7 dias). O diário anota "recebido" antes de mandar para a impressora e
"impresso" depois: a mesma chave nunca sai duas vezes, e se o computador desligar no meio, o trabalho aparece
como **incerto** na tela em vez de sair de novo sozinho. Remover o computador na tela para o agente na hora.

O agente consulta a fila a cada 2 s, testa as impressoras de rede e manda o status a cada 20 s. Sem
internet, ele espera e tenta de novo.

## Gerar

```bash
cd agent && ./mvnw verify          # testes + target/pedeai-agent.jar
```

O CI gera o instalador MSI (job "Instalador do agente"). Para gerar uma pasta com o runtime embutido na sua
máquina (cerca de 75 MB; num Windows com JDK 21 ou mais novo), passe só o jar para o `jpackage`:

```bash
mkdir staging && copy target\pedeai-agent.jar staging\
```

```bash
jpackage --type app-image --name PedeAiAgente --input staging --main-jar pedeai-agent.jar --add-modules java.base,java.desktop,java.net.http,jdk.charsets --win-console
```

`jdk.charsets` tem as tabelas PC437, PC850 e PC860, e `java.net.http` é o cliente da API: sem eles o agente não
abre.

Copie a pasta `PedeAiAgente` para o computador do restaurante e use `PedeAiAgente\PedeAiAgente.exe`
no lugar de `java -jar pedeai-agent.jar`.

## Usar no restaurante

```bash
java -jar pedeai-agent.jar listar
java -jar pedeai-agent.jar testar --rede 192.168.0.50 --colunas 48
java -jar pedeai-agent.jar testar --impressora "ELGIN i9" --colunas 48
```

- **Rede:** o IP sai na autoconfiguração da impressora (em geral, segurar o botão de avanço ao ligar). A porta é 9100.
- **USB:** a impressora precisa estar instalada no Windows (driver do fabricante ou "Generic / Text Only").
  Use o nome exato que o `listar` mostra.
- `--colunas`: 48 no papel de 80mm, 32 no de 58mm. Se a régua quebrar em duas linhas, tente um número menor.

## O que anotar de cada impressora

| Campo | Onde ver |
| --- | --- |
| Marca, modelo, papel (58 ou 80mm) | Etiqueta da impressora |
| Conexão | Rede (IP) ou USB (nome no Windows) |
| Tabela de caracteres | O `n=` da linha com `ÁÉÍÓÚ ÂÊÔ ÃÕ Ç` certos. Se nenhuma sair certa: "sem acentos" |
| Colunas | A régua coube inteira numa linha? |
| Fonte dupla | "1x PIZZA GRANDE" saiu com o dobro do tamanho? |
| Corte | O papel foi cortado no fim? |

Com essas respostas, o agente de verdade (pareamento, fila de trabalhos, diário local e heartbeat) usa a
configuração certa de cada impressora.
