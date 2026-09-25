import { NumberInput, type NumberInputProps } from '@mantine/core';

/**
 * Campo de valor em reais: "R$ 1.234,56", sempre com dois decimais (digitar "8" mostra "R$ 8,00").
 * Entrega número ou, durante a digitação, texto (ver decimalField).
 */
export function MoneyInput(props: NumberInputProps) {
  return (
    <NumberInput
      prefix="R$ "
      decimalSeparator=","
      thousandSeparator="."
      decimalScale={2}
      fixedDecimalScale
      min={0}
      hideControls
      allowNegative={false}
      {...props}
    />
  );
}
