package com.pedeai.agent;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Encoder ESC/POS mínimo: só os comandos que os tickets usam (docs/04-impressao.md#escpos-na-prática).
 * O texto sai na tabela de caracteres escolhida; o que não existe nela vira "?".
 */
final class EscPos {
    private static final int ESC = 0x1B;
    private static final int GS = 0x1D;
    private static final int LF = 0x0A;

    enum Align {
        LEFT, CENTER, RIGHT
    }

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private Charset charset = StandardCharsets.US_ASCII;

    /** ESC @: volta a impressora ao estado inicial (fonte normal, alinhado à esquerda, tabela padrão). */
    EscPos init() {
        charset = StandardCharsets.US_ASCII;
        return bytes(ESC, '@');
    }

    /** ESC t n: o número {@code n} de cada tabela muda de fabricante para fabricante; a página de teste descobre. */
    EscPos codepage(int n, Charset charset) {
        this.charset = charset;
        return bytes(ESC, 't', n);
    }

    EscPos align(Align align) {
        return bytes(ESC, 'a', align.ordinal());
    }

    EscPos bold(boolean on) {
        return bytes(ESC, 'E', on ? 1 : 0);
    }

    /** GS ! n: largura e altura de 1 a 8 vezes. Fonte dupla é {@code size(2, 2)}. */
    EscPos size(int width, int height) {
        if (width < 1 || width > 8 || height < 1 || height > 8) {
            throw new IllegalArgumentException("Tamanho vai de 1 a 8: " + width + "x" + height);
        }
        return bytes(GS, '!', (width - 1) << 4 | (height - 1));
    }

    EscPos text(String text) {
        out.writeBytes(text.getBytes(charset));
        return this;
    }

    EscPos line(String text) {
        return text(text).bytes(LF);
    }

    /** ESC d n: avança {@code n} linhas. */
    EscPos feed(int lines) {
        return bytes(ESC, 'd', lines);
    }

    /** Avança o papel até passar da lâmina e corta (GS V 0 total, GS V 1 parcial). */
    EscPos cut(boolean partial) {
        return feed(4).bytes(GS, 'V', partial ? 1 : 0);
    }

    byte[] toBytes() {
        return out.toByteArray();
    }

    private EscPos bytes(int... values) {
        for (int value : values) {
            out.write(value);
        }
        return this;
    }
}
