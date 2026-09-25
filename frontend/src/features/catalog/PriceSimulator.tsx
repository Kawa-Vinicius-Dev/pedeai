import {
  ActionIcon,
  Alert,
  Badge,
  Checkbox,
  Divider,
  Group,
  Loader,
  Modal,
  NumberInput,
  Stack,
  Table,
  Text,
  Title,
} from '@mantine/core';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { CircleAlert, Minus, Plus } from 'lucide-react';
import { useState } from 'react';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { OptionChoice, OptionGroup, OptionItem, Product } from '../../shared/api/types';
import { formatCents } from '../../shared/lib/numbers';
import { catalogKeys } from './api';
import { describeChoices, PRICING_RULE_LABELS } from './labels';

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
  const groups = product.optionGroupIds
    .map((id) => optionGroups.find((group) => group.id === id))
    .filter((group): group is OptionGroup => group !== undefined && group.active);
  const [quantity, setQuantity] = useState(1);
  const [chosen, setChosen] = useState<Record<string, number>>({});
  const options: OptionChoice[] = Object.entries(chosen)
    .filter(([, optionQuantity]) => optionQuantity > 0)
    .map(([optionId, optionQuantity]) => ({ optionId, quantity: optionQuantity }));

  const quote = useQuery({
    queryKey: catalogKeys.priceQuote(product.id, quantity, options),
    queryFn: () =>
      unwrap(
        api.POST('/api/products/{id}/price-quotes', {
          params: { path: { id: product.id } },
          body: { quantity, options },
        }),
      ),
    placeholderData: keepPreviousData,
  });

  function setOption(optionId: string, optionQuantity: number) {
    setChosen((current) => ({ ...current, [optionId]: optionQuantity }));
  }

  return (
    <Stack>
      {groups.length === 0 && <Text c="dimmed">Este produto não tem adicionais.</Text>}
      {groups.map((group) => (
        <Stack key={group.id} gap="xs">
          <Group justify="space-between" wrap="nowrap">
            <Title order={5}>{group.name}</Title>
            <Group gap={6} wrap="nowrap">
              <Badge variant="light" color="gray">
                {PRICING_RULE_LABELS[group.pricingRule]}
              </Badge>
              <Text size="sm" c="dimmed">
                {describeChoices(group.minChoices, group.maxChoices)}
              </Text>
            </Group>
          </Group>
          {group.options
            .filter((option) => option.active)
            .map((option) => (
              <OptionRow
                key={option.id}
                group={group}
                option={option}
                quantity={chosen[option.id] ?? 0}
                onChange={(optionQuantity) => setOption(option.id, optionQuantity)}
              />
            ))}
        </Stack>
      ))}

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

function OptionRow({
  group,
  option,
  quantity,
  onChange,
}: {
  group: OptionGroup;
  option: OptionItem;
  quantity: number;
  onChange: (quantity: number) => void;
}) {
  // Em grupos de soma a mesma opção pode vir mais de uma vez (2x bacon). Nos outros, só marca ou desmarca.
  const countable = group.pricingRule === 'SUM' && group.maxChoices > 1;
  return (
    <Group justify="space-between" wrap="nowrap">
      {countable ? (
        <Text size="sm">{option.name}</Text>
      ) : (
        <Checkbox
          label={option.name}
          checked={quantity > 0}
          disabled={!option.available && quantity === 0}
          onChange={(event) => onChange(event.currentTarget.checked ? 1 : 0)}
        />
      )}
      <Group gap="sm" wrap="nowrap">
        {!option.available && (
          <Badge size="sm" color="yellow" variant="light">
            Pausado
          </Badge>
        )}
        <Text size="sm" c="dimmed">
          {option.priceCents > 0 ? formatCents(option.priceCents) : 'grátis'}
        </Text>
        {countable && (
          <>
            <ActionIcon
              variant="default"
              size="sm"
              aria-label={`Tirar ${option.name}`}
              disabled={quantity === 0}
              onClick={() => onChange(quantity - 1)}
            >
              <Minus size={14} />
            </ActionIcon>
            <Text size="sm" w={20} ta="center" aria-label={`Quantidade de ${option.name}`}>
              {quantity}
            </Text>
            <ActionIcon
              variant="default"
              size="sm"
              aria-label={`Pôr ${option.name}`}
              disabled={!option.available}
              onClick={() => onChange(quantity + 1)}
            >
              <Plus size={14} />
            </ActionIcon>
          </>
        )}
      </Group>
    </Group>
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
