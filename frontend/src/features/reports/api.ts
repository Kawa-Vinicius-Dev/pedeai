import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import { unwrap } from '../../shared/api/errors';

/** Sem data, o dia operacional de hoje na loja (a virada do dia é a da loja, não a meia-noite). */
export function useDashboard(date: string | null) {
  return useQuery({
    queryKey: ['reports', 'dashboard', date],
    queryFn: () => unwrap(api.GET('/api/reports/dashboard', { params: { query: { date: date ?? undefined } } })),
    refetchInterval: 60_000,
  });
}

export function useRevenue(from: string, to: string) {
  return useQuery({
    queryKey: ['reports', 'revenue', from, to],
    queryFn: () => unwrap(api.GET('/api/reports/revenue', { params: { query: { from, to } } })),
    enabled: from !== '' && to !== '' && from <= to,
  });
}
