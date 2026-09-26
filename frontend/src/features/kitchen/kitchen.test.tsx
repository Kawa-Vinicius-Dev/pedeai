import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MENU_IDS, order, pizzeriaMenu } from '../../test/fixtures';
import { loggedInAs, renderApp } from '../../test/render';
import { server, serveMenu } from '../../test/server';

describe('cozinha', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  function serveKitchen() {
    const sectorIds: (string | null)[] = [];
    const patches: unknown[] = [];
    server.use(
      http.get('/api/kitchen/orders', ({ request }) => {
        const sectorId = new URL(request.url).searchParams.get('sectorId');
        sectorIds.push(sectorId);
        const confirmed = order({ status: 'CONFIRMED', confirmedAt: new Date().toISOString() });
        return HttpResponse.json([
          sectorId ? { ...confirmed, items: confirmed.items.filter((item) => item.sectorId === sectorId) } : confirmed,
        ]);
      }),
      http.patch('/api/orders/:id/status', async ({ request }) => {
        patches.push(await request.json());
        return HttpResponse.json(order({ status: 'IN_PREPARATION', version: 1 }));
      }),
    );
    return { sectorIds, patches };
  }

  it('filtra pelo setor e mostra itens, sabores e observações', async () => {
    loggedInAs('KITCHEN');
    serveMenu(pizzeriaMenu());
    const { sectorIds } = serveKitchen();
    renderApp('/cozinha');

    const card = await screen.findByLabelText('Pedido 12');
    expect(within(card).getByText('1x Pizza Grande')).toBeInTheDocument();
    expect(within(card).getByText('Quatro queijos')).toBeInTheDocument();
    expect(within(card).getByText('Sem cebola')).toBeInTheDocument();
    expect(within(card).getByText('2x Refrigerante lata')).toBeInTheDocument();

    fireEvent.click(await screen.findByText('Bar'));
    await waitFor(() => expect(sectorIds).toContain(MENU_IDS.bar));
    await waitFor(() => expect(screen.queryByText('1x Pizza Grande')).not.toBeInTheDocument());
    expect(screen.getByText('2x Refrigerante lata')).toBeInTheDocument();
  });

  it('dá alguns segundos para desfazer antes de mudar o status', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    loggedInAs('KITCHEN');
    serveMenu(pizzeriaMenu());
    const { patches } = serveKitchen();
    renderApp('/cozinha');

    const card = await screen.findByLabelText('Pedido 12');
    fireEvent.click(within(card).getByRole('button', { name: 'Iniciar' }));
    fireEvent.click(within(card).getByRole('button', { name: 'Desfazer' }));
    await act(() => vi.advanceTimersByTimeAsync(6_000));
    expect(patches).toEqual([]);

    fireEvent.click(within(card).getByRole('button', { name: 'Iniciar' }));
    await act(() => vi.advanceTimersByTimeAsync(6_000));
    await waitFor(() => expect(patches).toEqual([{ status: 'IN_PREPARATION', version: 0 }]));
  });
});
