import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { CashSession } from '../../shared/api/types';

export const cashKeys = {
  all: ['cash'] as const,
  current: ['cash', 'current'] as const,
  history: ['cash', 'history'] as const,
  session: (id: string) => ['cash', 'session', id] as const,
};

/** O caixa aberto, ou null. O esperado muda a cada pagamento: atualiza sozinho a cada 30 s. */
export function useCurrentCash() {
  return useQuery({
    queryKey: cashKeys.current,
    queryFn: async () => (await unwrap(api.GET('/api/cash-sessions/current'))) ?? null,
    refetchInterval: 30_000,
  });
}

export function useCashHistory(page: number) {
  return useQuery({
    queryKey: [...cashKeys.history, page],
    queryFn: () => unwrap(api.GET('/api/cash-sessions', { params: { query: { page, size: 10 } } })),
  });
}

export function useCashSession(id: string | null) {
  return useQuery({
    queryKey: cashKeys.session(id ?? ''),
    queryFn: () => unwrap(api.GET('/api/cash-sessions/{id}', { params: { path: { id: id ?? '' } } })),
    enabled: id !== null,
  });
}

/** Abrir, movimentar e fechar devolvem o caixa atualizado: vira o "atual" sem outra ida à API. */
function useCashMutation<T>(request: (body: T) => Promise<CashSession>, success: (session: CashSession) => string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (session) => {
      queryClient.setQueryData(cashKeys.current, session.status === 'OPEN' ? session : null);
      notifications.show({ color: 'green', message: success(session) });
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: cashKeys.all }),
  });
}

export function useOpenCash() {
  return useCashMutation(
    (openingAmountCents: number) => unwrap(api.POST('/api/cash-sessions', { body: { openingAmountCents } })),
    () => 'Caixa aberto.',
  );
}

export function useCashMovement(sessionId: string) {
  return useCashMutation(
    (body: { type: 'WITHDRAWAL' | 'DEPOSIT'; amountCents: number; reason: string }) =>
      unwrap(api.POST('/api/cash-sessions/{id}/movements', { params: { path: { id: sessionId } }, body })),
    () => 'Movimento registrado.',
  );
}

export function useCloseCash(sessionId: string) {
  return useCashMutation(
    (body: { counts: { paymentMethodId: string; countedCents: number }[]; notes?: string }) =>
      unwrap(api.PATCH('/api/cash-sessions/{id}', { params: { path: { id: sessionId } }, body })),
    () => 'Caixa fechado.',
  );
}

/** Relatório na impressora térmica. A chave por clique evita imprimir duas vezes no duplo clique. */
export function usePrintCashReport(sessionId: string) {
  return useMutation({
    mutationFn: (printerId: string) =>
      unwrap(
        api.POST('/api/cash-sessions/{sessionId}/print-jobs', {
          params: { path: { sessionId }, header: { 'Idempotency-Key': crypto.randomUUID() } },
          body: { printerId },
        }),
      ),
    onSuccess: (job) => notifications.show({ color: 'green', message: `${job.title}: enviado para a impressora.` }),
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
  });
}
