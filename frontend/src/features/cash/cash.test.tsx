import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import type { CashSession, Dashboard, Revenue } from '../../shared/api/types';
import { ORDER_IDS } from '../../test/fixtures';
import { loggedInAs, renderApp } from '../../test/render';
import { server } from '../../test/server';

const SESSION_ID = '01a0d567-0000-7000-8000-0000000000c1';
const PIX_ID = '01a0d567-0000-7000-8000-0000000000c2';

function session(overrides: Partial<CashSession> = {}): CashSession {
  return {
    id: SESSION_ID,
    status: 'OPEN',
    openedAt: '2026-09-29T12:00:00Z',
    openedByName: 'Ana',
    openingAmountCents: 10_000,
    closedAt: null,
    closedByName: null,
    notes: null,
    lines: [
      { paymentMethodId: ORDER_IDS.cash, name: 'Dinheiro', type: 'CASH', paymentsCents: 6_000, payments: 2, expectedCents: 16_000, countedCents: null, differenceCents: null },
      { paymentMethodId: PIX_ID, name: 'Pix', type: 'PIX', paymentsCents: 3_500, payments: 1, expectedCents: 3_500, countedCents: null, differenceCents: null },
    ],
    movements: [],
    expectedCents: 19_500,
    countedCents: null,
    differenceCents: null,
    ...overrides,
  };
}

const emptyHistory = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 };

describe('caixa', () => {
  beforeEach(() => {
    loggedInAs('CASHIER');
    server.use(
      http.get('/api/cash-sessions', () => HttpResponse.json(emptyHistory)),
      http.get('/api/printers', () => HttpResponse.json([])),
    );
  });

  it('abre o caixa com o troco da gaveta', async () => {
    let opened: unknown;
    server.use(
      http.get('/api/cash-sessions/current', () =>
        opened ? HttpResponse.json(session()) : new HttpResponse(null, { status: 204 }),
      ),
      http.post('/api/cash-sessions', async ({ request }) => {
        opened = await request.json();
        return HttpResponse.json(session(), { status: 201 });
      }),
    );
    const { user } = renderApp('/caixa');

    await user.type(await screen.findByLabelText('Troco na gaveta'), '100');
    await user.click(screen.getByRole('button', { name: 'Abrir caixa' }));

    await waitFor(() => expect(opened).toEqual({ openingAmountCents: 10_000 }));
    expect(await screen.findByText('Aberto')).toBeInTheDocument();
    const table = screen.getByRole('table', { name: 'Conferência por forma de pagamento' });
    expect(within(table).getByText('R$ 160,00')).toBeInTheDocument();
  });

  it('fecha contando cada forma e mostra a diferença', async () => {
    let closed: unknown;
    server.use(
      http.get('/api/cash-sessions/current', () => HttpResponse.json(session())),
      http.patch('/api/cash-sessions/:id', async ({ request }) => {
        closed = await request.json();
        return HttpResponse.json(session({ status: 'CLOSED', closedAt: '2026-09-29T22:00:00Z' }));
      }),
    );
    const { user } = renderApp('/caixa');

    await user.click(await screen.findByRole('button', { name: 'Fechar caixa' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Dinheiro contado'), '155');
    await user.type(within(dialog).getByLabelText('Pix contado'), '35');
    expect(within(dialog).getByText('-R$ 5,00')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar fechamento' }));

    await waitFor(() =>
      expect(closed).toEqual({
        counts: [
          { paymentMethodId: ORDER_IDS.cash, countedCents: 15_500 },
          { paymentMethodId: PIX_ID, countedCents: 3_500 },
        ],
      }),
    );
  });
});

describe('relatórios', () => {
  const summary = {
    orders: 3,
    grossCents: 10_200,
    subtotalCents: 9_700,
    discountCents: 0,
    platformSubsidyCents: 0,
    deliveryFeeCents: 500,
    additionalFeeCents: 0,
    averageTicketCents: 3_400,
    cancelledOrders: 1,
    cancelledCents: 3_000,
  };

  it('mostra o painel do dia', async () => {
    loggedInAs('OWNER');
    const dashboard: Dashboard = {
      date: '2026-09-29',
      summary,
      averagePreparationSeconds: 900,
      ordersByHour: Array.from({ length: 24 }, (_, hour) => (hour === 19 ? 3 : 0)),
      topProducts: [{ name: 'X-Burger', quantity: 3, totalCents: 9_000 }],
      bySource: [{ key: 'PEDEAI', orders: 3, totalCents: 10_200 }],
    };
    server.use(http.get('/api/reports/dashboard', () => HttpResponse.json(dashboard)));
    renderApp('/relatorios');

    expect(within(await screen.findByLabelText('Faturamento')).getByText('R$ 102,00')).toBeInTheDocument();
    expect(within(screen.getByLabelText('Tempo médio de preparo')).getByText('15 min')).toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Mais vendidos' })).getByText('X-Burger')).toBeInTheDocument();
  });

  it('mostra o faturamento por forma de pagamento', async () => {
    loggedInAs('MANAGER');
    const revenue: Revenue = {
      from: '2026-09-01',
      to: '2026-09-29',
      summary,
      byDay: [{ key: '2026-09-29', orders: 3, totalCents: 10_200 }],
      bySource: [{ key: 'PEDEAI', orders: 3, totalCents: 10_200 }],
      byType: [{ key: 'DELIVERY', orders: 1, totalCents: 3_500 }],
      byPaymentMethod: [
        { paymentMethodId: PIX_ID, name: 'Pix', type: 'PIX', payments: 1, totalCents: 3_500, pendingCents: 0 },
      ],
    };
    server.use(http.get('/api/reports/revenue', () => HttpResponse.json(revenue)));
    renderApp('/relatorios/faturamento');

    const methods = await screen.findByRole('table', { name: 'Por forma de pagamento' });
    expect(within(methods).getByText('R$ 35,00')).toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Por tipo' })).getByText('Delivery')).toBeInTheDocument();
  });

  it('esconde os relatórios de quem está no caixa', async () => {
    loggedInAs('CASHIER');
    server.use(http.get('/api/cash-sessions/current', () => new HttpResponse(null, { status: 204 })));
    renderApp('/relatorios');

    expect(await screen.findByRole('link', { name: 'Caixa' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Faturamento' })).not.toBeInTheDocument();
  });
});
