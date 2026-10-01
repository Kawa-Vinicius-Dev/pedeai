import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import type { OptionChoice, OrderTracking, PriceQuote, Storefront } from '../shared/api/types';
import { MENU_IDS, optionGroup, ORDER_IDS, store } from '../test/fixtures';
import { loggedInAs, renderApp, renderMenu } from '../test/render';
import { server } from '../test/server';

const CODE = 'codigo-de-acompanhamento-1';

function storefront(overrides: Partial<Storefront> = {}): Storefront {
  return {
    name: 'Pizzaria Bella',
    slug: 'pizzaria-bella',
    phone: '(11) 3333-4444',
    open: true,
    deliveryZones: [{ neighborhood: 'Centro', feeCents: 800 }],
    paymentMethods: [
      { id: ORDER_IDS.cash, name: 'Dinheiro', type: 'CASH' },
      { id: ORDER_IDS.pix, name: 'Pix', type: 'PIX' },
    ],
    categories: [
      {
        id: MENU_IDS.pizzas,
        name: 'Pizzas',
        products: [
          { id: MENU_IDS.pizza, name: 'Pizza Grande', description: '8 fatias', priceCents: 0, optionGroupIds: [MENU_IDS.flavors], available: true },
          { id: MENU_IDS.soda, name: 'Refrigerante lata', description: null, priceCents: 700, optionGroupIds: [], available: false },
        ],
      },
    ],
    optionGroups: [optionGroup()],
    ...overrides,
  };
}

function tracking(overrides: Partial<OrderTracking> = {}): OrderTracking {
  return {
    number: 7,
    type: 'DELIVERY',
    status: 'RECEIVED',
    storeName: 'Pizzaria Bella',
    storeSlug: 'pizzaria-bella',
    storePhone: '(11) 3333-4444',
    items: [{ quantity: 1, name: 'Pizza Grande', details: 'Calabresa, Quatro queijos' }],
    subtotalCents: 5290,
    deliveryFeeCents: 800,
    totalCents: 6090,
    createdAt: '2026-10-01T22:00:00Z',
    cancelReason: null,
    ...overrides,
  };
}

/** Pizza pelo sabor mais caro, como a API calcula. */
function serveQuotes() {
  server.use(
    http.post('/api/public/stores/:slug/products/:id/price-quotes', async ({ request }) => {
      const body = (await request.json()) as { quantity: number; options: OptionChoice[] };
      const chosen = optionGroup().options.filter((option) => body.options.some((choice) => choice.optionId === option.id));
      const unit = chosen.length === 0 ? 0 : Math.max(...chosen.map((option) => option.priceCents));
      const quote: PriceQuote = {
        productId: MENU_IDS.pizza,
        name: 'Pizza Grande',
        code: '500',
        sectorId: MENU_IDS.kitchen,
        quantity: body.quantity,
        basePriceCents: 0,
        optionsPriceCents: unit,
        unitPriceCents: unit,
        totalCents: unit * body.quantity,
        options: chosen.map((option) => ({
          optionId: option.id,
          groupId: MENU_IDS.flavors,
          groupName: 'Sabores',
          name: option.name,
          code: option.code,
          quantity: 1,
          unitPriceCents: option.priceCents,
        })),
      };
      return HttpResponse.json(quote);
    }),
  );
}

