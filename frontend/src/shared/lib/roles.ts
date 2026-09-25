import type { Role } from '../api/types';

export const ROLE_LABELS: Record<Role, string> = {
  OWNER: 'Dono',
  MANAGER: 'Gerente',
  CASHIER: 'Caixa',
  WAITER: 'Garçom',
  KITCHEN: 'Cozinha',
};

export const ROLE_OPTIONS = (Object.keys(ROLE_LABELS) as Role[]).map((value) => ({
  value,
  label: ROLE_LABELS[value],
}));

/** Mesmas regras de Permissions.java no backend. A API é quem barra de verdade (403). */
export const CATALOG_MANAGERS: Role[] = ['OWNER', 'MANAGER'];

/** Pausar e liberar item durante o serviço. */
export const AVAILABILITY_TOGGLERS: Role[] = ['OWNER', 'MANAGER', 'CASHIER', 'KITCHEN'];
