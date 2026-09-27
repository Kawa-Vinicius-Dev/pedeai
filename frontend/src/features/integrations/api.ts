import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { MarketplaceConnection } from '../../shared/api/types';
import { orderKeys } from '../orders/api';

export const integrationKeys = {
  all: ['integrations'] as const,
  setup: ['integrations', 'setup'] as const,
  connections: ['integrations', 'connections'] as const,
  merchants: ['integrations', 'merchants'] as const,
  sync: (orderId: string) => ['integrations', 'sync', orderId] as const,
  reasons: (orderId: string) => ['integrations', 'reasons', orderId] as const,
};

export function useIfoodSetup() {
  return useQuery({ queryKey: integrationKeys.setup, queryFn: () => unwrap(api.GET('/api/integrations/ifood/setup')) });
}

/** A saúde do vínculo muda com o polling (30 s): recarregar mais que isso não mostra nada novo. */
export function useConnections() {
  return useQuery({
    queryKey: integrationKeys.connections,
    queryFn: () => unwrap(api.GET('/api/integrations')),
    refetchInterval: 30_000,
  });
}

export function useMerchants(enabled: boolean) {
  return useQuery({
    queryKey: integrationKeys.merchants,
    queryFn: () => unwrap(api.GET('/api/integrations/ifood/merchants')),
    enabled,
  });
}

export function useUpdateConnection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status, autoConfirm }: Pick<MarketplaceConnection, 'id' | 'status' | 'autoConfirm'>) =>
      unwrap(api.PATCH('/api/integrations/{id}', { params: { path: { id } }, body: { status, autoConfirm } })),
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: integrationKeys.connections }),
  });
}

export function useSimulateOrder() {
  return useMutation({
    mutationFn: (id: string) =>
      unwrap(api.POST('/api/integrations/{id}/simulated-orders', { params: { path: { id } } })),
    onSuccess: () =>
      notifications.show({ color: 'green', message: 'Pedido simulado do iFood enviado. Ele aparece no quadro em instantes.' }),
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
  });
}

/** Enquanto houver ação pendente, confere a cada 3 s: a confirmação do iFood chega sozinha. */
export function useMarketplaceSync(orderId: string) {
  return useQuery({
    queryKey: integrationKeys.sync(orderId),
    queryFn: () => unwrap(api.GET('/api/orders/{orderId}/marketplace/sync', { params: { path: { orderId } } })),
    refetchInterval: (query) => (query.state.data?.some((action) => action.status === 'PENDING') ? 3_000 : false),
  });
}

export function useCancellationReasons(orderId: string, enabled: boolean) {
  return useQuery({
    queryKey: integrationKeys.reasons(orderId),
    queryFn: () =>
      unwrap(api.GET('/api/orders/{orderId}/marketplace/cancellation-reasons', { params: { path: { orderId } } })),
    enabled,
    staleTime: 0,
  });
}

export function useRequestCancellation(orderId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { code: string; description: string }) =>
      unwrap(api.POST('/api/orders/{orderId}/marketplace/cancellation', { params: { path: { orderId } }, body })),
    onSuccess: () =>
      notifications.show({
        color: 'green',
        message: 'Cancelamento solicitado ao iFood. O pedido é cancelado quando o iFood confirmar.',
      }),
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: integrationKeys.sync(orderId) });
      void queryClient.invalidateQueries({ queryKey: orderKeys.all });
    },
  });
}

export function useRetryAction(orderId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => unwrap(api.POST('/api/marketplace-actions/{id}/retry', { params: { path: { id } } })),
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: integrationKeys.sync(orderId) }),
  });
}

export const ACTION_LABELS: Record<string, string> = {
  CONFIRM: 'Aceite',
  START_PREPARATION: 'Início do preparo',
  READY: 'Pronto',
  DISPATCH: 'Saiu para entrega',
  REQUEST_CANCELLATION: 'Pedido de cancelamento',
};
