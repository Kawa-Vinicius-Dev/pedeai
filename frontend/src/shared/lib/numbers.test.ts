import { describe, expect, it } from 'vitest';
import { formatCents, moneyField } from './numbers';

describe('moneyField', () => {
  const field = moneyField('Informe o preço.');

  // O NumberInput do Mantine entrega número ou, no meio da digitação, texto com ponto decimal.
  it.each([
    [32.9, 3290],
    ['32.90', 3290],
    ['12.', 1200],
    ['0.05', 5],
    [0, 0],
    [1234.56, 123456],
  ])('%s vira %i centavos', (input, cents) => {
    expect(field.parse(input)).toBe(cents);
  });

  it('recusa campo vazio com a mensagem do campo', () => {
    const result = field.safeParse('');
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.message).toBe('Informe o preço.');
  });

  it('recusa valor negativo', () => {
    expect(field.safeParse(-1).success).toBe(false);
  });
});

describe('formatCents', () => {
  it('formata centavos em reais', () => {
    expect(formatCents(4590)).toBe('R$ 45,90');
    expect(formatCents(123456)).toBe('R$ 1.234,56');
  });
});
