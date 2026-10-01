import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { ApiKey } from '../../shared/api/types';
import { store } from '../../test/fixtures';
import { loggedInAs, renderApp } from '../../test/render';
import { server } from '../../test/server';

const KEY: ApiKey = {
  id: '01a0d567-0000-7000-8000-0000000000e1',
  name: 'Site da loja',
  keyPrefix: 'pk_AbCdEfG',
  createdAt: '2026-10-01T12:00:00Z',
  lastUsedAt: null,
  revokedAt: null,
};

describe('API de pedidos', () => {
  it('cria a chave, mostra uma vez só e revoga', async () => {
    loggedInAs('OWNER');
    let keys: ApiKey[] = [];
    let created: unknown;
    let revoked: string | undefined;
    server.use(
      http.get('/api/api-keys', () => HttpResponse.json(keys)),
      http.post('/api/api-keys', async ({ request }) => {
        created = await request.json();
        keys = [KEY];
        return HttpResponse.json({ key: KEY, secret: 'pk_segredo-de-teste' }, { status: 201 });
      }),
      http.delete('/api/api-keys/:id', ({ params }) => {
        revoked = params.id as string;
        keys = [{ ...KEY, revokedAt: '2026-10-01T13:00:00Z' }];
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderApp('/configuracoes/api-de-pedidos');

    expect(await screen.findByText('Nenhuma chave criada.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Nova chave' }));
    const dialog = await screen.findByRole('dialog', { name: 'Nova chave da API' });
    await user.type(within(dialog).getByLabelText('Nome'), 'Site da loja');
    await user.click(within(dialog).getByRole('button', { name: 'Criar chave' }));

    expect(await within(dialog).findByText('pk_segredo-de-teste')).toBeInTheDocument();
    expect(created).toEqual({ name: 'Site da loja' });
    await user.click(within(dialog).getByRole('button', { name: 'Pronto' }));

    const table = await screen.findByRole('table', { name: 'Chaves da API' });
    expect(within(table).queryByText('pk_segredo-de-teste')).not.toBeInTheDocument();
    await user.click(within(table).getByRole('button', { name: 'Revogar' }));
    const confirm = await screen.findByRole('dialog', { name: 'Revogar chave' });
    await user.click(within(confirm).getByRole('button', { name: 'Revogar' }));
    await waitFor(() => expect(revoked).toBe(KEY.id));
    expect(await within(table).findByText('Revogada')).toBeInTheDocument();
  });
});

describe('cardápio digital: horário e aceite automático', () => {
  it('salva o horário de segunda e o aceite automático', async () => {
    loggedInAs('OWNER');
    let patch: Record<string, unknown> | undefined;
    server.use(
      http.patch('/api/store', async ({ request }) => {
        patch = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(store({ menuAutoConfirm: true }));
      }),
    );
    const { user } = renderApp('/configuracoes/loja');

    await user.click(await screen.findByRole('switch', { name: /Aceitar pedidos do cardápio automaticamente/ }));
    await user.click(screen.getByRole('switch', { name: /Usar horário de funcionamento/ }));
    const week = await screen.findByLabelText('Horário de funcionamento');
    await user.click(screen.getByRole('button', { name: 'Salvar' }));
    expect(await within(week).findByText('Marque pelo menos um dia, ou desligue o horário.')).toBeInTheDocument();

    await user.click(within(week).getByLabelText('Segunda'));
    const opens = within(week).getByLabelText('Segunda: abre às');
    await user.clear(opens);
    await user.type(opens, '11:00');
    const closes = within(week).getByLabelText('Segunda: fecha às');
    await user.clear(closes);
    await user.type(closes, '15:00');
    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    await waitFor(() =>
      expect(patch).toMatchObject({
        menuAutoConfirm: true,
        openingHours: [{ dayOfWeek: 1, opensAt: '11:00', closesAt: '15:00' }],
      }),
    );
  });
});
