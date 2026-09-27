import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { PrintJob, Printer } from '../../shared/api/types';
import { MENU_IDS, order, orderSummary } from '../../test/fixtures';
import { loggedInAs, renderApp } from '../../test/render';
import { server } from '../../test/server';

const KITCHEN_PRINTER = '01a0d567-0000-7000-8000-0000000000c2';
const CASHIER_PRINTER = '01a0d567-0000-7000-8000-0000000000c3';

function printer(id: string, name: string): Printer {
  return {
    id,
    agentId: '01a0d567-0000-7000-8000-0000000000c1',
    name,
    connectionType: 'NETWORK',
    host: '10.0.0.5',
    port: 9100,
    systemName: null,
    paperWidthMm: 80,
    columns: 48,
    codepage: 'PC860',
    cutMode: 'PARTIAL',
    active: true,
    status: 'ONLINE',
    statusDetail: null,
    statusUpdatedAt: null,
  };
}

function job(overrides: Partial<PrintJob> = {}): PrintJob {
  return {
    id: '01a0d567-0000-7000-8000-0000000000e9',
    orderId: order().id,
    title: 'Pedido 12 · Cozinha',
    printerId: KITCHEN_PRINTER,
    documentType: 'PRODUCTION_TICKET',
    sectorId: MENU_IDS.kitchen,
    reason: 'AUTO',
    status: 'PRINTED',
    attempts: 0,
    lastError: null,
    preview: 'COZINHA - PEDIDO 12',
    createdAt: '2026-09-27T12:00:00Z',
    printedAt: '2026-09-27T12:00:02Z',
    ...overrides,
  };
}

describe('impressões', () => {
  it('mostra a faixa de alerta em todas as telas e leva ao painel', async () => {
    loggedInAs('CASHIER');
    server.use(
      http.get('/api/print-alerts', () =>
        HttpResponse.json([
          { printerId: KITCHEN_PRINTER, message: 'Impressora Cozinha offline: 3 impressões aguardando.', waitingJobs: 3 },
        ]),
      ),
      http.get('/api/orders/active', () => HttpResponse.json([])),
    );
    renderApp('/pedidos');

    const bar = await screen.findByRole('alert', { name: 'Alertas de impressão' });
    expect(within(bar).getByText('Impressora Cozinha offline: 3 impressões aguardando.')).toBeInTheDocument();
    expect(within(bar).getByRole('link', { name: 'Ver impressões' })).toHaveAttribute('href', '/impressoes');
  });

  it('no painel, manda de novo o incerto para outra impressora', async () => {
    loggedInAs('CASHIER');
    let retried: URL | undefined;
    server.use(
      http.get('/api/printers', () =>
        HttpResponse.json([printer(KITCHEN_PRINTER, 'Cozinha'), printer(CASHIER_PRINTER, 'Caixa')]),
      ),
      http.get('/api/print-jobs', () =>
        HttpResponse.json({
          content: [
            job({ id: 'ok', title: 'Pedido 11 · Cozinha' }),
            job({ status: 'UNCERTAIN', lastError: 'Agente reiniciou', printedAt: null }),
          ],
          page: 0,
          size: 20,
          totalElements: 2,
          totalPages: 1,
        }),
      ),
      http.post('/api/print-jobs/:id/retry', ({ request }) => {
        retried = new URL(request.url);
        return HttpResponse.json(job({ status: 'PENDING', printerId: CASHIER_PRINTER }));
      }),
    );
    const { user } = renderApp('/impressoes');

    const row = await screen.findByRole('row', { name: 'Pedido 12 · Cozinha' });
    expect(within(row).getByText('Incerto')).toBeInTheDocument();
    expect(within(row).getByText('Agente reiniciou')).toBeInTheDocument();
    expect(within(screen.getByRole('row', { name: 'Pedido 11 · Cozinha' })).queryByRole('button')).toBeNull();

    await user.click(within(row).getByRole('button', { name: 'Em outra' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Caixa' }));

    await waitFor(() => expect(retried?.pathname).toBe(`/api/print-jobs/${job().id}/retry`));
    expect(retried?.searchParams.get('printerId')).toBe(CASHIER_PRINTER);
  });

  it('no detalhe do pedido, reimprime o que já saiu com uma chave por clique', async () => {
    loggedInAs('CASHIER');
    const reprints: { body: unknown; key: string | null }[] = [];
    server.use(
      http.get('/api/orders/active', () => HttpResponse.json([orderSummary({ status: 'CONFIRMED' })])),
      http.get('/api/orders/:id', () => HttpResponse.json(order({ status: 'CONFIRMED' }))),
      http.get('/api/orders/:id/history', () => HttpResponse.json([])),
      http.get('/api/orders/:id/payments', () => HttpResponse.json([])),
      http.get('/api/printers', () => HttpResponse.json([printer(KITCHEN_PRINTER, 'Cozinha')])),
      http.get('/api/orders/:orderId/print-jobs', () => HttpResponse.json([job()])),
      http.post('/api/orders/:orderId/print-jobs', async ({ request }) => {
        reprints.push({ body: await request.json(), key: request.headers.get('Idempotency-Key') });
        return HttpResponse.json(job({ reason: 'REPRINT', title: 'Pedido 12 · Cozinha (reimpressão)' }), {
          status: 201,
        });
      }),
    );
    const { user } = renderApp('/pedidos');

    await user.click(await screen.findByRole('button', { name: 'Abrir pedido 12' }));
    const section = await screen.findByLabelText('Impressões do pedido');
    expect(within(section).getByText('Impresso')).toBeInTheDocument();
    await user.click(within(section).getByRole('button', { name: 'Reimprimir Pedido 12 · Cozinha' }));

    await waitFor(() => expect(reprints).toHaveLength(1));
    expect(reprints[0].body).toEqual({
      documentType: 'PRODUCTION_TICKET',
      sectorId: MENU_IDS.kitchen,
      printerId: KITCHEN_PRINTER,
    });
    expect(reprints[0].key).toMatch(/^[0-9a-f-]{36}$/);
  });
});
