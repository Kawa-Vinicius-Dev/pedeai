package com.pedeai.printing.service;

import com.pedeai.printing.domain.Codepage;
import com.pedeai.printing.domain.CutMode;
import com.pedeai.printing.domain.DocumentType;
import com.pedeai.printing.dto.TicketLineResponse;
import com.pedeai.printing.dto.TicketLineResponse.Align;
import com.pedeai.printing.dto.TicketResponse;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EscPosRendererTest {
    private static final TicketResponse TICKET = new TicketResponse(DocumentType.PRODUCTION_TICKET, 32, List.of(
            new TicketLineResponse("PEDIDO", Align.CENTER, false, true),
            new TicketLineResponse("Pão", Align.LEFT, true, false)));

    @Test
    void writesTheSameCommandsAsTheAgentTestPage() {
        byte[] bytes = EscPosRenderer.render(TICKET, Codepage.PC860, CutMode.PARTIAL, 1);

        assertThat(bytes).containsExactly(
                0x1B, '@', 0x1B, 't', 3,                                  // inicializa e escolhe PC860
                0x1B, 'a', 1, 0x1B, 'E', 0, 0x1D, '!', 0x11,              // centro, sem negrito, fonte dupla
                'P', 'E', 'D', 'I', 'D', 'O', 0x0A,
                0x1B, 'a', 0, 0x1B, 'E', 1, 0x1D, '!', 0x00,              // esquerda, negrito, fonte normal
                'P', (byte) 0x84, 'o', 0x0A,                              // "ã" em PC860 é 0x84
                0x1B, 'E', 0, 0x1D, '!', 0,
                0x1B, 'd', 4, 0x1D, 'V', 1);                              // avança e corte parcial
    }

    @Test
    void withoutAccentsTheTextStillPrints() {
        byte[] bytes = EscPosRenderer.render(TICKET, Codepage.NO_ACCENTS, CutMode.NONE, 1);

        String text = new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
        assertThat(text).contains("Pao\n").doesNotContain("?");
        assertThat(Arrays.copyOfRange(bytes, bytes.length - 3, bytes.length)).containsExactly(0x1B, 'd', 6);
    }

    @Test
    void eachCopyIsCutSeparately() {
        byte[] one = EscPosRenderer.render(TICKET, Codepage.PC850, CutMode.FULL, 1);
        byte[] two = EscPosRenderer.render(TICKET, Codepage.PC850, CutMode.FULL, 2);

        assertThat(two).hasSize(one.length * 2);
        assertThat(Arrays.copyOfRange(two, one.length, two.length)).isEqualTo(one);
    }
}
