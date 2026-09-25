import { z } from 'zod';

const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

/** 4590 → "R$ 45,90". A API trafega dinheiro sempre em centavos. */
export function formatCents(cents: number): string {
  return brl.format(cents / 100);
}

/**
 * Valor que o NumberInput do Mantine entrega durante a digitação: texto sem prefixo nem separador de milhar,
 * com ponto como separador decimal ("32.90", "12."). Não remova pontos aqui: "32.90" viraria 3290.
 */
export function parseDecimal(text: string): number {
  const normalized = text.trim().replace(',', '.');
  return normalized === '' ? Number.NaN : Number(normalized);
}

/**
 * Campo numérico de formulário. Enquanto a pessoa digita, o valor pode ser um texto intermediário como
 * "12.", que não pode ser convertido para número a cada tecla (o campo se apagaria). Só vira número aqui,
 * na validação.
 */
export function decimalField(message: string) {
  return z
    .union([z.number(), z.string()])
    .transform((value) => (typeof value === 'number' ? value : parseDecimal(value)))
    .pipe(z.number({ error: message }));
}

/** Valor em reais no formulário, centavos na saída. */
export function moneyField(message: string) {
  return decimalField(message)
    .pipe(z.number().min(0, 'O valor não pode ser negativo.').max(100_000, 'Valor acima do permitido.'))
    .transform((reais) => Math.round(reais * 100));
}

/** Centavos da API para o valor do campo em reais. */
export function centsToReais(cents: number): number {
  return cents / 100;
}
