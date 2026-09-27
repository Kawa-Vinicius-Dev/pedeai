package com.pedeai.printing.service;

import com.pedeai.catalog.dto.SectorResponse;
import com.pedeai.catalog.service.SectorService;
import com.pedeai.printing.domain.Codepage;
import com.pedeai.printing.domain.ConnectionType;
import com.pedeai.printing.domain.CutMode;
import com.pedeai.printing.domain.Printer;
import com.pedeai.printing.domain.SectorPrinter;
import com.pedeai.printing.dto.PrinterRequest;
import com.pedeai.printing.dto.PrinterResponse;
import com.pedeai.printing.dto.SectorPrinterRequest;
import com.pedeai.printing.repository.PrinterRepository;
import com.pedeai.printing.repository.SectorPrinterRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pedeai.printing.PrintingFixtures.AGENT_ID;
import static com.pedeai.printing.PrintingFixtures.KITCHEN;
import static com.pedeai.printing.PrintingFixtures.printer;
import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrinterServiceTest {
    @Mock
    private PrinterRepository printers;
    @Mock
    private SectorPrinterRepository sectorPrinters;
    @Mock
    private AgentService agents;
    @Mock
    private SectorService sectors;

    private PrinterService service;

    @BeforeEach
    void setUp() {
        service = new PrinterService(printers, sectorPrinters, agents, sectors, CLOCK);
        when(printers.save(any())).then(returnsFirstArg());
        when(sectorPrinters.save(any())).then(returnsFirstArg());
        when(agents.onlineAgentIds(STORE_ID)).thenReturn(List.of(AGENT_ID));
        when(sectors.get(STORE_ID, KITCHEN)).thenReturn(new SectorResponse(KITCHEN, "Cozinha", true, true));
    }

    private static PrinterRequest request(ConnectionType type, String host, String systemName, int paper) {
        return new PrinterRequest(AGENT_ID, " Cozinha ", type, host, 9100, systemName, paper, 48, Codepage.PC860,
                CutMode.PARTIAL, true);
    }

    @Test
    void networkPrinterKeepsOnlyTheAddress() {
        PrinterResponse created = service.create(STORE_ID,
                request(ConnectionType.NETWORK, " 192.168.0.50 ", "sobra", 80));

        assertThat(created.name()).isEqualTo("Cozinha");
        assertThat(created.host()).isEqualTo("192.168.0.50");
        assertThat(created.systemName()).isNull();
        assertThat(created.status().name()).isEqualTo("UNKNOWN");
    }

    @Test
    void validatesConnectionPaperAndName() {
        assertThatThrownBy(() -> service.create(STORE_ID, request(ConnectionType.NETWORK, " ", null, 80)))
                .hasMessage(PrinterService.NETWORK_NEEDS_ADDRESS);
        assertThatThrownBy(() -> service.create(STORE_ID, request(ConnectionType.SYSTEM, null, "", 80)))
                .hasMessage(PrinterService.SYSTEM_NEEDS_NAME);
        assertThatThrownBy(() -> service.create(STORE_ID, request(ConnectionType.SYSTEM, null, "ELGIN", 70)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(PrinterService.INVALID_PAPER);

        when(printers.existsByStoreIdAndNameIgnoreCaseAndIdNot(eq(STORE_ID), eq("Cozinha"), any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(STORE_ID, request(ConnectionType.SYSTEM, null, "ELGIN", 80)))
                .isInstanceOf(ConflictException.class);
        verify(printers, never()).save(any());
    }

    @Test
    void agentOfAnotherStoreIsNotFound() {
        doThrow(new ResourceNotFoundException(AgentService.NOT_FOUND)).when(agents).ensureExists(STORE_ID, AGENT_ID);

        assertThatThrownBy(() -> service.create(STORE_ID, request(ConnectionType.NETWORK, "10.0.0.5", null, 80)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void printerOfAnOfflineAgentShowsOffline() {
        Printer printer = printer("Cozinha");
        when(printers.findAllByStoreIdOrderByNameAsc(STORE_ID)).thenReturn(List.of(printer));
        when(agents.onlineAgentIds(STORE_ID)).thenReturn(List.of());

        assertThat(service.list(STORE_ID).getFirst().status().name()).isEqualTo("OFFLINE");
    }

    @Test
    void assignsSectorWithBackupAndRefusesTheSameOneTwice() {
        Printer main = printer("Cozinha");
        Printer backup = printer("Caixa");
        when(printers.findByIdAndStoreId(main.getId(), STORE_ID)).thenReturn(Optional.of(main));
        when(printers.findByIdAndStoreId(backup.getId(), STORE_ID)).thenReturn(Optional.of(backup));
        when(sectorPrinters.findBySectorIdAndStoreId(KITCHEN, STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignToSector(STORE_ID, KITCHEN,
                new SectorPrinterRequest(main.getId(), main.getId(), 1, true)))
                .hasMessage(PrinterService.SAME_BACKUP);

        var saved = service.assignToSector(STORE_ID, KITCHEN,
                new SectorPrinterRequest(main.getId(), backup.getId(), 2, true));
        assertThat(saved.backupPrinterId()).isEqualTo(backup.getId());
        assertThat(saved.copies()).isEqualTo(2);
        verify(sectorPrinters).save(any(SectorPrinter.class));
    }

    @Test
    void printerOfAnotherStoreCannotServeTheSector() {
        UUID foreign = UUID.randomUUID();
        when(printers.findByIdAndStoreId(foreign, STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignToSector(STORE_ID, KITCHEN,
                new SectorPrinterRequest(foreign, null, 1, true)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(printers, never()).existsByStoreIdAndNameIgnoreCaseAndIdNot(any(), anyString(), any());
    }
}
