import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import type { PrintAgent, Printer, SectorPrinter } from '../../shared/api/types';
import { MENU_IDS, pizzeriaMenu } from '../../test/fixtures';
import { loggedInAs, renderApp } from '../../test/render';
import { server, serveMenu } from '../../test/server';

const AGENT_ID = '01a0d567-0000-7000-8000-0000000000c1';
const PRINTER_ID = '01a0d567-0000-7000-8000-0000000000c2';

const agent: PrintAgent = {
  id: AGENT_ID,
  name: 'Caixa',
  os: 'Windows 11',
  agentVersion: '0.1.0',
  online: true,
  lastSeenAt: '2026-09-27T12:00:00Z',
  createdAt: '2026-09-27T11:00:00Z',
};

const kitchenPrinter: Printer = {
  id: PRINTER_ID,
  agentId: AGENT_ID,
  name: 'Cozinha',
  connectionType: 'NETWORK',
  host: '192.168.0.50',
  port: 9100,
  systemName: null,
  paperWidthMm: 80,
  columns: 48,
  codepage: 'PC860',
  cutMode: 'PARTIAL',
  active: true,
  status: 'ERROR',
  statusDetail: 'Sem papel',
  statusUpdatedAt: '2026-09-27T12:00:00Z',
};

describe('configurações de impressão', () => {
  beforeEach(() => {
    loggedInAs('OWNER');
    serveMenu(pizzeriaMenu());
  });

  function serve(state: { printers: Printer[]; sectorPrinters: SectorPrinter[] }) {
    const received: { printer?: unknown; sector?: { id: string; body: unknown } } = {};
    server.use(
      http.get('/api/print-agents', () => HttpResponse.json([agent])),
      http.post('/api/print-agents/pairing-codes', () =>
        HttpResponse.json({ code: '482913', expiresAt: '2026-09-27T12:10:00Z' }, { status: 201 }),
      ),
      http.get('/api/printers', () => HttpResponse.json(state.printers)),
      http.post('/api/printers', async ({ request }) => {
        received.printer = await request.json();
        state.printers = [{ ...kitchenPrinter, id: 'nova', name: 'Bar', connectionType: 'SYSTEM', status: 'UNKNOWN' }];
        return HttpResponse.json(state.printers[0], { status: 201 });
      }),
      http.get('/api/sector-printers', () => HttpResponse.json(state.sectorPrinters)),
      http.put('/api/sectors/:sectorId/printer', async ({ request, params }) => {
        received.sector = { id: params.sectorId as string, body: await request.json() };
        return HttpResponse.json({ sectorId: params.sectorId, ...(received.sector.body as object) });
      }),
    );
    return received;
  }

  it('mostra o código de pareamento e o status de computadores e impressoras', async () => {
    serve({ printers: [kitchenPrinter], sectorPrinters: [] });
    const { user } = renderApp('/configuracoes/impressao');

    expect(await within(await screen.findByLabelText('Computador Caixa')).findByText('Online')).toBeInTheDocument();
    expect(await screen.findByText('Com problema')).toBeInTheDocument();
    expect(screen.getByText('Sem papel')).toBeInTheDocument();
    expect(screen.getByText('Rede 192.168.0.50:9100')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Adicionar computador' }));
    expect(await screen.findByLabelText('Código de pareamento')).toHaveTextContent('482913');
  });

  it('cadastra impressora USB pelo nome no Windows, com papel de 58mm', async () => {
    const received = serve({ printers: [], sectorPrinters: [] });
    const { user } = renderApp('/configuracoes/impressao');

    await user.click(await screen.findByRole('button', { name: 'Nova impressora' }));
    const dialog = await screen.findByRole('dialog', { name: 'Nova impressora' });
    await user.type(within(dialog).getByLabelText('Nome'), 'Bar');
    await user.click(within(dialog).getByText('USB pelo Windows'));
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));
    expect(await within(dialog).findByText('Informe o nome exato no Windows.')).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText('Nome no Windows'), 'ELGIN i9');
    await user.click(within(dialog).getByRole('combobox', { name: 'Papel' }));
    await user.click(await screen.findByRole('option', { name: '58mm' }));
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() =>
      expect(received.printer).toEqual({
        agentId: AGENT_ID,
        name: 'Bar',
        connectionType: 'SYSTEM',
        systemName: 'ELGIN i9',
        paperWidthMm: 58,
        columns: 32,
        codepage: 'PC860',
        cutMode: 'PARTIAL',
        active: true,
      }),
    );
  });

  it('liga o setor a uma impressora', async () => {
    const received = serve({ printers: [kitchenPrinter], sectorPrinters: [] });
    const { user } = renderApp('/configuracoes/impressao');

    const row = await screen.findByLabelText('Setor Cozinha');
    await user.click(within(row).getByRole('combobox', { name: 'Impressora' }));
    await user.click(await screen.findByRole('option', { name: 'Cozinha' }));
    await user.click(within(row).getByRole('button', { name: 'Salvar' }));

    await waitFor(() =>
      expect(received.sector).toEqual({
        id: MENU_IDS.kitchen,
        body: { printerId: PRINTER_ID, copies: 1, enabled: true },
      }),
    );
  });
});
