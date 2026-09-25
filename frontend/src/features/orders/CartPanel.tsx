import { ActionIcon, Group, Stack, Text } from '@mantine/core';
import { Minus, Plus, Trash2 } from 'lucide-react';
import { formatCents } from '../../shared/lib/numbers';
import { type CartLine, lineTotal } from './cart';

export function CartPanel({
  lines,
  onQuantity,
}: {
  lines: CartLine[];
  onQuantity: (key: string, quantity: number) => void;
}) {
  if (lines.length === 0) {
    return <Text c="dimmed">Toque nos produtos do cardápio para adicionar.</Text>;
  }
  return (
    <Stack gap="sm" aria-label="Itens do pedido">
      {lines.map((line) => (
        <Group key={line.key} justify="space-between" align="flex-start" wrap="nowrap">
          <Stack gap={0} style={{ minWidth: 0 }}>
            <Text fw={500}>
              {line.quantity}x {line.name}
            </Text>
            {line.optionsLabel && (
              <Text size="xs" c="dimmed">
                {line.optionsLabel}
              </Text>
            )}
            {line.notes && (
              <Text size="xs" c="orange.8">
                Obs.: {line.notes}
              </Text>
            )}
          </Stack>
          <Group gap={4} wrap="nowrap">
            <ActionIcon
              variant="default"
              size="sm"
              aria-label={`Tirar um ${line.name}`}
              onClick={() => onQuantity(line.key, line.quantity - 1)}
            >
              {line.quantity === 1 ? <Trash2 size={14} /> : <Minus size={14} />}
            </ActionIcon>
            <ActionIcon
              variant="default"
              size="sm"
              aria-label={`Mais um ${line.name}`}
              onClick={() => onQuantity(line.key, line.quantity + 1)}
            >
              <Plus size={14} />
            </ActionIcon>
            <Text size="sm" w={84} ta="right">
              {formatCents(lineTotal(line))}
            </Text>
          </Group>
        </Group>
      ))}
    </Stack>
  );
}
