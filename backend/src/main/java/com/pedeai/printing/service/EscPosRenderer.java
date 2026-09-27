package com.pedeai.printing.service;

import com.pedeai.printing.domain.Codepage;
import com.pedeai.printing.domain.CutMode;
import com.pedeai.printing.dto.TicketLineResponse;
import com.pedeai.printing.dto.TicketResponse;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.text.Normalizer;

/**
 * Converte as linhas do ticket em bytes ESC/POS para uma impressora (docs/04-impressao.md#escpos-na-prática).
 * O agente só entrega esses bytes. Os mesmos comandos da página de teste do agente.
 */
final class EscPosRenderer {
    private static final int ESC = 0x1B;
    private static final int GS = 0x1D;
    private static final int LF = 0x0A;

    private EscPosRenderer() {
    }

    /** {@code copies} vezes o documento, cada cópia com o seu corte. */
    static byte[] render(TicketResponse ticket, Codepage codepage, CutMode cutMode, int copies) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Charset charset = Charset.forName(codepage.charset());
        for (int copy = 0; copy < copies; copy++) {
            write(out, ESC, '@', ESC, 't', codepage.escPosNumber());
            for (TicketLineResponse line : ticket.lines()) {
                write(out, ESC, 'a', line.align() == TicketLineResponse.Align.CENTER ? 1 : 0);
                write(out, ESC, 'E', line.bold() ? 1 : 0);
                write(out, GS, '!', line.big() ? 0x11 : 0x00);
                String text = codepage == Codepage.NO_ACCENTS ? withoutAccents(line.text()) : line.text();
                out.writeBytes(text.getBytes(charset));
                write(out, LF);
            }
            write(out, ESC, 'E', 0, GS, '!', 0);
            switch (cutMode) {
                case PARTIAL -> write(out, ESC, 'd', 4, GS, 'V', 1);
                case FULL -> write(out, ESC, 'd', 4, GS, 'V', 0);
                case NONE -> write(out, ESC, 'd', 6);
            }
        }
        return out.toByteArray();
    }

    /** O mesmo texto em linhas simples, para o painel de impressões mostrar o que saiu. */
    static String preview(TicketResponse ticket) {
        StringBuilder preview = new StringBuilder();
        ticket.lines().forEach(line -> preview.append(line.text()).append('\n'));
        return preview.toString();
    }

    static String withoutAccents(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static void write(ByteArrayOutputStream out, int... bytes) {
        for (int value : bytes) {
            out.write(value);
        }
    }
}
