package com.pedeai.agent;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscPosTest {

    @Test
    void writesTheCommandsByteForByte() {
        byte[] bytes = new EscPos().init()
                .align(EscPos.Align.CENTER).bold(true).size(2, 2).line("OK")
                .size(1, 1).bold(false).cut(true)
                .toBytes();

        assertArrayEquals(new byte[]{
                0x1B, '@',             // inicializa
                0x1B, 'a', 1,          // centraliza
                0x1B, 'E', 1,          // negrito
                0x1D, '!', 0x11,       // fonte dupla
                'O', 'K', 0x0A,
                0x1D, '!', 0x00,       // fonte normal
                0x1B, 'E', 0,
                0x1B, 'd', 4,          // avança até a lâmina
                0x1D, 'V', 1           // corte parcial
        }, bytes);
    }

    @Test
    void encodesAccentsInTheChosenCodepage() {
        byte[] bytes = new EscPos().codepage(3, Charset.forName("IBM860")).text("Çã").toBytes();

        // ESC t 3, depois Ç e ã na tabela PC860 (português).
        assertArrayEquals(new byte[]{0x1B, 't', 3, (byte) 0x80, (byte) 0x84}, bytes);
    }

    @Test
    void replacesWhatTheCodepageCannotPrint() {
        assertArrayEquals(new byte[]{'?'}, new EscPos().text("ã").toBytes());
    }

    @Test
    void refusesSizesThePrinterDoesNotHave() {
        assertThrows(IllegalArgumentException.class, () -> new EscPos().size(9, 1));
    }

    @Test
    void testPageTriesEveryCodepageAndEndsWithACut() {
        byte[] page = Main.testPage(32);
        String latin = new String(page, Charset.forName("ISO-8859-1"));

        for (Main.Codepage codepage : Main.CODEPAGES) {
            assertTrue(latin.contains("\u001Bt" + (char) codepage.n() + "n=" + codepage.n() + " "),
                    "falta a linha da tabela " + codepage.label());
        }
        assertTrue(latin.contains(Main.ruler(32) + "\n"));
        assertTrue(latin.endsWith("\u001Bd\u0004\u001DV\u0001"));
    }

    @Test
    void rulerFillsTheLineAndMarksTheLastColumn() {
        assertEquals("---------1---------2---------3-|", Main.ruler(32));
        assertEquals("Acao", Main.withoutAccents("Ação"));
    }
}
