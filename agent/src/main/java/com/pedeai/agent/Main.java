package com.pedeai.agent;

import javax.print.PrintException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Agente de impressão (docs/04-impressao.md#o-agente). {@code parear} liga o computador à loja, {@code rodar} imprime
 * a fila, e {@code testar} imprime a página de teste para descobrir tabela de caracteres, colunas, fonte dupla e corte.
 */
public final class Main {
    static final String ACCENTS = "ÁÉÍÓÚ ÂÊÔ ÃÕ Ç áéíóú ãõ ç";

    /** Tabelas mais comuns em térmicas ESC/POS. O {@code n} é o da Epson; outras marcas podem usar outro. */
    record Codepage(int n, String label, Charset charset) {
    }

    static final List<Codepage> CODEPAGES = List.of(
            new Codepage(0, "PC437", Charset.forName("IBM437")),
            new Codepage(2, "PC850", Charset.forName("IBM850")),
            new Codepage(3, "PC860 (português)", Charset.forName("IBM860")),
            new Codepage(16, "WPC1252", Charset.forName("windows-1252")),
            new Codepage(19, "PC858", Charset.forName("IBM00858")));

    private static final String USAGE = """
            Uso:
              java -jar pedeai-agent.jar parear https://api.pedeai.com.br 123456 [--nome "Caixa"]
                  Liga este computador à loja com o código da tela Configurações > Impressão.
              java -jar pedeai-agent.jar rodar
                  Busca os pedidos e imprime. Depois de pareado, abrir o agente sem nada também roda.
              java -jar pedeai-agent.jar nao-iniciar-com-windows
                  O pareamento faz o agente abrir junto com o Windows; isto desliga.
              java -jar pedeai-agent.jar listar
                  Mostra as impressoras instaladas no Windows.
              java -jar pedeai-agent.jar testar --rede 192.168.0.50[:9100] [--colunas 48]
              java -jar pedeai-agent.jar testar --impressora "Nome no Windows" [--colunas 48]
              java -jar pedeai-agent.jar testar --arquivo teste.bin [--colunas 48]
                  Imprime a página de teste (ou grava os bytes num arquivo).
                  --colunas: 48 no papel de 80mm, 32 no de 58mm.
            """;

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 && Settings.load() != null) {
            run();
            return;
        }
        if (args.length == 0 && System.console() != null) {
            // Primeira vez, aberto com dois cliques: pergunta o que a tela de impressão mostra e já começa a imprimir.
            System.out.println("PedeAí: primeira vez neste computador.");
            System.out.println("Na tela Configurações > Impressão, clique em Adicionar computador.");
            String url = System.console().readLine("Endereço do PedeAí: ").trim();
            String code = System.console().readLine("Código de 6 dígitos: ").trim();
            if (!tryPair(new String[]{"parear", url, code})) {
                // Aberto com dois cliques, a janela fecharia antes de dar para ler o erro.
                System.console().readLine("Aperte Enter para fechar e tente de novo.");
                System.exit(1);
            }
            run();
            return;
        }
        if (args.length == 0
                || !List.of("listar", "testar", "parear", "rodar", "nao-iniciar-com-windows").contains(args[0])) {
            System.out.print(USAGE);
            System.exit(args.length == 0 ? 0 : 2);
        }
        if (args[0].equals("nao-iniciar-com-windows")) {
            System.out.println(Startup.disable() ? "O agente não abre mais com o Windows."
                    : "O agente já não abria com o Windows.");
            return;
        }
        if (args[0].equals("parear")) {
            pair(args);
            return;
        }
        if (args[0].equals("rodar")) {
            run();
            return;
        }
        if (args[0].equals("listar")) {
            List<String> printers = Printers.installed();
            System.out.println(printers.isEmpty() ? "Nenhuma impressora instalada." : String.join("\n", printers));
            return;
        }

        String network = option(args, "--rede");
        String system = option(args, "--impressora");
        String file = option(args, "--arquivo");
        String columns = option(args, "--colunas");
        byte[] page = testPage(columns == null ? 48 : Integer.parseInt(columns));
        try {
            if (network != null) {
                String[] hostAndPort = network.split(":");
                int port = hostAndPort.length > 1 ? Integer.parseInt(hostAndPort[1]) : Printers.DEFAULT_PORT;
                Printers.sendToNetwork(hostAndPort[0], port, page);
            } else if (system != null) {
                Printers.sendToSystemPrinter(system, page);
            } else if (file != null) {
                Files.write(Path.of(file), page);
            } else {
                System.out.print(USAGE);
                System.exit(2);
            }
        } catch (IOException | PrintException e) {
            // Quem roda o teste está no restaurante: mensagem e o que conferir, sem stack trace.
            System.err.println("Não foi possível imprimir: " + e.getMessage());
            System.err.println(network != null
                    ? "Confira se a impressora está ligada, no mesmo Wi-Fi/rede e com o IP certo (imprima a "
                    + "autoconfiguração dela para ver o IP)."
                    : "Confira o nome exato com \"listar\" e se a impressora está ligada.");
            System.exit(1);
        }
        System.out.println("Página de teste enviada (" + page.length + " bytes).");
    }

    private static void pair(String[] args) {
        if (args.length < 3) {
            System.out.print(USAGE);
            System.exit(2);
        }
        if (!tryPair(args)) {
            System.exit(1);
        }
    }

    /** Pareia e liga a inicialização com o Windows. Falhou: explica o motivo e devolve falso. */
    private static boolean tryPair(String[] args) {
        String name = option(args, "--nome");
        try {
            ApiClient.Pairing pairing = ApiClient.pair(args[1], args[2].trim(),
                    name == null ? hostName() : name, System.getProperty("os.name"));
            Settings.save(new Settings(args[1], pairing.token(), pairing.agentName()));
            System.out.println("Pareado com " + pairing.storeName() + " como \"" + pairing.agentName() + "\".");
            Startup.enable().ifPresent(file -> System.out.println("O agente vai abrir sozinho com o Windows."));
            System.out.println("Agora cadastre as impressoras na tela Configurações > Impressão e deixe o agente rodando.");
            return true;
        } catch (ApiClient.ApiException e) {
            System.err.println(e.status == 401 ? "Código inválido ou vencido. Gere outro na tela de impressão."
                    : "Não deu para parear: " + e.getMessage());
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            System.err.println("Sem conexão com " + args[1] + ": " + e.getMessage());
        }
        return false;
    }

    private static void run() throws IOException {
        Settings settings = Settings.load();
        if (settings == null) {
            System.err.println("Este computador ainda não foi pareado. Rode: java -jar pedeai-agent.jar parear ...");
            System.exit(2);
        }
        System.out.println("PedeAí agente " + ApiClient.VERSION + ": " + settings.name() + " imprimindo. Ctrl+C para parar.");
        Journal journal = new Journal(Settings.folder().resolve("diario.log"), java.time.Instant.now());
        try {
            new AgentRunner(new ApiClient(settings.url(), settings.token()), journal, java.time.Clock.systemUTC(),
                    StatusTray.create(settings.name())).run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String hostName() {
        String name = System.getenv("COMPUTERNAME");
        return name == null || name.isBlank() ? "Computador de impressão" : name;
    }

    /**
     * Uma linha de acentos por tabela, com o {@code n} na frente: a pessoa anota a que saiu certa. Depois, régua de
     * colunas, negrito, fonte dupla e corte.
     */
    static byte[] testPage(int columns) {
        EscPos page = new EscPos().init()
                .align(EscPos.Align.CENTER).size(2, 2).line("PedeAi").size(1, 1)
                .line("Pagina de teste").line("")
                .align(EscPos.Align.LEFT)
                .line("Tabelas de caracteres (anote o n")
                .line("da linha com os acentos certos):");
        for (Codepage codepage : CODEPAGES) {
            page.codepage(codepage.n(), codepage.charset())
                    .line("n=" + codepage.n() + " " + codepage.label() + ":")
                    .line("  " + ACCENTS);
        }
        page.codepage(0, CODEPAGES.getFirst().charset())
                .line("Sem acentos: " + withoutAccents(ACCENTS)).line("")
                .line("Colunas (a regua deve ocupar")
                .line("a linha inteira, sem quebrar):")
                .line(ruler(columns)).line("")
                .bold(true).line("Negrito").bold(false)
                .size(2, 2).line("1x PIZZA GRANDE").size(1, 1)
                .line("(fonte dupla)")
                .line("")
                .line("Se o papel foi cortado abaixo,")
                .line("o corte funciona.")
                .cut(true);
        return page.toBytes();
    }

    /** Traços com a dezena marcada e "|" na última coluna: dá para contar as colunas no papel. */
    static String ruler(int columns) {
        StringBuilder ruler = new StringBuilder();
        for (int column = 1; column <= columns; column++) {
            ruler.append(column == columns ? '|' : column % 10 == 0 ? Character.forDigit(column / 10 % 10, 10) : '-');
        }
        return ruler.toString();
    }

    static String withoutAccents(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String option(String[] args, String name) {
        List<String> list = new ArrayList<>(List.of(args));
        int index = list.indexOf(name);
        return index >= 0 && index + 1 < list.size() ? list.get(index + 1) : null;
    }
}
