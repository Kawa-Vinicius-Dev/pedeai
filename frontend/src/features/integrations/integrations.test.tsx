import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { CatalogImport, Dispute, MarketplaceConnection, Platform } from '../../shared/api/types';
import { order, orderSummary, store } from '../../test/fixtures';
import { loggedInAs, renderApp } from '../../test/render';
import { server } from '../../test/server';

const CONNECTION: MarketplaceConnection = {
  id: '01a0d567-0000-7000-8000-0000000000b1',
  provider: 'IFOOD',
  externalMerchantId: 'loja-teste',
  merchantName: 'Loja simulada',
  status: 'ACTIVE',
  autoConfirm: false,
  lastEventAt: null,
  lastError: null,
  failedActions: 0,
  catalogSync: false,
  syncPending: 0,
  syncFailed: 0,
};

/** iFood só no simulador; 99Food e Open Delivery desligados, a menos que o teste diga outra coisa. */
function platforms(overrides: Partial<Record<Platform['provider'], Partial<Platform>>> = {}): Platform[] {
  const base: Platform[] = [
    { provider: 'IFOOD', name: 'iFood', configured: false, simulator: true },
    { provider: 'NINETY_NINE_FOOD', name: '99Food', configured: false, simulator: false },
    { provider: 'OPEN_DELIVERY', name: 'Open Delivery', configured: false, simulator: false },
  ];
  return base.map((platform) => ({ ...platform, ...overrides[platform.provider] }));
}

const IMPORT_RESULT: CatalogImport = {
  applied: false,
  categoriesCreated: 2,
  productsCreated: 3,
  productsUpdated: 0,
  optionGroupsCreated: 2,
  errors: [],
};

