import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Coupon, Courier } from '../../shared/api/types';
import { historyEntry, order, orderSummary } from '../../test/fixtures';
import { loggedInAs, renderApp } from '../../test/render';
import { server } from '../../test/server';

const JOAO: Courier = { id: '01a0d567-0000-7000-8000-0000000000c1', name: 'João', phone: null, active: true };

function coupon(overrides: Partial<Coupon> = {}): Coupon {
  return {
    id: '01a0d567-0000-7000-8000-0000000000c9',
    code: 'BEMVINDO10',
    type: 'PERCENT',
    amount: 1000,
    minOrderCents: 5000,
    maxUses: 100,
    usedCount: 3,
    validUntil: '2026-12-31',
    active: true,
    ...overrides,
  };
}

describe('cupons de desconto', () => {
  it('lista os cupons e cria um de valor fixo, enviando centavos', async () => {
    loggedInAs('MANAGER');
    const coupons = [coupon()];
    let created: unknown;
    server.use(
      http.get('/api/coupons', () => HttpResponse.json(coupons)),
      http.post('/api/coupons', async ({ request }) => {
        created = await request.json();
        const saved = coupon({ id: '01a0d567-0000-7000-8000-0000000000ca', code: 'FRETE5', type: 'FIXED', amount: 500 });
        coupons.push(saved);
        return HttpResponse.json(saved, { status: 201 });
      }),
    );
    const { user } = renderApp('/configuracoes/cupons');

    const row = (await screen.findByText('BEMVINDO10')).closest('tr') as HTMLElement;
    expect(within(row).getByText('10%')).toBeInTheDocument();
    expect(within(row).getByText('R$ 50,00')).toBeInTheDocument();
    expect(within(row).getByText('3 de 100')).toBeInTheDocument();
    expect(within(row).getByText('31/12/2026')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Novo cupom' }));
    const dialog = await screen.findByRole('dialog', { name: 'Novo cupom' });
    await user.type(within(dialog).getByRole('textbox', { name: 'Código' }), 'frete5');
    await user.click(within(dialog).getByRole('radio', { name: 'Valor fixo' }));
    const discount = within(dialog).getByRole('textbox', { name: 'Desconto' });
    await user.clear(discount);
    await user.type(discount, '5');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() =>
      expect(created).toEqual({
        code: 'frete5',
        type: 'FIXED',
        amount: 500,
        minOrderCents: 0,
        maxUses: null,
        validUntil: null,
        active: true,
      }),
    );
    expect(await screen.findByText('FRETE5')).toBeInTheDocument();
  });

  it('manda a porcentagem em pontos-base', async () => {
    loggedInAs('OWNER');
    let created: { amount?: number; type?: string } = {};
    server.use(
      http.get('/api/coupons', () => HttpResponse.json([])),
      http.post('/api/coupons', async ({ request }) => {
        created = (await request.json()) as typeof created;
        return HttpResponse.json(coupon({ code: 'QUINZE', amount: 1500 }), { status: 201 });
      }),
    );
    const { user } = renderApp('/configuracoes/cupons');

    await user.click(await screen.findByRole('button', { name: 'Novo cupom' }));
    const dialog = await screen.findByRole('dialog', { name: 'Novo cupom' });
    await user.type(within(dialog).getByRole('textbox', { name: 'Código' }), 'QUINZE');
    const discount = within(dialog).getByRole('textbox', { name: 'Desconto' });
    await user.clear(discount);
    await user.type(discount, '15');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(created).toMatchObject({ type: 'PERCENT', amount: 1500 }));
  });
});

describe('entregadores', () => {
  it('cadastra um entregador e mostra o acerto do dia', async () => {
    loggedInAs('MANAGER');
    const couriers: Courier[] = [];
    let created: unknown;
    server.use(
      http.get('/api/couriers', () => HttpResponse.json(couriers)),
      http.post('/api/couriers', async ({ request }) => {
        created = await request.json();
        couriers.push(JOAO);
        return HttpResponse.json(JOAO, { status: 201 });
      }),
      http.get('/api/couriers/settlement', () =>
        HttpResponse.json([
          { courierId: JOAO.id, courierName: 'João', deliveries: 4, deliveryFeeCents: 3200 },
          { courierId: null, courierName: null, deliveries: 1, deliveryFeeCents: 600 },
        ]),
      ),
    );
    const { user } = renderApp('/configuracoes/entregadores');

    expect(await screen.findByText('Nenhum entregador cadastrado ainda.')).toBeInTheDocument();
    const settlement = await screen.findByLabelText('Acerto do dia');
    const joao = (await within(settlement).findByText('João')).closest('tr') as HTMLElement;
    expect(within(joao).getByText('4')).toBeInTheDocument();
    expect(within(joao).getByText('R$ 32,00')).toBeInTheDocument();
    expect(within(settlement).getByText('Sem entregador')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Novo entregador' }));
    const dialog = await screen.findByRole('dialog', { name: 'Novo entregador' });
    await user.type(within(dialog).getByRole('textbox', { name: 'Nome' }), 'João');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(created).toEqual({ name: 'João', phone: null, active: true }));
    expect(await screen.findByRole('button', { name: 'Editar João' })).toBeInTheDocument();
  });

  it('no detalhe do pedido de delivery, escolhe quem leva', async () => {
    loggedInAs('CASHIER');
    let assigned: unknown;
    server.use(
      http.get('/api/orders/active', () => HttpResponse.json([orderSummary()])),
      http.get('/api/orders/:id', () => HttpResponse.json(order())),
      http.get('/api/orders/:id/history', () => HttpResponse.json([historyEntry()])),
      http.get('/api/orders/:id/payments', () => HttpResponse.json([])),
      http.get('/api/payment-methods', () => HttpResponse.json([])),
      http.get('/api/couriers', () => HttpResponse.json([JOAO, { ...JOAO, id: 'inativo', name: 'Pedro', active: false }])),
      http.patch('/api/orders/:id/courier', async ({ request }) => {
        assigned = await request.json();
        return HttpResponse.json(order({ courierId: JOAO.id, courierName: 'João' }));
      }),
    );
    const { user } = renderApp('/pedidos');

    await user.click(await screen.findByRole('button', { name: 'Abrir pedido 12' }));
    const drawer = await screen.findByRole('dialog', { name: 'Pedido 12' });
    await user.click(await within(drawer).findByRole('combobox', { name: 'Entregador' }));
    // Entregador inativo não é oferecido.
    expect(screen.queryByRole('option', { name: 'Pedro' })).not.toBeInTheDocument();
    await user.click(await screen.findByRole('option', { name: 'João' }));

    await waitFor(() => expect(assigned).toEqual({ courierId: JOAO.id }));
    await waitFor(() => expect(within(drawer).getByRole('combobox', { name: 'Entregador' })).toHaveValue('João'));
  });
});
