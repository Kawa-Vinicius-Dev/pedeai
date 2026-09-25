import type { PricingRule } from '../../shared/api/types';

export const PRICING_RULES: PricingRule[] = ['SUM', 'MAX', 'AVERAGE'];

export const PRICING_RULE_LABELS: Record<PricingRule, string> = {
  SUM: 'Soma',
  MAX: 'Maior valor',
  AVERAGE: 'Média',
};

export const PRICING_RULE_HELP: Record<PricingRule, string> = {
  SUM: 'Cada opção escolhida soma ao preço. Ex.: adicionais do lanche.',
  MAX: 'Cobra a opção mais cara. Ex.: pizza meio a meio pelo sabor mais caro.',
  AVERAGE: 'Cobra a média das opções. Ex.: pizza meio a meio pela média dos sabores.',
};

/** "Obrigatório: escolha 1", "Opcional: até 3", "Escolha de 1 a 2". */
export function describeChoices(min: number, max: number): string {
  if (min === 0) {
    return `Opcional: até ${max}`;
  }
  if (min === max) {
    return `Obrigatório: escolha ${min}`;
  }
  return `Obrigatório: escolha de ${min} a ${max}`;
}
