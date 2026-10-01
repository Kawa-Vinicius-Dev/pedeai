import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { CatalogImport } from '../../shared/api/types';
import { pizzeriaMenu } from '../../test/fixtures';
import { loggedInAs, renderApp } from '../../test/render';
import { server, serveMenu } from '../../test/server';

describe('importar cardápio de planilha', () => {
  it('pré-visualiza, mostra os erros por linha e só importa sem erro', async () => {
    loggedInAs('OWNER');
    serveMenu(pizzeriaMenu());
    const sent: { content: string; dryRun: boolean }[] = [];
    server.use(
      http.post('/api/catalog/imports/spreadsheet', async ({ request }) => {
        const body = (await request.json()) as { content: string; dryRun: boolean };
        sent.push(body);
        const broken = body.content.includes('trinta');
        const result: CatalogImport = {
          applied: !body.dryRun && !broken,
          categoriesCreated: broken ? 0 : 1,
          productsCreated: broken ? 0 : 2,
          productsUpdated: 0,
          optionGroupsCreated: 0,
          errors: broken ? [{ origin: 'linha 3', message: 'Preço inválido: "trinta" (use 12,90).' }] : [],
        };
        return HttpResponse.json(result);
      }),
    );
    const { user } = renderApp('/cardapio/produtos');

    await user.click(await screen.findByRole('button', { name: 'Importar planilha' }));
    const dialog = await screen.findByRole('dialog', { name: 'Importar cardápio de planilha' });
    const input = dialog.querySelector('input[type="file"]') as HTMLInputElement;

    await user.upload(input, new File(['categoria;produto;preco\nLanches;X-Burger;10\nLanches;X;trinta\n'],
      'cardapio.csv', { type: 'text/csv' }));
    await user.click(within(dialog).getByRole('button', { name: 'Pré-visualizar' }));
    expect(await within(dialog).findByText('Nada foi importado')).toBeInTheDocument();
    expect(within(dialog).getByText(/Preço inválido/)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Importar' })).toBeDisabled();

    await user.upload(input, new File(['categoria;produto;preco\nLanches;X-Burger;10\nLanches;X-Salada;12\n'],
      'cardapio.csv', { type: 'text/csv' }));
    await user.click(within(dialog).getByRole('button', { name: 'Pré-visualizar' }));
    expect(await within(dialog).findByText('Pré-visualização: nada foi gravado ainda.')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Importar' }));

    expect(await within(dialog).findByText('Cardápio importado.')).toBeInTheDocument();
    await waitFor(() => expect(sent.at(-1)).toMatchObject({ dryRun: false }));
    expect(sent.at(-1)?.content).toContain('X-Salada');
  });
});
