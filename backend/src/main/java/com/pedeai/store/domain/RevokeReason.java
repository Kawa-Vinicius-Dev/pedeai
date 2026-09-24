package com.pedeai.store.domain;

public enum RevokeReason {
    /** Trocado por um token novo na renovação. */
    ROTATED,
    /** A pessoa saiu. */
    LOGOUT,
    /** Derrubado por segurança: reuso suspeito, usuário desativado ou senha trocada. */
    REVOKED
}
