import { ActionIcon, Badge, Checkbox, Group, Stack, Text, Title } from '@mantine/core';
import { Minus, Plus } from 'lucide-react';
import type { OptionGroup, OptionItem } from '../../shared/api/types';
import { formatCents } from '../../shared/lib/numbers';
import type { ChosenOptions } from './itemPricing';
import { describeChoices, PRICING_RULE_LABELS } from './labels';

/** Escolhas de cada grupo de adicionais: marcar (sabores, borda) ou contar (2x bacon). */
export function ItemChoices({
  groups,
  chosen,
  onChange,
}: {
  groups: OptionGroup[];
  chosen: ChosenOptions;
  onChange: (chosen: ChosenOptions) => void;
}) {
  return (
    <>
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
                onChange={(quantity) => onChange({ ...chosen, [option.id]: quantity })}
              />
            ))}
        </Stack>
      ))}
    </>
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
