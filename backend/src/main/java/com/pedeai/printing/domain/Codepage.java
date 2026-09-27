package com.pedeai.printing.domain;

/**
 * As opções da página de teste do agente: o número {@code n} do comando ESC t e a tabela Java que codifica o texto.
 * A loja escolhe a linha que imprimiu os acentos certos. {@link #NO_ACCENTS} tira os acentos antes de imprimir.
 */
public enum Codepage {
    PC437(0, "IBM437"),
    PC850(2, "IBM850"),
    PC860(3, "IBM860"),
    WPC1252(16, "windows-1252"),
    PC858(19, "IBM00858"),
    NO_ACCENTS(0, "US-ASCII");

    private final int escPosNumber;
    private final String charset;

    Codepage(int escPosNumber, String charset) {
        this.escPosNumber = escPosNumber;
        this.charset = charset;
    }

    public int escPosNumber() {
        return escPosNumber;
    }

    public String charset() {
        return charset;
    }
}
