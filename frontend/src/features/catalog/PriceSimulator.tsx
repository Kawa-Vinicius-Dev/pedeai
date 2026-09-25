import { Alert, Divider, Loader, Modal, NumberInput, Stack, Table, Text } from '@mantine/core';
import { CircleAlert } from 'lucide-react';
import { useState } from 'react';
import { errorMessage } from '../../shared/api/errors';
import type { OptionGroup, Product } from '../../shared/api/types';
import { formatCents } from '../../shared/lib/numbers';
import { ItemChoices } from './ItemChoices';
import { type ChosenOptions, productGroups, toChoices, usePriceQuote } from './itemPricing';

/**
 * Monta o item como no pedido e mostra o preço calculado pela API. Serve para conferir o cadastro:
 * pizza meio a meio, mínimo e máximo de cada grupo, opção pausada.
 */
export function PriceSimulator({
  product,
  optionGroups,
  onClose,
}: {
  product: Product | null;
  optionGroups: OptionGroup[];
  onClose: () => void;
}) {
  return (
    <Modal opened={product !== null} onClose={onClose} title={product ? `Simular preço: ${product.name}` : ''} size="lg">
      {product && <SimulatorBody key={product.id} product={product} optionGroups={optionGroups} />}
    </Modal>
  );
}

function SimulatorBody({ product, optionGroups }: { product: Product; optionGroups: OptionGroup[] }) {
  const groups = productGroups(product, optionGroups);
  const [quantity, setQuantity] = useState(1);
  const [chosen, setChosen] = useState<ChosenOptions>({});
  const quote = usePriceQuote(product.id, quantity, toChoices(chosen));

  return (
    <Stack>
      {groups.length === 0 && <Text c="dimmed">Este produto não tem adicionais.</Text>}
      <ItemChoices groups={groups} chosen={chosen} onChange={setChosen} />

      <Divider />
      <NumberInput
        label="Quantidade"
        min={1}
        max={999}
        allowDecimal={false}
        allowNegative={false}
        maw={160}
        value={quantity}
        onChange={(value) => setQuantity(typeof value === 'number' && value >= 1 ? value : 1)}
      />

      {quote.isError && (
        <Alert color="yellow" icon={<CircleAlert size={18} />}>
          {errorMessage(quote.error)}
        </Alert>
      )}
      {quote.isPending && <Loader size="sm" aria-label="Calculando preço" />}
      {quote.data && !quote.isError && (
        <Table aria-label="Preço calculado" withRowBorders={false} verticalSpacing={4}>
          <Table.Tbody>
            <PriceLine label="Preço base" cents={quote.data.basePriceCents} />
            <PriceLine label="Adicionais" cents={quote.data.optionsPriceCents} />
            <PriceLine label="Preço unitário" cents={quote.data.unitPriceCents} />
            <PriceLine label={`Total (${quote.data.quantity} un.)`} cents={quote.data.totalCents} strong />
          </Table.Tbody>
        </Table>
      )}
    </Stack>
  );
}

function PriceLine({ label, cents, strong = false }: { label: string; cents: number; strong?: boolean }) {
  return (
    <Table.Tr>
      <Table.Td>
        <Text fw={strong ? 700 : undefined}>{label}</Text>
      </Table.Td>
      <Table.Td ta="right">
        <Text fw={strong ? 700 : undefined}>{formatCents(cents)}</Text>
      </Table.Td>
    </Table.Tr>
  );
}