describe('integração com o iFood', () => {
  it('no modo simulador, liga uma loja de teste e simula um pedido', async () => {
    loggedInAs('OWNER');
    let connections: MarketplaceConnection[] = [];
    const received: { connect?: unknown; simulated?: string } = {};
    server.use(
      http.get('/api/integrations/platforms', () => HttpResponse.json(platforms())),
      http.get('/api/integrations', () => HttpResponse.json(connections)),
      http.post('/api/integrations', async ({ request }) => {
        received.connect = await request.json();
        connections = [CONNECTION];
        return HttpResponse.json(CONNECTION, { status: 201 });
      }),
      http.post('/api/integrations/:id/simulated-orders', ({ params }) => {
        received.simulated = params.id as string;
        return HttpResponse.json({ externalOrderId: 'x' }, { status: 202 });
      }),
      http.post('/api/integrations/:id/catalog-import', async ({ request }) => {
        const { dryRun } = (await request.json()) as { dryRun: boolean };
        return HttpResponse.json({ ...IMPORT_RESULT, applied: !dryRun });
      }),
    );
    const { user } = renderApp('/configuracoes/integracoes');

    expect(await screen.findByText(/Modo simulador/)).toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: 'Ligar ao iFood' }));
    await waitFor(() =>
      expect(received.connect).toEqual({ provider: 'IFOOD', externalMerchantId: 'loja-teste', autoConfirm: false }),
    );

    const card = await screen.findByLabelText('Integração Loja simulada');
    expect(within(card).getByText('Ativa')).toBeInTheDocument();
    await user.click(within(card).getByRole('button', { name: 'Simular pedido do iFood' }));
    await waitFor(() => expect(received.simulated).toBe(CONNECTION.id));

    await user.click(within(card).getByRole('button', { name: 'Importar cardápio do iFood' }));
    const modal = await screen.findByRole('dialog', { name: 'Importar cardápio do iFood' });
    await user.click(within(modal).getByRole('button', { name: 'Pré-visualizar' }));
    expect(await within(modal).findByText('Pré-visualização: nada foi gravado ainda.')).toBeInTheDocument();
    expect(within(within(modal).getByLabelText('Produtos novos')).getByText('3')).toBeInTheDocument();
    await user.click(within(modal).getByRole('button', { name: 'Importar' }));
    expect(await within(modal).findByText('Cardápio importado.')).toBeInTheDocument();
  });

  it('o PedeAí manda no cardápio do iFood: liga a sincronização, salva o acréscimo e envia tudo', async () => {
    loggedInAs('OWNER');
    let connection: MarketplaceConnection = { ...CONNECTION, lastError: 'Pizza: produto com grupo cobrado pelo maior valor' };
    const received: { patch?: unknown; store?: unknown; synced?: string } = {};
    server.use(
      http.get('/api/integrations/platforms', () => HttpResponse.json(platforms())),
      http.get('/api/integrations', () => HttpResponse.json([connection])),
      http.patch('/api/integrations/:id', async ({ request }) => {
        received.patch = await request.json();
        connection = { ...connection, catalogSync: true, syncPending: 12 };
        return HttpResponse.json(connection);
      }),
      http.patch('/api/store', async ({ request }) => {
        received.store = await request.json();
        return HttpResponse.json(store({ ifoodMarkupBp: 1500 }));
      }),
      http.post('/api/integrations/:id/catalog-sync', ({ params }) => {
        received.synced = params.id as string;
        connection = { ...connection, syncFailed: 1 };
        return HttpResponse.json(connection);
      }),
    );
    const { user } = renderApp('/configuracoes/integracoes');

    const card = await screen.findByLabelText('Integração Loja simulada');
    await user.click(within(card).getByRole('switch', { name: /O PedeAí manda no cardápio do iFood/ }));
    await waitFor(() => expect(received.patch).toEqual({ status: 'ACTIVE', autoConfirm: false, catalogSync: true }));
    expect(await within(card).findByText('12 envios na fila')).toBeInTheDocument();

    await user.type(await within(card).findByLabelText('Acréscimo nos preços do iFood'), '15');
    await user.click(within(card).getByRole('button', { name: 'Salvar acréscimo' }));
    await waitFor(() => expect(received.store).toEqual({ ifoodMarkupBp: 1500 }));

    await user.click(within(card).getByRole('button', { name: 'Enviar cardápio e horário agora' }));
    await waitFor(() => expect(received.synced).toBe(CONNECTION.id));
    expect(await within(card).findByText(/1 envio não chegou ao iFood/)).toBeInTheDocument();
    expect(within(card).getByText(/Pizza: produto com grupo/)).toBeInTheDocument();
  });

  it('o cliente pede no app para cancelar: o caixa vê o prazo e recusa com um motivo', async () => {
    loggedInAs('CASHIER');
    let answer: unknown;
    const dispute: Dispute = {
      id: 'd-1',
      orderId: '01a0d567-0000-7000-8000-000000000101',
      orderNumber: 12,
      provider: 'IFOOD',
      kind: 'CANCELLATION',
      message: 'Demorou demais',
      expiresAt: '2026-09-27T15:05:00Z',
      status: 'OPEN',
      rejectReasons: [
        { code: 'DISH_ALREADY_DONE', description: 'O pedido já está pronto' },
        { code: 'OUT_FOR_DELIVERY', description: 'O pedido já saiu para entrega' },
      ],
    };
    let disputes = [dispute];
    server.use(
      http.get('/api/orders/active', () => HttpResponse.json([])),
      http.get('/api/marketplace/disputes', () => HttpResponse.json(disputes)),
      http.post('/api/marketplace/disputes/:id/answer', async ({ request }) => {
        answer = await request.json();
        disputes = [];
        return HttpResponse.json({ ...dispute, status: 'REJECTED' });
      }),
    );
    const { user } = renderApp('/pedidos');

    const alert = await screen.findByRole('alert', { name: 'O cliente pediu para cancelar o pedido #12 (iFood)' });
    expect(within(alert).getByText('“Demorou demais”')).toBeInTheDocument();
    expect(within(alert).getByText(/Depois disso, quem decide é o app/)).toBeInTheDocument();
    await user.click(within(alert).getByRole('button', { name: 'Recusar' }));
    await user.click(within(alert).getByRole('combobox', { name: 'Motivo da recusa' }));
    await user.click(await screen.findByRole('option', { name: 'O pedido já está pronto' }));
    await user.click(within(alert).getByRole('button', { name: 'Recusar cancelamento' }));
    await waitFor(() => expect(answer).toEqual({ accept: false, rejectCode: 'DISH_ALREADY_DONE' }));
    await waitFor(() =>
      expect(screen.queryByRole('alert', { name: /pedido #12/ })).not.toBeInTheDocument(),
    );
  });

  it('liga a 99Food pelo padrão Open Delivery quando é o que o servidor tem', async () => {
    loggedInAs('MANAGER');
    let body: unknown;
    server.use(
      http.get('/api/integrations/platforms', () =>
        HttpResponse.json(platforms({ IFOOD: { simulator: false }, NINETY_NINE_FOOD: { configured: true } })),
      ),
      http.get('/api/integrations', () => HttpResponse.json([])),
      http.post('/api/integrations', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...CONNECTION, provider: 'NINETY_NINE_FOOD', merchantName: '99Food' }, { status: 201 });
      }),
    );
    const { user } = renderApp('/configuracoes/integracoes');

    await user.type(await screen.findByLabelText('Id da loja no 99Food'), 'loja-99');
    await user.click(screen.getByRole('button', { name: 'Ligar ao 99Food' }));
    await waitFor(() =>
      expect(body).toEqual({ provider: 'NINETY_NINE_FOOD', externalMerchantId: 'loja-99', autoConfirm: false }),
    );
  });

  it('pedido do iFood mostra o selo e pede o cancelamento com um motivo do iFood', async () => {
    loggedInAs('CASHIER');
    const ifoodOrder = order({ status: 'CONFIRMED', source: 'IFOOD', externalDisplayId: '7391', externalId: 'ifood-1' });
    let requested: unknown;
    server.use(
      http.get('/api/orders/active', () =>
        HttpResponse.json([orderSummary({ status: 'CONFIRMED', source: 'IFOOD', externalDisplayId: '7391' })]),
      ),
      http.get('/api/orders/:id', () => HttpResponse.json(ifoodOrder)),
      http.get('/api/orders/:id/history', () => HttpResponse.json([])),
      http.get('/api/orders/:id/payments', () => HttpResponse.json([])),
      http.get('/api/payment-methods', () => HttpResponse.json([])),
      http.get('/api/orders/:orderId/marketplace/sync', () =>
        HttpResponse.json([
          { id: 'a1', action: 'CONFIRM', status: 'DONE', attempts: 0, lastError: null, createdAt: '2026-09-27T12:00:00Z' },
        ]),
      ),
      http.get('/api/orders/:orderId/marketplace/cancellation-reasons', () =>
        HttpResponse.json([
          { code: '503', description: 'Item indisponível' },
          { code: '504', description: 'Restaurante sem motoboy' },
        ]),
      ),
      http.post('/api/orders/:orderId/marketplace/cancellation', async ({ request }) => {
        requested = await request.json();
        return HttpResponse.json(
          { id: 'a2', action: 'REQUEST_CANCELLATION', status: 'PENDING', attempts: 0, lastError: null, createdAt: '2026-09-27T12:01:00Z' },
          { status: 202 },
        );
      }),
    );
    const { user } = renderApp('/pedidos');

    const card = await screen.findByLabelText('Pedido 12');
    expect(within(card).getByText('iFood 7391')).toBeInTheDocument();
    await user.click(within(card).getByRole('button', { name: 'Abrir pedido 12' }));
    const drawer = await screen.findByRole('dialog', { name: 'Pedido 12' });
    const panel = await within(drawer).findByLabelText('Sincronização com o iFood');
    expect(within(panel).getByText('Enviado')).toBeInTheDocument();
    expect(within(drawer).queryByRole('button', { name: 'Cancelar pedido' })).not.toBeInTheDocument();

    await user.click(within(panel).getByRole('button', { name: 'Solicitar cancelamento' }));
    const modal = await screen.findByRole('dialog', { name: 'Solicitar cancelamento do pedido 12' });
    await user.click(await within(modal).findByLabelText('Item indisponível'));
    await user.click(within(modal).getByRole('button', { name: 'Solicitar cancelamento' }));

    await waitFor(() => expect(requested).toEqual({ code: '503', description: 'Item indisponível' }));
  });
});
