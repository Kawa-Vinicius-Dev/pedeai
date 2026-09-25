import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import type { OptionChoice, Order, OrderSummary, Payment, PriceQuote } from '../../shared/api/types';
import {
  apiError,
  customer,
  deliveryZone,
  historyEntry,
  MENU_IDS,
  optionGroup,
  order,
  ORDER_IDS,
  orderSummary,
  payment,
  paymentMethod,
  pizzeriaMenu,
} from '../../test/fixtures';
import { loggedInAs, renderApp } from '../../test/render';
import { connectedStreams, pushStreamEvent, server, serveMenu } from '../../test/server';

const cash = paymentMethod();
const pix = paymentMethod({ id: ORDER_IDS.pix, name: 'Pix', type: 'PIX' });
const TAKEOUT_ID = '01a0d567-0000-7000-8000-0000000000f5';
const READY_ID = '01a0d567-0000-7000-8000-0000000000f6';

/** Pizza pela regra do maior valor, como a API calcula. Sem sabor, a API recusa. */
function servePriceQuotes() {
  server.use(
    http.post('/api/products/:id/price-quotes', async ({ request }) => {
      const body = (await request.json()) as { quantity: number; options: OptionChoice[] };
      if (body.options.length === 0) {
        return HttpResponse.json(apiError(422, 'Escolha pelo menos 1 opção em Sabores.'), { status: 422 });
      }
      const chosen = optionGroup().options.filter((option) => body.options.some((choice) => choice.optionId === option.id));
      const unit = Math.max(...chosen.map((option) => option.priceCents));
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

function serveCheckout() {
  serveMenu(pizzeriaMenu());
  servePriceQuotes();
  server.use(
    http.get('/api/payment-methods', () => HttpResponse.json([cash, pix])),
    http.get('/api/delivery-zones', () => HttpResponse.json([deliveryZone()])),
  );
}

describe('novo pedido (PDV)', () => {
  it('lança um delivery com pizza meio a meio, cliente cadastrado, taxa do bairro e troco', async () => {
    loggedInAs('CASHIER');
    serveCheckout();
    let lookedUp: string | null = null;
    let created: unknown;
    server.use(
      http.get('/api/customers', ({ request }) => {
        lookedUp = new URL(request.url).searchParams.get('phone');
        return HttpResponse.json({ content: [customer()], page: 0, size: 20, totalElements: 1, totalPages: 1 });
      }),
      http.post('/api/orders', async ({ request }) => {
        created = await request.json();
        return HttpResponse.json(order(), { status: 201 });
      }),
    );
    const { user } = renderApp('/pedidos/novo');

    await user.click(await screen.findByRole('button', { name: 'Adicionar Pizza Grande' }));
    const builder = await screen.findByRole('dialog', { name: 'Pizza Grande' });
    await user.click(within(builder).getByRole('checkbox', { name: 'Calabresa' }));
    await user.click(within(builder).getByRole('checkbox', { name: 'Quatro queijos' }));
    await user.type(within(builder).getByRole('textbox', { name: 'Observação do item' }), 'Sem cebola');
    await user.click(await within(builder).findByRole('button', { name: /^Adicionar R\$\s52,90$/ }));
    await user.click(screen.getByRole('button', { name: 'Adicionar Refrigerante lata' }));
    await user.click(screen.getByRole('button', { name: 'Adicionar Refrigerante lata' }));

    const cart = screen.getByLabelText('Itens do pedido');
    expect(within(cart).getByText('1x Pizza Grande')).toBeInTheDocument();
    expect(within(cart).getByText('Calabresa, Quatro queijos')).toBeInTheDocument();
    expect(within(cart).getByText('Obs.: Sem cebola')).toBeInTheDocument();
    expect(within(cart).getByText('2x Refrigerante lata')).toBeInTheDocument();

    await user.click(screen.getByRole('radio', { name: 'Delivery' }));
    await user.type(screen.getByRole('textbox', { name: 'Telefone' }), '11999990000');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));

    expect(await screen.findByText('Cliente cadastrado: Maria Oliveira, (11) 99999-0000.')).toBeInTheDocument();
    expect(lookedUp).toBe('11999990000');
    expect(screen.getByRole('textbox', { name: 'Nome do cliente' })).toHaveValue('Maria Oliveira');
    expect(screen.getByRole('radio', { name: 'Rua das Flores, 120, apto 3 · Centro' })).toBeChecked();
    await waitFor(() => expect(screen.getByRole('textbox', { name: 'Taxa de entrega' })).toHaveValue('R$ 8,00'));

    await user.click(screen.getByRole('combobox', { name: 'Pagamento' }));
    await user.click(await screen.findByRole('option', { name: 'Dinheiro' }));
    await user.type(screen.getByRole('textbox', { name: 'Troco para' }), '100');
    expect(screen.getByText('Levar R$ 25,10 de troco.')).toBeInTheDocument();
    expect(within(screen.getByLabelText('Totais do pedido')).getByText('R$ 74,90')).toBeInTheDocument();
    // Desconto é só com gerente ou dono.
    expect(screen.queryByRole('textbox', { name: 'Desconto' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Lançar pedido' }));

    await waitFor(() =>
      expect(created).toEqual({
        type: 'DELIVERY',
        customer: { name: 'Maria Oliveira', phone: '11999990000' },
        deliveryAddress: {
          street: 'Rua das Flores',
          number: '120',
          complement: 'apto 3',
          neighborhood: 'Centro',
          reference: 'Portão azul',
        },
        items: [
          {
            productId: MENU_IDS.pizza,
            quantity: 1,
            options: [
              { optionId: MENU_IDS.calabresa, quantity: 1 },
              { optionId: MENU_IDS.fourCheese, quantity: 1 },
            ],
            notes: 'Sem cebola',
          },
          { productId: MENU_IDS.soda, quantity: 2, options: [] },
        ],
        discountCents: 0,
        deliveryFeeCents: 800,
        payments: [{ paymentMethodId: ORDER_IDS.cash, amountCents: 7490, changeForCents: 10000, paid: false }],
      }),
    );
    expect(await screen.findByText('Pedido 12 lançado')).toBeInTheDocument();
    expect(screen.getByText('Toque nos produtos do cardápio para adicionar.')).toBeInTheDocument();
  });

  it('no delivery pede cliente e endereço, usa a taxa do bairro e não aceita troco menor que o total', async () => {
    loggedInAs('OWNER');
    serveCheckout();
    let created: unknown = null;
    server.use(
      http.get('/api/customers', () => HttpResponse.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
      http.post('/api/orders', async ({ request }) => {
        created = await request.json();
        return HttpResponse.json(order(), { status: 201 });
      }),
    );
    const { user } = renderApp('/pedidos/novo');

    await user.click(await screen.findByRole('button', { name: 'Adicionar Refrigerante lata' }));
    await user.click(screen.getByRole('radio', { name: 'Delivery' }));
    await user.click(screen.getByRole('button', { name: 'Lançar pedido' }));

    expect(await screen.findByText('Informe o nome do cliente.')).toBeInTheDocument();
    expect(screen.getByText('Informe o telefone para a entrega.')).toBeInTheDocument();
    expect(screen.getByText('Informe a rua.')).toBeInTheDocument();
    expect(screen.getByText('Informe o número (ou s/n).')).toBeInTheDocument();
    expect(screen.getByText('Informe o bairro.')).toBeInTheDocument();

    await user.type(screen.getByRole('textbox', { name: 'Telefone' }), '11988887777');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(await screen.findByText('Cliente novo: fica cadastrado ao lançar o pedido.')).toBeInTheDocument();
    await user.type(screen.getByRole('textbox', { name: 'Nome do cliente' }), 'João');
    await user.type(screen.getByRole('textbox', { name: 'Rua' }), 'Rua B');
    await user.type(screen.getByRole('textbox', { name: 'Número' }), '10');
    const neighborhood = screen.getByRole('textbox', { name: 'Bairro' });
    await user.type(neighborhood, 'centro');
    const fee = screen.getByRole('textbox', { name: 'Taxa de entrega' });
    await waitFor(() => expect(fee).toHaveValue('R$ 8,00'));
    // Outro bairro, sem taxa cadastrada: a taxa do Centro não pode ficar no campo sem ninguém perceber.
    await user.type(neighborhood, ' Novo');
    await waitFor(() => expect(fee).toHaveValue(''));
    expect(screen.getByText('Bairro sem taxa cadastrada: digite a taxa.')).toBeInTheDocument();
    await user.clear(neighborhood);
    await user.type(neighborhood, 'centro');
    await waitFor(() => expect(fee).toHaveValue('R$ 8,00'));

    await user.click(screen.getByRole('combobox', { name: 'Pagamento' }));
    await user.click(await screen.findByRole('option', { name: 'Dinheiro' }));
    const changeFor = screen.getByRole('textbox', { name: 'Troco para' });
    await user.type(changeFor, '10');
    await user.click(screen.getByRole('button', { name: 'Lançar pedido' }));

    expect(await screen.findByText('O troco precisa ser para R$ 15,00 ou mais.')).toBeInTheDocument();
    expect(created).toBeNull();

    await user.clear(changeFor);
    await user.type(changeFor, '20');
    await user.click(screen.getByRole('button', { name: 'Lançar pedido' }));

    // O bairro vai com o nome da lista de taxas, não como foi digitado.
    await waitFor(() =>
      expect(created).toMatchObject({
        customer: { name: 'João', phone: '11988887777' },
        deliveryAddress: { street: 'Rua B', number: '10', neighborhood: 'Centro' },
        deliveryFeeCents: 800,
        payments: [{ paymentMethodId: ORDER_IDS.cash, amountCents: 1500, changeForCents: 2000, paid: false }],
      }),
    );
  });
});

describe('quadro de pedidos', () => {
  function serveBoard(active: OrderSummary[]) {
    server.use(http.get('/api/orders/active', () => HttpResponse.json(active)));
  }

  it('separa os pedidos por etapa e avança o status com a versão que a tela viu', async () => {
    loggedInAs('CASHIER');
    const active = [
      orderSummary(),
      orderSummary({
        id: TAKEOUT_ID,
        number: 13,
        type: 'TAKEOUT',
        status: 'IN_PREPARATION',
        customerName: 'João',
        deliveryNeighborhood: null,
        version: 2,
      }),
      orderSummary({ id: READY_ID, number: 14, status: 'READY', version: 3 }),
    ];
    serveBoard(active);
    let patched: { id: unknown; body: unknown } | null = null;
    server.use(
      http.patch('/api/orders/:id/status', async ({ params, request }) => {
        patched = { id: params.id, body: await request.json() };
        active[0] = { ...active[0], status: 'CONFIRMED', version: 1 };
        return HttpResponse.json(order({ status: 'CONFIRMED', version: 1 }));
      }),
    );
    const { user } = renderApp('/pedidos');

    const received = await screen.findByLabelText('Recebidos');
    const card = within(received).getByLabelText('Pedido 12');
    expect(within(card).getByText('Maria Oliveira · Centro')).toBeInTheDocument();
    expect(within(card).getByText('1x Pizza Grande, 2x Refrigerante lata')).toBeInTheDocument();
    expect(within(screen.getByLabelText('Em produção')).getByLabelText('Pedido 13')).toBeInTheDocument();
    const ready = within(screen.getByLabelText('Prontos')).getByLabelText('Pedido 14');
    // Delivery pronto sai para entrega; retirada em preparo só pode ficar pronta.
    expect(within(ready).getByRole('button', { name: 'Saiu para entrega' })).toBeInTheDocument();
    expect(within(screen.getByLabelText('Pedido 13')).getByRole('button', { name: 'Pronto' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Novo pedido' })).toBeInTheDocument();

    await user.click(within(card).getByRole('button', { name: 'Confirmar' }));

    await waitFor(() => expect(patched).toEqual({ id: ORDER_IDS.order, body: { status: 'CONFIRMED', version: 0 } }));
    expect(await screen.findByText('Pedido 12: Confirmado.')).toBeInTheDocument();
    await waitFor(() =>
      expect(within(screen.getByLabelText('Em produção')).getByLabelText('Pedido 12')).toBeInTheDocument(),
    );
  });

  it('para a cozinha, só mostra iniciar preparo e pronto, e não deixa lançar pedido', async () => {
    loggedInAs('KITCHEN');
    serveBoard([orderSummary(), orderSummary({ id: TAKEOUT_ID, number: 13, status: 'CONFIRMED', version: 1 })]);
    renderApp('/pedidos');

    const received = await screen.findByLabelText('Pedido 12');
    expect(within(received).queryByRole('button')).toHaveAccessibleName('Abrir pedido 12');
    const confirmed = screen.getByLabelText('Pedido 13');
    expect(within(confirmed).getByRole('button', { name: 'Iniciar preparo' })).toBeInTheDocument();
    expect(within(confirmed).getByRole('button', { name: 'Pronto' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Novo pedido' })).not.toBeInTheDocument();
  });

  it('a cozinha que abre o endereço do PDV volta para o início', async () => {
    loggedInAs('KITCHEN');
    renderApp('/pedidos/novo');

    expect(await screen.findByRole('heading', { name: 'Olá, Ana!' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Quadro de pedidos' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Lançar pedido' })).not.toBeInTheDocument();
  });

  it('mostra na hora o pedido lançado em outra tela, pelo tempo real', async () => {
    loggedInAs('MANAGER');
    let active: OrderSummary[] = [];
    server.use(http.get('/api/orders/active', () => HttpResponse.json(active)));
    renderApp('/pedidos');

    expect(await within(await screen.findByLabelText('Recebidos')).findByText('Nenhum pedido.')).toBeInTheDocument();
    await waitFor(() => expect(connectedStreams()).toBe(1));

    active = [orderSummary()];
    pushStreamEvent('order.created', { orderId: ORDER_IDS.order, number: 12, status: 'RECEIVED', version: 0 });

    expect(await within(screen.getByLabelText('Recebidos')).findByLabelText('Pedido 12')).toBeInTheDocument();
  });
});

describe('detalhe do pedido', () => {
  function serveDetail(state: { order: Order; payments: Payment[] }) {
    server.use(
      http.get('/api/orders/active', () => HttpResponse.json([orderSummary({ status: state.order.status })])),
      http.get('/api/orders/:id', () => HttpResponse.json(state.order)),
      http.get('/api/orders/:id/history', () => HttpResponse.json([historyEntry()])),
      http.get('/api/orders/:id/payments', () => HttpResponse.json(state.payments)),
      http.get('/api/payment-methods', () => HttpResponse.json([cash, pix])),
    );
  }

  beforeEach(() => loggedInAs('OWNER'));

  it('mostra itens, entrega, troco e a linha do tempo, e recebe o pagamento', async () => {
    const state = { order: order(), payments: [payment()] };
    serveDetail(state);
    let received: unknown;
    server.use(
      http.patch('/api/orders/:orderId/payments/:paymentId', async ({ request }) => {
        received = await request.json();
        state.payments = [payment({ status: 'PAID', paidAt: '2026-09-24T22:40:00Z' })];
        return HttpResponse.json(state.payments[0]);
      }),
    );
    const { user } = renderApp('/pedidos');

    await user.click(await screen.findByRole('button', { name: 'Abrir pedido 12' }));
    const drawer = await screen.findByRole('dialog', { name: 'Pedido 12' });
    expect(within(drawer).getByText('1x Pizza Grande')).toBeInTheDocument();
    expect(within(drawer).getByText('Calabresa, Quatro queijos')).toBeInTheDocument();
    expect(within(drawer).getByText('Obs.: Sem cebola')).toBeInTheDocument();
    expect(within(drawer).getByText(/Rua das Flores, 120, apto 3 · Centro/)).toBeInTheDocument();
    expect(within(drawer).getByText('R$ 74,90', { selector: 'p.mantine-Text-root[data-size="sm"]:last-child' })).toBeInTheDocument();
    expect(await within(drawer).findByText('Troco para R$ 100,00: levar R$ 25,10')).toBeInTheDocument();
    expect(await within(drawer).findByText('· Ana Souza', { exact: false })).toBeInTheDocument();

    await user.click(within(drawer).getByRole('button', { name: 'Receber' }));

    await waitFor(() => expect(received).toEqual({ status: 'PAID' }));
    expect(await screen.findByText('Dinheiro: R$ 74,90 recebido.')).toBeInTheDocument();
    expect(await within(drawer).findByText('Pago')).toBeInTheDocument();
  });

  it('cancela com o motivo, que fica no pedido', async () => {
    const state = { order: order(), payments: [] as Payment[] };
    serveDetail(state);
    let cancelled: unknown;
    server.use(
      http.patch('/api/orders/:id/status', async ({ request }) => {
        cancelled = await request.json();
        state.order = order({ status: 'CANCELLED', cancelReason: 'Cliente desistiu', version: 1 });
        return HttpResponse.json(state.order);
      }),
    );
    const { user } = renderApp('/pedidos');

    await user.click(await screen.findByRole('button', { name: 'Abrir pedido 12' }));
    const drawer = await screen.findByRole('dialog', { name: 'Pedido 12' });
    await user.click(within(drawer).getByRole('button', { name: 'Cancelar pedido' }));
    const modal = await screen.findByRole('dialog', { name: 'Cancelar pedido 12' });
    const confirm = within(modal).getByRole('button', { name: 'Cancelar pedido' });
    expect(confirm).toBeDisabled();
    await user.type(within(modal).getByRole('textbox', { name: 'Motivo' }), 'Cliente desistiu');
    await user.click(confirm);

    await waitFor(() =>
      expect(cancelled).toEqual({ status: 'CANCELLED', reason: 'Cliente desistiu', version: 0 }),
    );
    expect(await within(drawer).findByText('Pedido cancelado')).toBeInTheDocument();
    expect(within(drawer).getByText('Cliente desistiu')).toBeInTheDocument();
  });
});

describe('histórico de pedidos', () => {
  it('filtra por busca, dia e status, sempre voltando para a primeira página', async () => {
    loggedInAs('MANAGER');
    const requests: URLSearchParams[] = [];
    server.use(
      http.get('/api/orders', ({ request }) => {
        requests.push(new URL(request.url).searchParams);
        return HttpResponse.json({
          content: [orderSummary({ status: 'COMPLETED' })],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        });
      }),
    );
    const { user } = renderApp('/pedidos/historico');

    const row = (await screen.findByText('#12')).closest('tr') as HTMLElement;
    expect(within(row).getByText('Maria Oliveira')).toBeInTheDocument();
    expect(within(row).getByText('Concluído')).toBeInTheDocument();
    expect(within(row).getByText('R$ 74,90')).toBeInTheDocument();

    await user.type(screen.getByRole('textbox', { name: 'Buscar pedido' }), 'maria');
    await waitFor(() => expect(requests.at(-1)?.get('q')).toBe('maria'));
    // Uma consulta para a busca inteira, não uma por tecla.
    expect(requests.filter((params) => params.has('q'))).toHaveLength(1);

    fireEvent.change(screen.getByLabelText('Dia'), { target: { value: '2026-09-24' } });
    await waitFor(() => expect(requests.at(-1)?.get('businessDate')).toBe('2026-09-24'));

    await user.click(screen.getByRole('combobox', { name: 'Status' }));
    await user.click(await screen.findByRole('option', { name: 'Cancelado' }));
    await waitFor(() => expect(requests.at(-1)?.get('status')).toBe('CANCELLED'));
    expect(requests.at(-1)?.get('q')).toBe('maria');
    expect(requests.at(-1)?.get('page')).toBe('0');
  });
});
