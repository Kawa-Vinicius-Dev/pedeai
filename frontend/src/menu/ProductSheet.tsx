import { ActionIcon, Alert, Button, Group, Image, Modal, Stack, Text, TextInput } from '@mantine/core';
import { CircleAlert, Minus, Plus } from 'lucide-react';
import { useState } from 'react';
import { ItemChoices } from '../features/catalog/ItemChoices';
import { type ChosenOptions, toChoices } from '../features/catalog/itemPricing';
import type { CartLine } from '../features/orders/cart';
import { errorMessage } from '../shared/api/errors';
import type { MenuProduct, OptionGroup } from '../shared/api/types';
import { formatCents } from '../shared/lib/numbers';
import { usePublicQuote } from './api';

/** O cliente monta o item: sabores, adicionais, quantidade e observação. O preço vem da API. */
export function ProductSheet({
  slug,
  product,
  optionGroups,
  onAdd,
  onClose,
}: {
  slug: string;
  product: MenuProduct | null;
  optionGroups: OptionGroup[];
  onAdd: (line: CartLine) => void;
  onClose: () => void;
}) {
  return (
    <Modal opened={product !== null} onClose={onClose} title={product?.name ?? ''} size="lg">
      {product && (
        <SheetBody key={product.id} slug={slug} product={product} optionGroups={optionGroups} onAdd={onAdd} />
      )}
    </Modal>
  );
}

function SheetBody({
  slug,
  product,
  optionGroups,
  onAdd,
}: {
  slug: string;
  product: MenuProduct;
  optionGroups: OptionGroup[];
  onAdd: (line: CartLine) => void;
}) {
  const groups = product.optionGroupIds
    .map((id) => optionGroups.find((group) => group.id === id))
    .filter((group): group is OptionGroup => group !== undefined);
  const [chosen, setChosen] = useState<ChosenOptions>({});
  const [quantity, setQuantity] = useState(1);
  const [notes, setNotes] = useState('');
  const options = toChoices(chosen);
  // Só pergunta o preço quando os grupos obrigatórios estão completos: antes disso a API recusaria.
  const complete = groups.every(
    (group) => group.options.reduce((sum, option) => sum + (chosen[option.id] ?? 0), 0) >= group.minChoices,
  );
  const quote = usePublicQuote(slug, product.id, quantity, options, complete);
  const ready = complete && quote.data !== undefined && !quote.isError && !quote.isPlaceholderData;

  function add() {
    if (!quote.data) {
      return;
    }
    onAdd({
      // O carrinho fica salvo no navegador: a chave não pode recomeçar a cada vez que a página abre.
      key: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
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
      {product.imageUrl && <Image src={product.imageUrl} alt={product.name} h={200} radius="md" fit="cover" />}
      {product.description && (
        <Text size="sm" c="dimmed">
          {product.description}
        </Text>
      )}
      <ItemChoices groups={groups} chosen={chosen} onChange={setChosen} showPricingRule={false} />
      <TextInput
        label="Observação"
        placeholder="Ex.: sem cebola"
        maxLength={300}
        value={notes}
        onChange={(event) => setNotes(event.currentTarget.value)}
      />
      {quote.isError && complete && (
        <Alert color="yellow" icon={<CircleAlert size={18} />}>
          {errorMessage(quote.error)}
        </Alert>
      )}
      <Group justify="space-between" wrap="nowrap">
        <Group gap="xs" wrap="nowrap">
          <ActionIcon
            variant="default"
            size="lg"
            aria-label="Diminuir quantidade"
            disabled={quantity <= 1}
            onClick={() => setQuantity(quantity - 1)}
          >
            <Minus size={16} />
          </ActionIcon>
          <Text fw={600} w={24} ta="center" aria-label="Quantidade">
            {quantity}
          </Text>
          <ActionIcon variant="default" size="lg" aria-label="Aumentar quantidade" onClick={() => setQuantity(quantity + 1)}>
            <Plus size={16} />
          </ActionIcon>
        </Group>
        <Button onClick={add} disabled={!ready}>
          {ready && quote.data ? `Adicionar ${formatCents(quote.data.totalCents)}` : 'Adicionar'}
        </Button>
      </Group>
    </Stack>
  );
}
