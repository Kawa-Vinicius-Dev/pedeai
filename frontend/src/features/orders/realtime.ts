import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { subscribeToStream } from '../../shared/api/stream';
import { orderKeys } from './api';

export interface OrderEvent {
  type: 'order.created' | 'order.status_changed';
  orderId: string;
  number: number;
  status: string;
  version: number;
}

type Listener = (event: OrderEvent) => void;
const listeners = new Set<Listener>();

/** Para quem quer reagir ao aviso além de recarregar (o som do quadro de pedidos). */
export function onOrderEvent(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/**
 * Uma conexão de tempo real por aba, aberta enquanto a pessoa está logada. Cada aviso de pedido faz as
 * listas e o detalhe recarregarem pelo REST (o aviso não traz o pedido).
 */
export function useOrderStream(enabled: boolean) {
  const queryClient = useQueryClient();
  useEffect(() => {
    if (!enabled) {
      return undefined;
    }
    return subscribeToStream(
      (event) => {
        if (!event.type.startsWith('order.')) {
          return;
        }
        void queryClient.invalidateQueries({ queryKey: orderKeys.all });
        try {
          const parsed = { ...(JSON.parse(event.data) as Omit<OrderEvent, 'type'>), type: event.type } as OrderEvent;
          listeners.forEach((listener) => listener(parsed));
        } catch {
          // Aviso malformado: a lista já foi recarregada, o resto não importa.
        }
      },
      () => void queryClient.invalidateQueries({ queryKey: orderKeys.all }),
    );
  }, [enabled, queryClient]);
}
