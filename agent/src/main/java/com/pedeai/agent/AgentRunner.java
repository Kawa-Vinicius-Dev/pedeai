package com.pedeai.agent;

import com.pedeai.agent.ApiClient.Job;
import com.pedeai.agent.ApiClient.PrinterConfig;
import com.pedeai.agent.ApiClient.PrinterStatus;

import javax.print.PrintException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O agente rodando: busca a fila, imprime e confirma; manda o heartbeat com o status das impressoras
 * (docs/04-impressao.md#o-agente). O diário garante que a mesma chave de entrega nunca sai duas vezes.
 */
final class AgentRunner {
    // ponytail: consulta a fila a cada 2 s; o aviso em tempo real (SSE) do servidor fica para quando houver muitas lojas.
    static final Duration POLL = Duration.ofSeconds(2);
    static final Duration HEARTBEAT = Duration.ofSeconds(20);
    static final Duration CONFIG_REFRESH = Duration.ofMinutes(1);
    static final String UNCERTAIN = "O agente parou no meio desta impressão. Confira se saiu papel antes de imprimir de novo.";
    private static final int PROBE_TIMEOUT_MS = 3_000;
    /** Algumas impressoras aceitam uma conexão por vez: só marca offline depois de 2 falhas seguidas. */
    private static final int FAILURES_FOR_OFFLINE = 2;

    private final ApiClient api;
    private final Journal journal;
    private final Clock clock;
    private final StatusTray tray;
    private boolean updateNotified;
    private final Map<UUID, PrinterConfig> printers = new HashMap<>();
    private final Map<UUID, PrinterStatus> statuses = new HashMap<>();
    private final Map<UUID, Integer> probeFailures = new HashMap<>();

    AgentRunner(ApiClient api, Journal journal, Clock clock) {
        this(api, journal, clock, StatusTray.none());
    }

    AgentRunner(ApiClient api, Journal journal, Clock clock, StatusTray tray) {
        this.api = api;
        this.journal = journal;
        this.clock = clock;
        this.tray = tray;
    }

    void refreshConfig() throws IOException, InterruptedException {
        ApiClient.Config config = api.config();
        printers.clear();
        config.printers().forEach(printer -> printers.put(printer.id(), printer));
        if (!updateNotified && isNewer(config.latestVersion(), ApiClient.VERSION)) {
            updateNotified = true;
            String where = config.downloadUrl() == null ? "Peça o instalador novo a quem cuida do sistema."
                    : "Baixe em " + config.downloadUrl();
            log("Versão nova do agente disponível: " + config.latestVersion() + ". " + where);
            tray.notify("PedeAí: agente desatualizado", "Versão " + config.latestVersion() + " disponível. " + where);
        }
    }

    /** Estado para a bandeja: sem conexão, impressora com problema, ou tudo certo. */
    StatusTray.Status status(boolean connected) {
        if (!connected) {
            return StatusTray.Status.OFFLINE;
        }
        return statuses.values().stream().anyMatch(status -> printers.containsKey(status.printerId())
                && !"ONLINE".equals(status.status())) ? StatusTray.Status.PRINTER_PROBLEM : StatusTray.Status.OK;
    }

    private void showStatus(boolean connected) {
        StatusTray.Status status = status(connected);
        String tooltip = switch (status) {
            case OFFLINE -> "PedeAí: sem conexão. Os pedidos esperam na fila.";
            case PRINTER_PROBLEM -> "PedeAí: impressora com problema: " + statuses.values().stream()
                    .filter(printerStatus -> !"ONLINE".equals(printerStatus.status()))
                    .map(printerStatus -> printers.containsKey(printerStatus.printerId())
                            ? printers.get(printerStatus.printerId()).name() : "?")
                    .reduce((a, b) -> a + ", " + b).orElse("");
            case OK -> "PedeAí: imprimindo (" + printers.size() + (printers.size() == 1 ? " impressora)" : " impressoras)");
        };
        tray.show(status, tooltip);
    }

    /** "0.2.10" é mais nova que "0.2.9". Sem versão informada, não avisa. */
    static boolean isNewer(String candidate, String current) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        String[] a = candidate.trim().split("[.]");
        String[] b = current.trim().split("[.]");
        try {
            for (int i = 0; i < Math.max(a.length, b.length); i++) {
                int left = i < a.length ? Integer.parseInt(a[i]) : 0;
                int right = i < b.length ? Integer.parseInt(b[i]) : 0;
                if (left != right) {
                    return left > right;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }

    /** Uma passada pela fila. Devolve quantos trabalhos saíram no papel. */
    int processJobs() throws IOException, InterruptedException {
        int printed = 0;
        for (Job job : api.jobs()) {
            if (!reserve(job)) {
                continue;
            }
            Journal.State previous = journal.state(job.deliveryKey());
            if (previous == Journal.State.PRINTED) {
                // Já saiu papel; a confirmação anterior é que se perdeu.
                api.update(job.id(), "PRINTED", null);
                continue;
            }
            if (previous == Journal.State.RECEIVED) {
                api.update(job.id(), "UNCERTAIN", UNCERTAIN);
                continue;
            }
            PrinterConfig printer = printers.get(job.printerId());
            if (printer == null) {
                refreshConfig();
                printer = printers.get(job.printerId());
            }
            if (printer == null) {
                api.update(job.id(), "FAILED", "Impressora não está configurada neste computador.");
                continue;
            }
            journal.received(job.deliveryKey(), Instant.now(clock));
            try {
                send(printer, job.payload());
            } catch (IOException | PrintException e) {
                journal.failed(job.deliveryKey(), Instant.now(clock));
                report(printer, "OFFLINE", e.getMessage());
                api.update(job.id(), "FAILED", e.getMessage());
                continue;
            }
            journal.printed(job.deliveryKey(), Instant.now(clock));
            report(printer, "ONLINE", null);
            api.update(job.id(), "PRINTED", null);
            printed++;
        }
        return printed;
    }

    /** Heartbeat com o status de cada impressora. Só testa a conexão agora, com a fila parada. */
    void heartbeat() throws IOException, InterruptedException {
        List<String> installed = Printers.installed();
        for (PrinterConfig printer : printers.values()) {
            if (printer.network()) {
                probe(printer);
            } else if (installed.stream().anyMatch(name -> name.equalsIgnoreCase(printer.systemName()))) {
                // Instalada no Windows: volta a ONLINE. Uma falha de envio vale só até o próximo heartbeat; senão, com
                // reserva configurada, a principal nunca mais recebe trabalho para mostrar que voltou.
                report(printer, "ONLINE", null);
            } else {
                report(printer, "ERROR", "Impressora \"" + printer.systemName() + "\" não encontrada no Windows.");
            }
        }
        api.heartbeat(new ArrayList<>(statuses.values().stream()
                .filter(status -> printers.containsKey(status.printerId())).toList()));
    }

    /** Roda até o computador ser removido na tela (401). Sem internet, espera e tenta de novo. */
    void run() throws InterruptedException {
        Instant nextHeartbeat = Instant.EPOCH;
        Instant nextConfig = Instant.EPOCH;
        while (!Thread.currentThread().isInterrupted()) {
            Instant now = Instant.now(clock);
            try {
                if (!now.isBefore(nextConfig)) {
                    refreshConfig();
                    nextConfig = now.plus(CONFIG_REFRESH);
                }
                int printed = processJobs();
                if (printed > 0) {
                    log(printed + (printed == 1 ? " impressão enviada." : " impressões enviadas."));
                }
                if (!now.isBefore(nextHeartbeat)) {
                    heartbeat();
                    nextHeartbeat = now.plus(HEARTBEAT);
                }
                showStatus(true);
            } catch (ApiClient.ApiException e) {
                if (e.status == 401) {
                    log("Este computador foi removido na tela de impressão. Pareie de novo para voltar a imprimir.");
                    return;
                }
                log("Erro na API: " + e.getMessage());
            } catch (IOException e) {
                log("Sem conexão com o PedeAí (" + e.getMessage() + "). Tentando de novo.");
                showStatus(false);
            }
            Thread.sleep(POLL.toMillis());
        }
    }

    private boolean reserve(Job job) throws IOException, InterruptedException {
        try {
            api.update(job.id(), "SENT", null);
            return true;
        } catch (ApiClient.ApiException e) {
            if (e.status == 409) {
                return false; // outra passada já pegou, ou saiu da fila
            }
            throw e;
        }
    }

    private void send(PrinterConfig printer, byte[] payload) throws IOException, PrintException {
        if (printer.network()) {
            Printers.sendToNetwork(printer.host(), printer.port(), payload);
        } else {
            Printers.sendToSystemPrinter(printer.systemName(), payload);
        }
    }

    private void probe(PrinterConfig printer) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(printer.host(), printer.port()), PROBE_TIMEOUT_MS);
            probeFailures.remove(printer.id());
            report(printer, "ONLINE", null);
        } catch (IOException e) {
            int failures = probeFailures.merge(printer.id(), 1, Integer::sum);
            if (failures >= FAILURES_FOR_OFFLINE) {
                report(printer, "OFFLINE", "Sem resposta em " + printer.host() + ":" + printer.port() + ".");
            }
        }
    }

    private void report(PrinterConfig printer, String status, String detail) {
        statuses.put(printer.id(), new PrinterStatus(printer.id(), status, detail));
    }

    private void log(String message) {
        System.out.println(Instant.now(clock) + " " + message);
    }
}
