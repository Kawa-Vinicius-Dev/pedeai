import type { OrderStatus, OrderType, PaymentMethodType, Role } from '../../shared/api/types';

export const STATUS_LABELS: Record<OrderStatus, string> = {
  RECEIVED: 'Recebido',
  CONFIRMED: 'Confirmado',
  IN_PREPARATION: 'Em preparo',
  READY: 'Pronto',
  DISPATCHED: 'Saiu para entrega',
  COMPLETED: 'Concluído',
  CANCELLED: 'Cancelado',
};

export const STATUS_COLORS: Record<OrderStatus, string> = {
  RECEIVED: 'red',
  CONFIRMED: 'blue',
  IN_PREPARATION: 'orange',
  READY: 'green',
  DISPATCHED: 'grape',
  COMPLETED: 'gray',
  CANCELLED: 'dark',
};

export const TYPE_LABELS: Record<OrderType, string> = {
  TAKEOUT: 'Retirada',
  DELIVERY: 'Delivery',
  DINE_IN: 'Mesa',
};

export const PAYMENT_TYPE_LABELS: Record<PaymentMethodType, string> = {
  CASH: 'Dinheiro',
  PIX: 'Pix',
  CREDIT: 'Crédito',
  DEBIT: 'Débito',
  VOUCHER: 'Vale-refeição',
  ONLINE: 'Online (marketplace)',
  OTHER: 'Outro',
};

export interface StatusAction {
  status: OrderStatus;
  label: string;
}

const KITCHEN_TARGETS: OrderStatus[] = ['IN_PREPARATION', 'READY'];

/** O próximo passo de cada status, na ordem do botão principal. A cozinha só vê "iniciar" e "pronto". */
export function nextActions(order: { status: OrderStatus; type: OrderType }, role: Role): StatusAction[] {
  const actions: StatusAction[] = (() => {
    switch (order.status) {
      case 'RECEIVED':
        return [{ status: 'CONFIRMED', label: 'Confirmar' }];
      case 'CONFIRMED':
        return [
          { status: 'IN_PREPARATION', label: 'Iniciar preparo' },
          { status: 'READY', label: 'Pronto' },
        ];
      case 'IN_PREPARATION':
        return [{ status: 'READY', label: 'Pronto' }];
      case 'READY':
        return order.type === 'DELIVERY'
          ? [{ status: 'DISPATCHED', label: 'Saiu para entrega' }]
          : [{ status: 'COMPLETED', label: 'Concluir' }];
      case 'DISPATCHED':
        return [{ status: 'COMPLETED', label: 'Entregue' }];
      default:
        return [];
    }
  })();
  return role === 'KITCHEN' ? actions.filter((action) => KITCHEN_TARGETS.includes(action.status)) : actions;
}

/** iFood e 99Food: status e cancelamento passam pela plataforma. Balcão e cardápio digital são da própria loja. */
export function isMarketplace(source: string): boolean {
  return source === 'IFOOD' || source === 'NINETY_NINE_FOOD';
}

/** Mesmas regras do backend (OrderStatusService): a API é quem barra de verdade. */
export function canCancel(order: { status: OrderStatus; source: string }, role: Role): boolean {
  if (order.status === 'CANCELLED' || role === 'KITCHEN' || role === 'WAITER') {
    return false;
  }
  // Pedido de marketplace em andamento é cancelado pela plataforma: ver canRequestMarketplaceCancel.
  if (isMarketplace(order.source) && order.status !== 'COMPLETED') {
    return false;
  }
  const manager = role === 'OWNER' || role === 'MANAGER';
  if (order.status === 'COMPLETED') {
    return manager && !isMarketplace(order.source);
  }
  const preparationStarted = order.status === 'IN_PREPARATION' || order.status === 'READY' || order.status === 'DISPATCHED';
  return !preparationStarted || manager;
}

/** Pedido do iFood em andamento: o cancelamento é pedido à plataforma, com um motivo dela. */
export function canRequestMarketplaceCancel(order: { status: OrderStatus; source: string }, role: Role): boolean {
  const final = order.status === 'CANCELLED' || order.status === 'COMPLETED';
  return isMarketplace(order.source) && !final && ['OWNER', 'MANAGER', 'CASHIER'].includes(role);
}

export const SOURCE_LABELS: Record<string, string> = {
  PEDEAI: 'Balcão/telefone',
  DIGITAL_MENU: 'Cardápio',
  IFOOD: 'iFood',
  NINETY_NINE_FOOD: '99Food',
};

/** "iFood 7391": o número que o cliente e o entregador veem no aplicativo. */
export function sourceBadge(order: { source: string; externalDisplayId: string | null }): string | null {
  if (order.source === 'PEDEAI') {
    return null;
  }
  return [SOURCE_LABELS[order.source] ?? order.source, order.externalDisplayId].filter(Boolean).join(' ');
}

/** "Pedido 12" — o número recomeça a cada dia operacional. */
export function orderTitle(order: { number: number }): string {
  return `Pedido ${order.number}`;
}

/** Minutos desde um instante, para o cronômetro do quadro. */
export function minutesSince(iso: string, now: number): number {
  return Math.max(0, Math.floor((now - new Date(iso).getTime()) / 60_000));
}

/** "+5511999990000" → "(11) 99999-0000". Outros formatos ficam como vieram. */
export function formatPhone(phone: string | null): string {
  if (!phone) {
    return '';
  }
  const match = /^\+55(\d{2})(\d{4,5})(\d{4})$/.exec(phone);
  return match ? `(${match[1]}) ${match[2]}-${match[3]}` : phone;
}
