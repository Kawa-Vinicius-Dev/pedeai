import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { MarketplaceConnection } from '../../shared/api/types';
import { orderKeys } from '../orders/api';

export const integrationKeys = {
  all: ['integrations'] as const,
  platforms: ['integrations', 'platforms'] as const,
  connections: ['integrations', 'connections'] as const,
  merchants: ['integrations', 'merchants'] as const,
  sync: (orderId: string) => ['integrations', 'sync', orderId] as const,
  reasons: (orderId: string) => ['integrations', 'reasons', orderId] as const,
  disputes: ['integrations', 'disputes'] as const,
};

/** iFood, 99Food e o app Open Delivery: quais o servidor tem configurados, ou só no simulador. */
export function usePlatforms() {
  return useQuery({ queryKey: integrationKeys.platforms, queryFn: () => unwrap(api.GET('/api/integrations/platforms')) });
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
    mutationFn: ({
      id,
      status,
      autoConfirm,
      catalogSync,
    }: Pick<MarketplaceConnection, 'id' | 'status' | 'autoConfirm'> & { catalogSync?: boolean }) =>
      unwrap(
        api.PATCH('/api/integrations/{id}', { params: { path: { id } }, body: { status, autoConfirm, catalogSync } }),
      ),
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: integrationKeys.connections }),
  });
}

/** "Enviar tudo agora": horário e cardápio inteiro para o iFood. */
export function useSyncCatalog() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => unwrap(api.POST('/api/integrations/{id}/catalog-sync', { params: { path: { id } } })),
    onSuccess: () =>
      notifications.show({ color: 'green', message: 'Cardápio na fila. Ele chega ao iFood em alguns segundos.' }),
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: integrationKeys.connections }),
  });
}

/** Pedidos de cancelamento do cliente esperando a loja. O prazo é curto: confere a cada 15 s. */
export function useDisputes(enabled: boolean) {
  return useQuery({
    queryKey: integrationKeys.disputes,
    queryFn: () => unwrap(api.GET('/api/marketplace/disputes')),
    refetchInterval: 15_000,
    enabled,
  });
}

export function useAnswerDispute() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, accept, rejectCode }: { id: string; accept: boolean; rejectCode?: string }) =>
      unwrap(api.POST('/api/marketplace/disputes/{id}/answer', { params: { path: { id } }, body: { accept, rejectCode } })),
    onSuccess: (dispute) =>
      notifications.show({
        color: 'green',
        message:
          dispute.status === 'ACCEPTED'
            ? 'Cancelamento aceito. O pedido é cancelado quando o app confirmar.'
            : 'Cancelamento recusado. O pedido continua.',
      }),
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: integrationKeys.disputes });
      void queryClient.invalidateQueries({ queryKey: orderKeys.all });
    },
  });
}

export function useSimulateOrder() {
  return useMutation({
    mutationFn: (id: string) =>
      unwrap(api.POST('/api/integrations/{id}/simulated-orders', { params: { path: { id } } })),
    onSuccess: () =>
      notifications.show({ color: 'green', message: 'Pedido simulado enviado. Ele aparece no quadro em instantes.' }),
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
        message: 'Cancelamento solicitado ao app. O pedido é cancelado quando o app confirmar.',
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
  ACCEPT_DISPUTE: 'Aceite do cancelamento pedido pelo cliente',
  REJECT_DISPUTE: 'Recusa do cancelamento pedido pelo cliente',
};
