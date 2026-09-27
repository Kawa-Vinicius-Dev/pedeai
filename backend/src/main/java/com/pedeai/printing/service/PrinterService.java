package com.pedeai.printing.service;

import com.pedeai.catalog.service.SectorService;
import com.pedeai.printing.domain.ConnectionType;
import com.pedeai.printing.domain.Printer;
import com.pedeai.printing.domain.SectorPrinter;
import com.pedeai.printing.dto.PrinterRequest;
import com.pedeai.printing.dto.PrinterResponse;
import com.pedeai.printing.dto.SectorPrinterRequest;
import com.pedeai.printing.dto.SectorPrinterResponse;
import com.pedeai.printing.repository.PrinterRepository;
import com.pedeai.printing.repository.SectorPrinterRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.text.Texts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Impressoras da loja e a impressora de cada setor. */
@Service
public class PrinterService {
    static final String NOT_FOUND = "Impressora não encontrada.";
    static final String DUPLICATE_NAME = "Já existe uma impressora com esse nome.";
    static final String NETWORK_NEEDS_ADDRESS = "Impressora de rede precisa do IP e da porta (em geral, 9100).";
    static final String SYSTEM_NEEDS_NAME = "Impressora USB precisa do nome exato dela no Windows.";
    static final String INVALID_PAPER = "O papel é de 58 ou 80mm.";
    static final String SAME_BACKUP = "A impressora reserva precisa ser outra.";

    private final PrinterRepository printerRepository;
    private final SectorPrinterRepository sectorPrinterRepository;
    private final AgentService agentService;
    private final SectorService sectorService;
    private final Clock clock;

    public PrinterService(PrinterRepository printerRepository, SectorPrinterRepository sectorPrinterRepository,
                          AgentService agentService, SectorService sectorService, Clock clock) {
        this.printerRepository = printerRepository;
        this.sectorPrinterRepository = sectorPrinterRepository;
        this.agentService = agentService;
        this.sectorService = sectorService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PrinterResponse> list(UUID storeId) {
        Set<UUID> online = Set.copyOf(agentService.onlineAgentIds(storeId));
        return printerRepository.findAllByStoreIdOrderByNameAsc(storeId).stream()
                .map(printer -> PrinterResponse.from(printer, online.contains(printer.getAgentId())))
                .toList();
    }

    @Transactional
    public PrinterResponse create(UUID storeId, PrinterRequest request) {
        Printer printer = new Printer(storeId, Instant.now(clock));
        configure(storeId, printer, request);
        return PrinterResponse.from(printerRepository.save(printer), isOnline(storeId, printer));
    }

    @Transactional
    public PrinterResponse update(UUID storeId, UUID id, PrinterRequest request) {
        Printer printer = find(storeId, id);
        configure(storeId, printer, request);
        return PrinterResponse.from(printer, isOnline(storeId, printer));
    }

    @Transactional(readOnly = true)
    public List<SectorPrinterResponse> listSectorPrinters(UUID storeId) {
        return sectorPrinterRepository.findAllByStoreId(storeId).stream().map(SectorPrinterResponse::from).toList();
    }

    @Transactional
    public SectorPrinterResponse assignToSector(UUID storeId, UUID sectorId, SectorPrinterRequest request) {
        sectorService.get(storeId, sectorId);
        find(storeId, request.printerId());
        if (request.backupPrinterId() != null) {
            if (request.backupPrinterId().equals(request.printerId())) {
                throw new BusinessRuleException(SAME_BACKUP);
            }
            find(storeId, request.backupPrinterId());
        }
        SectorPrinter sectorPrinter = sectorPrinterRepository.findBySectorIdAndStoreId(sectorId, storeId)
                .orElseGet(() -> new SectorPrinter(sectorId, storeId));
        sectorPrinter.configure(request.printerId(), request.backupPrinterId(), request.copies(), request.enabled());
        return SectorPrinterResponse.from(sectorPrinterRepository.save(sectorPrinter));
    }

    private void configure(UUID storeId, Printer printer, PrinterRequest request) {
        agentService.ensureExists(storeId, request.agentId());
        String name = request.name().trim();
        if (printerRepository.existsByStoreIdAndNameIgnoreCaseAndIdNot(storeId, name, printer.getId())) {
            throw new ConflictException(DUPLICATE_NAME);
        }
        if (request.paperWidthMm() != 58 && request.paperWidthMm() != 80) {
            throw new BusinessRuleException(INVALID_PAPER);
        }
        String host = Texts.trimToNull(request.host());
        String systemName = Texts.trimToNull(request.systemName());
        if (request.connectionType() == ConnectionType.NETWORK && (host == null || request.port() == null)) {
            throw new BusinessRuleException(NETWORK_NEEDS_ADDRESS);
        }
        if (request.connectionType() == ConnectionType.SYSTEM && systemName == null) {
            throw new BusinessRuleException(SYSTEM_NEEDS_NAME);
        }
        printer.configure(request.agentId(), name, request.connectionType(), host, request.port(), systemName,
                request.paperWidthMm(), request.columns(), request.codepage(), request.cutMode(), request.active(),
                Instant.now(clock));
    }

    private boolean isOnline(UUID storeId, Printer printer) {
        return agentService.onlineAgentIds(storeId).stream().anyMatch(id -> Objects.equals(id, printer.getAgentId()));
    }

    private Printer find(UUID storeId, UUID id) {
        return printerRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }
}
