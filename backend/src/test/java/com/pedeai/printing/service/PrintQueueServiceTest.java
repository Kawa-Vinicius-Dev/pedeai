package com.pedeai.printing.service;

import com.pedeai.order.service.OrderService;
import com.pedeai.printing.domain.PrintJob;
import com.pedeai.printing.domain.Printer;
import com.pedeai.printing.dto.AgentJobUpdateRequest;
import com.pedeai.printing.dto.AgentJobUpdateRequest.Status;
import com.pedeai.printing.dto.AgentPrincipal;
import com.pedeai.printing.repository.PrintJobRepository;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pedeai.printing.PrintingFixtures.AGENT_ID;
import static com.pedeai.printing.PrintingFixtures.job;
import static com.pedeai.printing.PrintingFixtures.printer;
import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrintQueueServiceTest {
    private static final AgentPrincipal AGENT = new AgentPrincipal(AGENT_ID, STORE_ID);

    @Mock
    private PrintJobRepository jobs;

    private PrintQueueService service;
    private Printer printer;

    @BeforeEach
    void setUp() {
        service = new PrintQueueService(jobs, mock(OrderService.class), CLOCK);
        printer = printer("Cozinha");
    }

    private PrintJob stored(PrintJob.Status status) {
        PrintJob job = job(printer, status);
        when(jobs.findByIdAndAgentId(job.getId(), AGENT_ID)).thenReturn(Optional.of(job));
        return job;
    }

    @Test
    void onlyOneReservationAndARepeatedPrintedIsFine() {
        PrintJob job = stored(PrintJob.Status.PENDING);

        service.update(AGENT, job.getId(), new AgentJobUpdateRequest(Status.SENT, null));
        assertThatThrownBy(() -> service.update(AGENT, job.getId(), new AgentJobUpdateRequest(Status.SENT, null)))
                .isInstanceOf(ConflictException.class);
        service.update(AGENT, job.getId(), new AgentJobUpdateRequest(Status.PRINTED, null));
        service.update(AGENT, job.getId(), new AgentJobUpdateRequest(Status.PRINTED, null));

        assertThat(job.getStatus()).isEqualTo(PrintJob.Status.PRINTED);
    }

    @Test
    void failingOrUncertainNeedsTheReservationFirst() {
        PrintJob job = stored(PrintJob.Status.PENDING);

        assertThatThrownBy(() -> service.update(AGENT, job.getId(), new AgentJobUpdateRequest(Status.FAILED, "x")))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.update(AGENT, job.getId(), new AgentJobUpdateRequest(Status.UNCERTAIN, "x")))
                .isInstanceOf(ConflictException.class);
        assertThat(job.getStatus()).isEqualTo(PrintJob.Status.PENDING);
    }

    @Test
    void failureGoesBackToTheQueue() {
        PrintJob job = stored(PrintJob.Status.SENT);

        service.update(AGENT, job.getId(), new AgentJobUpdateRequest(Status.FAILED, "Conexão recusada"));

        assertThat(job.getStatus()).isEqualTo(PrintJob.Status.PENDING);
        assertThat(job.getLastError()).isEqualTo("Conexão recusada");
    }

    @Test
    void jobOfAnotherAgentIsNotFound() {
        UUID other = UUID.randomUUID();
        when(jobs.findByIdAndAgentId(other, AGENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(AGENT, other, new AgentJobUpdateRequest(Status.SENT, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void maintenanceReleasesStuckReservationsAndExpiresOldJobs() {
        PrintJob stuck = job(printer, PrintJob.Status.SENT);
        PrintJob old = job(printer, PrintJob.Status.PENDING);
        Clock later = Clock.fixed(NOW.plusSeconds(21 * 60), ZoneOffset.UTC);
        service = new PrintQueueService(jobs, mock(OrderService.class), later);
        when(jobs.findAllByStatusAndLeaseUntilBefore(PrintJob.Status.SENT, later.instant())).thenReturn(List.of(stuck));
        when(jobs.findAllByStatusAndExpiresAtBefore(PrintJob.Status.PENDING, later.instant())).thenReturn(List.of(old));

        service.maintain();

        assertThat(stuck.getStatus()).isEqualTo(PrintJob.Status.PENDING);
        assertThat(stuck.getAttempts()).isEqualTo(1);
        assertThat(old.getStatus()).isEqualTo(PrintJob.Status.EXPIRED);
    }
}
