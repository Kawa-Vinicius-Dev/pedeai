import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { OrderStatus, OrderType } from '../../shared/api/types';
import { STATUS_LABELS } from './labels';

export interface OrderFilters {
  businessDate: string | null;
  status: OrderStatus | null;
  type: OrderType | null;
  q: string;
  page: number;
}

/** Tudo de pedidos fica sob ['orders']: um aviso do tempo real invalida o conjunto de uma vez. */
export const orderKeys = {
  all: ['orders'] as const,
  active: ['orders', 'active'] as const,
  detail: (id: string) => ['orders', 'detail', id] as const,
  history: (id: string) => ['orders', 'detail', id, 'history'] as const,
  payments: (id: string) => ['orders', 'detail', id, 'payments'] as const,
  page: (filters: OrderFilters) => ['orders', 'page', filters] as const,
};

export const settingsKeys = {
  paymentMethods: ['settings', 'payment-methods'] as const,
  deliveryZones: ['settings', 'delivery-zones'] as const,
};

/** O quadro. O tempo real atualiza na hora; o intervalo é só a rede de segurança se o aviso se perder. */
export function useActiveOrders() {
  return useQuery({
    queryKey: orderKeys.active,
    queryFn: () => unwrap(api.GET('/api/orders/active')),
    refetchInterval: 60_000,
  });
}

export function useOrder(id: string | null) {
  return useQuery({
    queryKey: orderKeys.detail(id ?? ''),
    queryFn: () => unwrap(api.GET('/api/orders/{id}', { params: { path: { id: id ?? '' } } })),
    enabled: id !== null,
  });
}

export function useOrderHistory(id: string | null) {
  return useQuery({
    queryKey: orderKeys.history(id ?? ''),
    queryFn: () => unwrap(api.GET('/api/orders/{id}/history', { params: { path: { id: id ?? '' } } })),
    enabled: id !== null,
  });
}

export function useOrderPayments(id: string | null, enabled = true) {
  return useQuery({
    queryKey: orderKeys.payments(id ?? ''),
    queryFn: () =>
      unwrap(api.GET('/api/orders/{orderId}/payments', { params: { path: { orderId: id ?? '' } } })),
    enabled: id !== null && enabled,
  });
}

export function useOrdersPage(filters: OrderFilters) {
  return useQuery({
    queryKey: orderKeys.page(filters),
    queryFn: () =>
      unwrap(
        api.GET('/api/orders', {
          params: {
            query: {
              businessDate: filters.businessDate ?? undefined,
              status: filters.status ?? undefined,
              type: filters.type ?? undefined,
              q: filters.q.trim() || undefined,
              page: filters.page,
              size: 20,
            },
          },
        }),
      ),
  });
}

export function usePaymentMethods() {
  return useQuery({ queryKey: settingsKeys.paymentMethods, queryFn: () => unwrap(api.GET('/api/payment-methods')) });
}

export function useDeliveryZones() {
  return useQuery({ queryKey: settingsKeys.deliveryZones, queryFn: () => unwrap(api.GET('/api/delivery-zones')) });
}

/** Muda o status. Se outra tela mexeu antes, a API responde 409 e a lista recarrega. */
export function useChangeStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status, reason, version }: { id: string; status: OrderStatus; reason?: string; version: number }) =>
      unwrap(api.PATCH('/api/orders/{id}/status', { params: { path: { id } }, body: { status, reason, version } })),
    onSuccess: (order) => {
      queryClient.setQueryData(orderKeys.detail(order.id), order);
      notifications.show({ color: 'green', message: `Pedido ${order.number}: ${STATUS_LABELS[order.status]}.` });
    },
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: orderKeys.all }),
  });
}
