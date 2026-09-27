import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { PrintJob, Printer, SectorPrinterRequest } from '../../shared/api/types';

/** Tudo de impressão fica sob ['printing']. O status muda com o heartbeat do agente, então recarrega sozinho. */
export const printingKeys = {
  all: ['printing'] as const,
  agents: ['printing', 'agents'] as const,
  printers: ['printing', 'printers'] as const,
  sectorPrinters: ['printing', 'sector-printers'] as const,
};

const STATUS_REFRESH_MS = 20_000;

export function usePrintAgents() {
  return useQuery({
    queryKey: printingKeys.agents,
    queryFn: () => unwrap(api.GET('/api/print-agents')),
    refetchInterval: STATUS_REFRESH_MS,
  });
}

export function usePrinters() {
  return useQuery({
    queryKey: printingKeys.printers,
    queryFn: () => unwrap(api.GET('/api/printers')),
    refetchInterval: STATUS_REFRESH_MS,
  });
}

export function useSectorPrinters() {
  return useQuery({ queryKey: printingKeys.sectorPrinters, queryFn: () => unwrap(api.GET('/api/sector-printers')) });
}

export function useRevokeAgent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => unwrap(api.DELETE('/api/print-agents/{id}', { params: { path: { id } } })),
    onSuccess: () => notifications.show({ color: 'green', message: 'Computador removido.' }),
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: printingKeys.all }),
  });
}

export function useAssignSectorPrinter() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ sectorId, body }: { sectorId: string; body: SectorPrinterRequest }) =>
      unwrap(api.PUT('/api/sectors/{sectorId}/printer', { params: { path: { sectorId } }, body })),
    onSuccess: () => notifications.show({ color: 'green', message: 'Impressora do setor salva.' }),
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: printingKeys.sectorPrinters }),
  });
}

export const STATUS_LABELS: Record<Printer['status'], string> = {
  UNKNOWN: 'Aguardando o agente',
  ONLINE: 'Online',
  OFFLINE: 'Offline',
  ERROR: 'Com problema',
};

export const STATUS_COLORS: Record<Printer['status'], string> = {
  UNKNOWN: 'gray',
  ONLINE: 'green',
  OFFLINE: 'red',
  ERROR: 'orange',
};

/** Os mesmos nomes da página de teste do agente, para a pessoa escolher a linha que saiu certa. */
export const CODEPAGE_OPTIONS: { value: Printer['codepage']; label: string }[] = [
  { value: 'PC860', label: 'n=3 PC860 (português)' },
  { value: 'PC850', label: 'n=2 PC850' },
  { value: 'WPC1252', label: 'n=16 WPC1252' },
  { value: 'PC858', label: 'n=19 PC858' },
  { value: 'PC437', label: 'n=0 PC437' },
  { value: 'NO_ACCENTS', label: 'Sem acentos' },
];

export const CUT_OPTIONS: { value: Printer['cutMode']; label: string }[] = [
  { value: 'PARTIAL', label: 'Corte parcial' },
  { value: 'FULL', label: 'Corte total' },
  { value: 'NONE', label: 'Sem corte' },
];

export const jobKeys = {
  all: ['printing', 'jobs'] as const,
  alerts: ['printing', 'jobs', 'alerts'] as const,
  recent: ['printing', 'jobs', 'recent'] as const,
  order: (orderId: string) => ['printing', 'jobs', 'order', orderId] as const,
};

/** A faixa de alerta de todas as telas. O agente manda status a cada 20 s, então consultar mais que isso não ajuda. */
export function usePrintAlerts(enabled: boolean) {
  return useQuery({
    queryKey: jobKeys.alerts,
    queryFn: () => unwrap(api.GET('/api/print-alerts')),
    refetchInterval: STATUS_REFRESH_MS,
    enabled,
  });
}

export function useRecentPrintJobs(page: number) {
  return useQuery({
    queryKey: [...jobKeys.recent, page],
    queryFn: () => unwrap(api.GET('/api/print-jobs', { params: { query: { page, size: 20 } } })),
    refetchInterval: 10_000,
  });
}

export function useOrderPrintJobs(orderId: string) {
  return useQuery({
    queryKey: jobKeys.order(orderId),
    queryFn: () => unwrap(api.GET('/api/orders/{orderId}/print-jobs', { params: { path: { orderId } } })),
    refetchInterval: 5_000,
  });
}

/** Imprimir de novo o que falhou, ficou incerto ou expirou. Sem impressora, vai para a mesma. */
export function useRetryPrintJob() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, printerId }: { id: string; printerId?: string }) =>
      unwrap(api.POST('/api/print-jobs/{id}/retry', { params: { path: { id }, query: { printerId } } })),
    onSuccess: (job) => notifications.show({ color: 'green', message: `${job.title}: mandado de novo.` }),
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: jobKeys.all }),
  });
}

/** Reimpressão com a faixa REIMPRESSÃO. A chave por clique evita imprimir duas vezes no duplo clique. */
export function useReprint(orderId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { documentType: PrintJob['documentType']; sectorId?: string; printerId: string }) =>
      unwrap(
        api.POST('/api/orders/{orderId}/print-jobs', {
          params: { path: { orderId }, header: { 'Idempotency-Key': crypto.randomUUID() } },
          body,
        }),
      ),
    onSuccess: (job) => notifications.show({ color: 'green', message: `${job.title} enviada.` }),
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: jobKeys.all }),
  });
}

export const JOB_STATUS_LABELS: Record<PrintJob['status'], string> = {
  PENDING: 'Na fila',
  SENT: 'Imprimindo',
  PRINTED: 'Impresso',
  FAILED: 'Falhou',
  UNCERTAIN: 'Incerto',
  EXPIRED: 'Expirou',
  CANCELLED: 'Cancelado',
};

export const JOB_STATUS_COLORS: Record<PrintJob['status'], string> = {
  PENDING: 'blue',
  SENT: 'blue',
  PRINTED: 'green',
  FAILED: 'red',
  UNCERTAIN: 'orange',
  EXPIRED: 'gray',
  CANCELLED: 'gray',
};

export const NEEDS_ATTENTION: PrintJob['status'][] = ['FAILED', 'UNCERTAIN', 'EXPIRED'];
