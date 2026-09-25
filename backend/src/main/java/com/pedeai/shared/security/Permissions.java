package com.pedeai.shared.security;

/** Expressões de {@code @PreAuthorize} reaproveitadas. A tabela de papéis está em docs/02-arquitetura.md. */
public final class Permissions {
    public static final String OWNER = "hasRole('OWNER')";
    public static final String MANAGE_CATALOG = "hasAnyRole('OWNER', 'MANAGER')";
    /** Pausar e liberar item durante o serviço: quem está no caixa ou na cozinha também percebe que acabou. */
    public static final String TOGGLE_AVAILABILITY = "hasAnyRole('OWNER', 'MANAGER', 'CASHIER', 'KITCHEN')";

    private Permissions() {
    }
}