describe('cardápio digital', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('monta a pizza, faz o pedido para entrega e acompanha', async () => {
    let placed: unknown;
    server.use(
      http.get('/api/public/stores/pizzaria-bella', () => HttpResponse.json(storefront())),
      http.post('/api/public/stores/pizzaria-bella/orders', async ({ request }) => {
        placed = await request.json();
        return HttpResponse.json({ number: 7, trackingCode: CODE, totalCents: 6090 }, { status: 201 });
      }),
      http.get(`/api/public/orders/${CODE}`, () => HttpResponse.json(tracking())),
    );
    serveQuotes();
    const { user, router } = renderMenu('/loja/pizzaria-bella');

    expect(await screen.findByRole('heading', { name: 'Pizzaria Bella' })).toBeInTheDocument();
    expect(within(screen.getByRole('button', { name: 'Refrigerante lata' })).getByText('Esgotado')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Refrigerante lata' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Pizza Grande' }));
    const sheet = await screen.findByRole('dialog');
    await user.click(within(sheet).getByLabelText('Calabresa'));
    await user.click(within(sheet).getByLabelText('Quatro queijos'));
    await user.click(await within(sheet).findByRole('button', { name: /^Adicionar R\$\s52,90$/ }));

    await user.click(await screen.findByRole('button', { name: /Ver carrinho · 1 item · R\$\s52,90/ }));
    const cart = await screen.findByRole('dialog', { name: 'Seu carrinho' });
    expect(within(cart).getByText('Calabresa, Quatro queijos')).toBeInTheDocument();
    await user.click(within(cart).getByRole('button', { name: 'Continuar' }));

    const checkout = await screen.findByRole('dialog', { name: 'Finalizar pedido' });
    await user.type(within(checkout).getByLabelText('Seu nome'), 'Maria');
    await user.type(within(checkout).getByLabelText('Telefone com DDD'), '11988880000');
    await user.click(within(checkout).getByRole('combobox', { name: 'Bairro' }));
    await user.click(await screen.findByRole('option', { name: /Centro/ }));
    await user.type(within(checkout).getByLabelText('Rua'), 'Rua das Flores');
    await user.type(within(checkout).getByLabelText('Número'), '120');
    await user.click(within(checkout).getByRole('combobox', { name: 'Pagamento na entrega ou na retirada' }));
    await user.click(await screen.findByRole('option', { name: 'Dinheiro' }));
    await user.type(within(checkout).getByLabelText(/Troco para/), '100');
    expect(within(checkout).getByText('R$ 60,90')).toBeInTheDocument();
    await user.click(within(checkout).getByRole('button', { name: 'Fazer pedido' }));

    await waitFor(() =>
      expect(placed).toEqual({
        type: 'DELIVERY',
        customerName: 'Maria',
        customerPhone: '11988880000',
        deliveryAddress: { street: 'Rua das Flores', number: '120', neighborhood: 'Centro' },
        items: [
          {
            productId: MENU_IDS.pizza,
            quantity: 1,
            options: [
              { optionId: MENU_IDS.calabresa, quantity: 1 },
              { optionId: MENU_IDS.fourCheese, quantity: 1 },
            ],
          },
        ],
        paymentMethodId: ORDER_IDS.cash,
        changeForCents: 10_000,
      }),
    );
    await waitFor(() => expect(router.state.location.pathname).toBe(`/loja/pizzaria-bella/pedido/${CODE}`));
    expect(await screen.findByText('Esperando a loja aceitar')).toBeInTheDocument();
    expect(screen.getByText('R$ 60,90')).toBeInTheDocument();
  });

  it('com a loja fechada mostra o cardápio mas não deixa pedir', async () => {
    server.use(http.get('/api/public/stores/pizzaria-bella', () => HttpResponse.json(storefront({ open: false }))));
    renderMenu('/loja/pizzaria-bella');

    expect(await screen.findByText('A loja não está recebendo pedidos agora. Dá para ver o cardápio.')).toBeInTheDocument();
    expect(screen.getByText('Fechado')).toBeInTheDocument();
  });

  it('mostra o motivo quando a loja cancela', async () => {
    server.use(
      http.get(`/api/public/orders/${CODE}`, () =>
        HttpResponse.json(tracking({ status: 'CANCELLED', cancelReason: 'Acabou a massa' })),
      ),
    );
    renderMenu(`/loja/pizzaria-bella/pedido/${CODE}`);

    expect(await screen.findByText('Acabou a massa')).toBeInTheDocument();
    expect(screen.getByText('Pedido cancelado')).toBeInTheDocument();
  });
});

describe('cardápio no painel da loja', () => {
  it('quem está no caixa abre o cardápio pelo quadro de pedidos', async () => {
    loggedInAs('CASHIER');
    let body: unknown;
    server.use(
      http.get('/api/orders/active', () => HttpResponse.json([])),
      http.put('/api/store/menu-open', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(store({ menuOpen: true }));
      }),
    );
    const { user } = renderApp('/pedidos');

    const toggle = await screen.findByRole('switch', { name: 'Cardápio aberto' });
    expect(toggle).not.toBeChecked();
    await user.click(toggle);

    await waitFor(() => expect(body).toEqual({ open: true }));
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Cardápio aberto' })).toBeChecked());
  });

  it('o dono vê e troca o endereço do cardápio', async () => {
    loggedInAs('OWNER');
    let patch: unknown;
    server.use(
      http.patch('/api/store', async ({ request }) => {
        patch = await request.json();
        return HttpResponse.json(store({ slug: 'bella-centro' }));
      }),
    );
    const { user } = renderApp('/configuracoes/loja');

    const slug = await screen.findByLabelText('Endereço do cardápio');
    await waitFor(() => expect(slug).toHaveValue('pizzaria-bella'));
    expect(screen.getByRole('link', { name: /\/loja\/pizzaria-bella$/ })).toBeInTheDocument();
    await user.clear(slug);
    await user.type(slug, 'Bella Centro');
    await user.click(screen.getByRole('button', { name: 'Salvar' }));
    expect(await screen.findByText(/Use só letras minúsculas/)).toBeInTheDocument();

    await user.clear(slug);
    await user.type(slug, 'bella-centro');
    await user.click(screen.getByRole('button', { name: 'Salvar' }));
    await waitFor(() => expect(patch).toMatchObject({ slug: 'bella-centro' }));
  });
});

