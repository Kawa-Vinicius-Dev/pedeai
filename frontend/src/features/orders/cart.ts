import type { OptionChoice } from '../../shared/api/types';

/** Um item no carrinho do PDV. O preço de uma unidade veio da API (mesma conta que o pedido vai fazer). */
export interface CartLine {
  key: string;
  productId: string;
  name: string;
  quantity: number;
  unitPriceCents: number;
  options: OptionChoice[];
  /** "Calabresa, Quatro queijos, Borda Catupiry", para mostrar no carrinho. */
  optionsLabel: string;
  notes: string;
}

let lastKey = 0;

/** Chave local da linha do carrinho (crypto.randomUUID só existe em HTTPS, e o PDV também roda na rede local). */
export function nextLineKey(): string {
  lastKey += 1;
  return `linha-${lastKey}`;
}

export function lineTotal(line: CartLine): number {
  return line.unitPriceCents * line.quantity;
}

export function cartSubtotal(lines: CartLine[]): number {
  return lines.reduce((sum, line) => sum + lineTotal(line), 0);
}

/** Produto sem adicionais e sem observação: clicar de novo soma na mesma linha. */
export function addLine(lines: CartLine[], line: CartLine): CartLine[] {
  const same = lines.find(
    (current) => current.productId === line.productId && current.options.length === 0 && line.options.length === 0
      && current.notes === '' && line.notes === '',
  );
  if (same) {
    return lines.map((current) => (current === same ? { ...current, quantity: current.quantity + line.quantity } : current));
  }
  return [...lines, line];
}

export function changeQuantity(lines: CartLine[], key: string, quantity: number): CartLine[] {
  return quantity <= 0
    ? lines.filter((line) => line.key !== key)
    : lines.map((line) => (line.key === key ? { ...line, quantity } : line));
}
