import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import type { User } from '../../shared/api/types';
import { apiError, store, user as userFixture } from '../../test/fixtures';
import { loggedInAs, renderApp } from '../../test/render';
import { server } from '../../test/server';

const owner = userFixture();
const cashier = userFixture({
  id: '01a0d567-0000-7000-8000-000000000003',
  name: 'Caio Lima',
  email: 'caio@example.com',
  role: 'CASHIER',
});

describe('equipe', () => {
  beforeEach(() => loggedInAs('OWNER'));

  it('lista as pessoas da loja com o papel de cada uma', async () => {
    server.use(http.get('/api/users', () => HttpResponse.json([owner, cashier])));
    renderApp('/configuracoes/equipe');

    const row = (await screen.findByText('caio@example.com')).closest('tr');
    expect(row).not.toBeNull();
    expect(within(row as HTMLElement).getByText('Caixa')).toBeInTheDocument();
    expect(within(row as HTMLElement).getByText('Ativo')).toBeInTheDocument();
    expect(screen.getByText('(você)')).toBeInTheDocument();
  });

  it('adiciona uma pessoa com o papel escolhido', async () => {
    const team: User[] = [owner];
    let created: unknown;
    server.use(
      http.get('/api/users', () => HttpResponse.json(team)),
      http.post('/api/users', async ({ request }) => {
        created = await request.json();
        const bia = userFixture({ id: '01a0d567-0000-7000-8000-000000000004', name: 'Bia', email: 'bia@example.com', role: 'WAITER' });
        team.push(bia);
        return HttpResponse.json(bia, { status: 201 });
      }),
    );
    const { user } = renderApp('/configuracoes/equipe');

    await user.click(await screen.findByRole('button', { name: 'Adicionar pessoa' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Nome'), 'Bia');
    await user.type(within(dialog).getByLabelText('E-mail'), 'bia@example.com');
    await user.type(within(dialog).getByLabelText('Senha inicial', { selector: 'input' }), 'senha-da-bia');
    await user.click(within(dialog).getByRole('combobox', { name: 'Papel' }));
    await user.click(await screen.findByRole('option', { name: 'Garçom' }));
    await user.click(within(dialog).getByRole('button', { name: 'Adicionar' }));

    await waitFor(() =>
      expect(created).toEqual({ name: 'Bia', email: 'bia@example.com', password: 'senha-da-bia', role: 'WAITER' }),
    );
    expect(await screen.findByText('bia@example.com')).toBeInTheDocument();
  });

  it('mostra no formulário o conflito devolvido pela API', async () => {
    server.use(
      http.get('/api/users', () => HttpResponse.json([owner])),
      http.post('/api/users', () => HttpResponse.json(apiError(409, 'Este e-mail já está em uso.'), { status: 409 })),
    );
    const { user } = renderApp('/configuracoes/equipe');

    await user.click(await screen.findByRole('button', { name: 'Adicionar pessoa' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Nome'), 'Caio');
    await user.type(within(dialog).getByLabelText('E-mail'), 'caio@example.com');
    await user.type(within(dialog).getByLabelText('Senha inicial', { selector: 'input' }), 'senha-do-caio');
    await user.click(within(dialog).getByRole('button', { name: 'Adicionar' }));

    expect(await within(dialog).findByText('Este e-mail já está em uso.')).toBeInTheDocument();
  });
});

describe('dados da loja', () => {
  beforeEach(() => loggedInAs('OWNER'));

  it('mostra a taxa em % e salva em pontos-base', async () => {
    let patch: unknown;
    server.use(
      http.get('/api/store', () => HttpResponse.json(store())),
      http.patch('/api/store', async ({ request }) => {
        patch = await request.json();
        return HttpResponse.json(store({ serviceFeeBp: 1250 }));
      }),
    );
    const { user } = renderApp('/configuracoes/loja');

    const fee = await screen.findByLabelText('Taxa de serviço (mesas)');
    await waitFor(() => expect(fee).toHaveValue('10%'));
    // Digita como a pessoa faria: o texto passa por "12," antes de virar número, e não pode se perder.
    await user.tripleClick(fee);
    await user.keyboard('12,5');
    await waitFor(() => expect(fee).toHaveValue('12,5%'));
    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(patch).toMatchObject({ serviceFeeBp: 1250, businessDayCutoff: '05:00' }));
  });
});
