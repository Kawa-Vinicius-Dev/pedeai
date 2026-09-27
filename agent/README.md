# Agente de impressão (protótipo)

Primeiro passo da impressão automática ([04 · Impressão](../docs/04-impressao.md#primeiro-passo-recomendado-protótipo-de-impressão)):
imprimir a **página de teste** nas impressoras reais do restaurante-piloto para descobrir, em cada uma,
qual tabela de caracteres imprime os acentos, quantas colunas cabem, se a fonte dupla sai e se o corte funciona.

Java puro, sem dependências. Ainda não conversa com a API.

## Gerar

```bash
cd agent && ./mvnw verify          # testes + target/pedeai-agent.jar
```

Para levar a um computador **sem Java**, gere uma pasta com o runtime embutido (cerca de 75 MB; rodar
num Windows com JDK 21 ou mais novo):

```bash
jpackage --type app-image --name PedeAiAgente --input target --main-jar pedeai-agent.jar --add-modules java.base,java.desktop,jdk.charsets --win-console
```

`jdk.charsets` é obrigatório: é nele que estão as tabelas PC437, PC850 e PC860. Sem ele o programa nem abre.

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
