import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { CreateOrderRequest, LoyaltyStatus } from '../../shared/api/types';
import { customer, deliveryZone, order, ORDER_IDS, paymentMethod, pizzeriaMenu, store } from '../../test/fixtures';
import { loggedInAs, renderApp } from '../../test/render';
import { server, serveMenu } from '../../test/server';

describe('estoque simples', () => {
  it('mostra o estoque e conta quanto sobrou', async () => {
    loggedInAs('CASHIER');
    const menu = pizzeriaMenu();
    menu.products[1] = { ...menu.products[1], stockQuantity: 0, available: false };
    serveMenu(menu);
    let body: unknown;
    server.use(
      http.put('/api/products/:id/stock', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...menu.products[0], stockQuantity: 12 });
      }),
    );
    const { user } = renderApp('/cardapio/produtos');

    expect(await screen.findByText('Sem estoque')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Estoque de Pizza Grande' }));
    const dialog = await screen.findByRole('dialog', { name: 'Estoque de Pizza Grande' });
    // Produto que não controla estoque não tem o botão de deixar de controlar.
    expect(within(dialog).queryByRole('button', { name: 'Não controlar estoque' })).not.toBeInTheDocument();
    await user.type(within(dialog).getByRole('textbox', { name: 'Quantidade disponível' }), '12');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(body).toEqual({ quantity: 12 }));
    expect(await screen.findByText('Estoque: 12')).toBeInTheDocument();
  });

  it('deixa de controlar o estoque', async () => {
    loggedInAs('OWNER');
    const menu = pizzeriaMenu();
    menu.products[0] = { ...menu.products[0], stockQuantity: 4 };
    serveMenu(menu);
    let body: unknown;
    server.use(
      http.put('/api/products/:id/stock', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...menu.products[0], stockQuantity: null });
      }),
    );
    const { user } = renderApp('/cardapio/produtos');

    expect(await screen.findByText('Estoque: 4')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Estoque de Pizza Grande' }));
    const dialog = await screen.findByRole('dialog', { name: 'Estoque de Pizza Grande' });
    expect(within(dialog).getByRole('textbox', { name: 'Quantidade disponível' })).toHaveValue('4');
    await user.click(within(dialog).getByRole('button', { name: 'Não controlar estoque' }));

    await waitFor(() => expect(body).toEqual({ quantity: null }));
    await waitFor(() => expect(screen.queryByText('Estoque: 4')).not.toBeInTheDocument());
  });
});

describe('fidelidade', () => {
  it('liga o programa com o desconto em centavos', async () => {
    loggedInAs('MANAGER');
    let body: unknown;
    server.use(
      http.get('/api/coupons', () => HttpResponse.json([])),
      http.get('/api/store', () => HttpResponse.json(store())),
      http.put('/api/store/loyalty', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(store({ loyaltyOrders: 8, loyaltyDiscountCents: 1500 }));
      }),
    );
    const { user } = renderApp('/configuracoes/cupons');

    await user.click(await screen.findByRole('switch', { name: 'Ligar o programa de fidelidade' }));
    const orders = screen.getByRole('textbox', { name: 'A cada quantos pedidos' });
    await user.clear(orders);
    await user.type(orders, '8');
    await user.click(screen.getByRole('button', { name: 'Salvar fidelidade' }));
    expect(await screen.findByText('Informe o valor do desconto.')).toBeInTheDocument();

    await user.type(screen.getByRole('textbox', { name: 'Desconto do prêmio' }), '15');
    await user.click(screen.getByRole('button', { name: 'Salvar fidelidade' }));

    await waitFor(() => expect(body).toEqual({ orders: 8, discountCents: 1500 }));
    expect(await screen.findByText('Programa de fidelidade salvo.')).toBeInTheDocument();
  });

  it('no PDV, o caixa usa o prêmio do cliente e o total já sai com o desconto', async () => {
    loggedInAs('CASHIER');
    serveMenu(pizzeriaMenu());
    const reward: LoyaltyStatus = {
      enabled: true,
      completedOrders: 10,
      ordersToNext: 0,
      rewardAvailable: true,
      rewardCents: 1000,
    };
    let created: CreateOrderRequest | null = null;
    server.use(
      http.get('/api/payment-methods', () => HttpResponse.json([paymentMethod()])),
      http.get('/api/delivery-zones', () => HttpResponse.json([deliveryZone()])),
      http.get('/api/customers', () =>
        HttpResponse.json({ content: [customer()], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
      ),
      http.get(`/api/customers/${ORDER_IDS.maria}/loyalty`, () => HttpResponse.json(reward)),
      http.post('/api/orders', async ({ request }) => {
        created = (await request.json()) as CreateOrderRequest;
        return HttpResponse.json(order(), { status: 201 });
      }),
    );
    const { user } = renderApp('/pedidos/novo');

    await user.click(await screen.findByRole('button', { name: 'Adicionar Refrigerante lata' }));
    await user.click(screen.getByRole('button', { name: 'Adicionar Refrigerante lata' }));
    await user.type(screen.getByRole('textbox', { name: 'Telefone' }), '11999990000');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));

    const use = await screen.findByRole('checkbox', { name: /Usar o prêmio de fidelidade: R\$\s10,00/ });
    await user.click(use);
    const totals = screen.getByLabelText('Totais do pedido');
    expect(within(totals).getByText('Prêmio de fidelidade')).toBeInTheDocument();
    expect(within(totals).getByText('R$ 4,00')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Lançar pedido' }));
    await waitFor(() => expect(created).not.toBeNull());
    expect(created!.loyaltyReward).toBe(true);
    expect(created!.discountCents).toBe(0);
  });

  it('no PDV, mostra quanto falta para o próximo prêmio', async () => {
    loggedInAs('CASHIER');
    serveMenu(pizzeriaMenu());
    server.use(
      http.get('/api/payment-methods', () => HttpResponse.json([paymentMethod()])),
      http.get('/api/delivery-zones', () => HttpResponse.json([])),
      http.get('/api/customers', () =>
        HttpResponse.json({ content: [customer()], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
      ),
      http.get(`/api/customers/${ORDER_IDS.maria}/loyalty`, () =>
        HttpResponse.json({ enabled: true, completedOrders: 7, ordersToNext: 3, rewardAvailable: false, rewardCents: 0 }),
      ),
    );
    const { user } = renderApp('/pedidos/novo');

    await user.type(await screen.findByRole('textbox', { name: 'Telefone' }), '11999990000');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));

    expect(
      await screen.findByText('Fidelidade: faltam 3 pedido(s) concluído(s) para o próximo prêmio.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: /Usar o prêmio/ })).not.toBeInTheDocument();
  });
});
