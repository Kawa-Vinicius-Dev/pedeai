import { Alert, Button, Divider, Group, Modal, NumberInput, Stack, Text, TextInput } from '@mantine/core';
import { CircleAlert } from 'lucide-react';
import { useState } from 'react';
import { errorMessage } from '../../shared/api/errors';
import type { OptionGroup, Product } from '../../shared/api/types';
import { formatCents } from '../../shared/lib/numbers';
import { ItemChoices } from '../catalog/ItemChoices';
import { type ChosenOptions, productGroups, toChoices, usePriceQuote } from '../catalog/itemPricing';
import { type CartLine, nextLineKey } from './cart';

/** Monta o item com adicionais: valida mínimo e máximo de cada grupo e mostra o preço calculado pela API. */
export function ItemBuilderModal({
  product,
  optionGroups,
  onAdd,
  onClose,
}: {
  product: Product | null;
  optionGroups: OptionGroup[];
  onAdd: (line: CartLine) => void;
  onClose: () => void;
}) {
  return (
    <Modal opened={product !== null} onClose={onClose} title={product?.name ?? ''} size="lg">
      {product && (
        <BuilderBody key={product.id} product={product} optionGroups={optionGroups} onAdd={onAdd} />
      )}
    </Modal>
  );
}

function BuilderBody({
  product,
  optionGroups,
  onAdd,
}: {
  product: Product;
  optionGroups: OptionGroup[];
  onAdd: (line: CartLine) => void;
}) {
  const groups = productGroups(product, optionGroups);
  const [chosen, setChosen] = useState<ChosenOptions>({});
  const [quantity, setQuantity] = useState(1);
  const [notes, setNotes] = useState('');
  const options = toChoices(chosen);
  const quote = usePriceQuote(product.id, quantity, options);
  const ready = quote.data !== undefined && !quote.isError && !quote.isPlaceholderData;

  function add() {
    if (!quote.data) {
      return;
    }
    onAdd({
      key: nextLineKey(),
      productId: product.id,
      name: product.name,
      quantity,
      unitPriceCents: quote.data.unitPriceCents,
      options,
      optionsLabel: quote.data.options
        .map((option) => (option.quantity > 1 ? `${option.quantity}x ${option.name}` : option.name))
        .join(', '),
      notes: notes.trim(),
    });
  }

  return (
    <Stack>
      {product.description && (
        <Text size="sm" c="dimmed">
          {product.description}
        </Text>
      )}
      <ItemChoices groups={groups} chosen={chosen} onChange={setChosen} />
      <Divider />
      <Group align="flex-end" grow>
        <NumberInput
          label="Quantidade"
          min={1}
          max={999}
          allowDecimal={false}
          allowNegative={false}
          value={quantity}
          onChange={(value) => setQuantity(typeof value === 'number' && value >= 1 ? value : 1)}
        />
        <TextInput
          label="Observação do item"
          placeholder="Ex.: sem cebola"
          maxLength={300}
          value={notes}
          onChange={(event) => setNotes(event.currentTarget.value)}
        />
      </Group>
      {quote.isError && (
        <Alert color="yellow" icon={<CircleAlert size={18} />}>
          {errorMessage(quote.error)}
        </Alert>
      )}
      <Group justify="flex-end">
        <Button onClick={add} disabled={!ready}>
          {ready && quote.data ? `Adicionar ${formatCents(quote.data.totalCents)}` : 'Adicionar'}
        </Button>
      </Group>
    </Stack>
  );
}
