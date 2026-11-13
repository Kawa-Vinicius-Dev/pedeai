import { describe, expect, it } from 'vitest';
import { whatsappLink } from './labels';

const order = {
  number: 12,
  status: 'READY',
  type: 'DELIVERY',
  source: 'PEDEAI',
  customerName: 'Maria Oliveira',
  customerPhone: '+5511999990000',
} as const;

describe('aviso pelo WhatsApp', () => {
  it('monta o link com o telefone do cliente e o aviso do status', () => {
    const link = whatsappLink(order, 'Pizzaria Bella');

    expect(link).toMatch(/^https:\/\/wa\.me\/5511999990000\?text=/);
    expect(decodeURIComponent(link?.split('text=')[1] ?? '')).toBe(
      'Olá, Maria! Aqui é da Pizzaria Bella. Pedido 12: o seu pedido está pronto e já vai sair para entrega.',
    );
    expect(decodeURIComponent(whatsappLink({ ...order, type: 'TAKEOUT' }, 'Pizzaria Bella') ?? '')).toContain(
      'pronto para retirada',
    );
  });

  it('não oferece o aviso sem telefone nem em pedido de marketplace', () => {
    expect(whatsappLink({ ...order, customerPhone: null }, 'Pizzaria Bella')).toBeNull();
    expect(whatsappLink({ ...order, source: 'IFOOD' }, 'Pizzaria Bella')).toBeNull();
  });
});
