import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { TicketDocument } from '../../shared/api/types';
import { MENU_IDS, order, orderSummary, pizzeriaMenu } from '../../test/fixtures';
import { loggedInAs, renderApp } from '../../test/render';
import { server, serveMenu } from '../../test/server';

describe('impressão pelo navegador', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  function serveOrder() {
    const requests: URL[] = [];
    serveMenu(pizzeriaMenu());
    server.use(
      http.get('/api/orders/active', () => HttpResponse.json([orderSummary({ status: 'CONFIRMED' })])),
      http.get('/api/orders/:id', () => HttpResponse.json(order({ status: 'CONFIRMED' }))),
      http.get('/api/orders/:id/history', () => HttpResponse.json([])),
      http.get('/api/orders/:id/payments', () => HttpResponse.json([])),
      http.get('/api/orders/:orderId/tickets/:documentType', ({ request, params }) => {
        requests.push(new URL(request.url));
        const ticket: TicketDocument = {
          documentType: params.documentType as TicketDocument['documentType'],
          columns: 32,
          lines: [{ text: 'COZINHA - PEDIDO 12', align: 'CENTER', bold: false, big: true }],
        };
        return HttpResponse.json(ticket);
      }),
    );
    return requests;
  }

  it('imprime o ticket de produção do setor no papel escolhido, só com o ticket na página', async () => {
    loggedInAs('CASHIER');
    const requests = serveOrder();
    const printed: (string | null)[] = [];
    vi.spyOn(window, 'print').mockImplementation(() => {
      printed.push(document.querySelector('.ticket-print')?.textContent ?? null);
    });
    const { user } = renderApp('/pedidos');

    await user.click(await screen.findByRole('button', { name: 'Abrir pedido 12' }));
    const drawer = await screen.findByRole('dialog', { name: 'Pedido 12' });
    await user.click(within(drawer).getByRole('button', { name: 'Imprimir' }));
    await user.click(await screen.findByText('58mm'));
    await user.click(await screen.findByRole('menuitem', { name: 'Produção · Bar' }));

    await vi.waitFor(() => expect(printed).toEqual(['COZINHA - PEDIDO 12']));
    expect(requests[0].pathname).toMatch(/\/tickets\/PRODUCTION_TICKET$/);
    expect(requests[0].searchParams.get('sectorId')).toBe(MENU_IDS.bar);
    expect(requests[0].searchParams.get('columns')).toBe('32');
    expect(document.querySelector('.ticket-print')).toBeNull();
  });

  it('a cozinha não vê a via completa', async () => {
    loggedInAs('KITCHEN');
    serveOrder();
    const { user } = renderApp('/pedidos');

    await user.click(await screen.findByRole('button', { name: 'Abrir pedido 12' }));
    await user.click(within(await screen.findByRole('dialog', { name: 'Pedido 12' })).getByRole('button', { name: 'Imprimir' }));

    expect(await screen.findByRole('menuitem', { name: 'Produção · Cozinha' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Via completa' })).not.toBeInTheDocument();
  });
});
