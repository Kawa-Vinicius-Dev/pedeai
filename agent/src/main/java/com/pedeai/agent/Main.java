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
 * Protótipo do agente (docs/04-impressao.md#primeiro-passo-recomendado-protótipo-de-impressão): imprime a página de
 * teste nas impressoras reais do piloto para descobrir tabela de caracteres, colunas, fonte dupla e corte.
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
        if (args.length == 0 || !List.of("listar", "testar").contains(args[0])) {
            System.out.print(USAGE);
            System.exit(args.length == 0 ? 0 : 2);
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
