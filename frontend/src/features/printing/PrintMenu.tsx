import { Button, Menu, SegmentedControl } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { Printer } from 'lucide-react';
import { useState } from 'react';
import { createPortal, flushSync } from 'react-dom';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { Order, TicketDocument } from '../../shared/api/types';
import { useSession } from '../auth/auth-context';
import { useSectors } from '../catalog/api';
import './print.css';

const PAPER_KEY = 'pedeai.print.paper';
/** Colunas na fonte normal e largura útil de impressão de cada papel (docs/04-impressao.md#escpos-na-prática). */
const PAPERS = {
  '80': { columns: 48, printableMm: 72 },
  '58': { columns: 32, printableMm: 48 },
} as const;
type Paper = keyof typeof PAPERS;

/**
 * Impressão pelo navegador (contingência, sem agente): o servidor monta o documento e o navegador abre o diálogo
 * de impressão só com o ticket.
 */
export function PrintMenu({ order }: { order: Order }) {
  const { user } = useSession();
  const sectors = useSectors();
  const [paper, setPaper] = useState<Paper>(readPaper);
  const [ticket, setTicket] = useState<TicketDocument | null>(null);
  const [loading, setLoading] = useState(false);

  const orderSectors = (sectors.data ?? []).filter((sector) =>
    order.items.some((item) => item.status === 'ACTIVE' && item.sectorId === sector.id),
  );

  async function print(documentType: TicketDocument['documentType'], sectorId?: string) {
    setLoading(true);
    try {
      const printable = await unwrap(
        api.GET('/api/orders/{orderId}/tickets/{documentType}', {
          params: { path: { orderId: order.id, documentType }, query: { sectorId, columns: PAPERS[paper].columns } },
        }),
      );
      // O ticket precisa estar na página antes do diálogo abrir; o print() só volta quando o diálogo fecha.
      flushSync(() => setTicket(printable));
      window.print();
      setTicket(null);
    } catch (error) {
      notifications.show({ color: 'red', message: errorMessage(error) });
    } finally {
      setLoading(false);
    }
  }

  function choosePaper(value: string) {
    setPaper(value as Paper);
    try {
      localStorage.setItem(PAPER_KEY, value);
    } catch {
      // Sem armazenamento: vale só nesta tela.
    }
  }

  return (
    <>
      <Menu position="bottom-start" closeOnItemClick>
        <Menu.Target>
          <Button variant="default" leftSection={<Printer size={16} />} loading={loading}>
            Imprimir
          </Button>
        </Menu.Target>
        <Menu.Dropdown>
          <SegmentedControl
            fullWidth
            size="xs"
            aria-label="Papel"
            value={paper}
            onChange={choosePaper}
            data={[
              { value: '80', label: '80mm' },
              { value: '58', label: '58mm' },
            ]}
          />
          {user.role !== 'KITCHEN' && (
            <Menu.Item onClick={() => void print('ORDER_TICKET')}>Via completa</Menu.Item>
          )}
          {orderSectors.map((sector) => (
            <Menu.Item key={sector.id} onClick={() => void print('PRODUCTION_TICKET', sector.id)}>
              Produção · {sector.name}
            </Menu.Item>
          ))}
        </Menu.Dropdown>
      </Menu>
      {ticket && createPortal(<Ticket ticket={ticket} paper={paper} />, document.body)}
    </>
  );
}

function Ticket({ ticket, paper }: { ticket: TicketDocument; paper: Paper }) {
  // Tamanho da letra para as colunas ocuparem a largura útil (a letra monoespaçada tem ~0,6 em de largura).
  const fontSize = `${(PAPERS[paper].printableMm / (ticket.columns * 0.6)).toFixed(2)}mm`;
  return (
    <div className="ticket-print" style={{ fontSize, width: `${PAPERS[paper].printableMm}mm` }}>
      {ticket.lines.map((line, index) => (
        <pre
          key={index}
          className={[line.bold && 'bold', line.big && 'big', line.align === 'CENTER' && 'center']
            .filter(Boolean)
            .join(' ')}
        >
          {line.text || ' '}
        </pre>
      ))}
    </div>
  );
}

function readPaper(): Paper {
  try {
    return localStorage.getItem(PAPER_KEY) === '58' ? '58' : '80';
  } catch {
    return '80';
  }
}
