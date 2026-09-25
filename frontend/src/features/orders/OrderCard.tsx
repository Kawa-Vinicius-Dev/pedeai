import { Badge, Button, Group, Paper, Stack, Text, UnstyledButton } from '@mantine/core';
import type { OrderStatus, OrderSummary, Role } from '../../shared/api/types';
import { formatCents } from '../../shared/lib/numbers';
import { minutesSince, nextActions, STATUS_COLORS, STATUS_LABELS, TYPE_LABELS } from './labels';

/** Recebido há mais de 3 minutos: o iFood cancela sozinho aos 8 (docs/01-fluxos.md). */
const RECEIVED_ALERT_MINUTES = 3;

export function OrderCard({
  order,
  role,
  now,
  busy,
  onOpen,
  onAdvance,
}: {
  order: OrderSummary;
  role: Role;
  now: number;
  busy: boolean;
  onOpen: () => void;
  onAdvance: (status: OrderStatus) => void;
}) {
  const waiting = minutesSince(order.createdAt, now);
  const late = order.status === 'RECEIVED' && waiting >= RECEIVED_ALERT_MINUTES;
  const actions = nextActions(order, role);
  const elapsedColor = late || waiting >= 40 ? 'red' : waiting >= 20 ? 'orange' : 'dimmed';

  return (
    <Paper
      withBorder
      radius="md"
      p="sm"
      style={late ? { borderColor: 'var(--mantine-color-red-6)', borderWidth: 2 } : undefined}
      aria-label={`Pedido ${order.number}`}
    >
      <Stack gap={6}>
        <UnstyledButton onClick={onOpen} aria-label={`Abrir pedido ${order.number}`}>
          <Stack gap={4}>
            <Group justify="space-between" wrap="nowrap">
              <Group gap={6} wrap="nowrap">
                <Text fw={800} fz="lg">
                  #{order.number}
                </Text>
                <Badge size="sm" variant="light" color={order.type === 'DELIVERY' ? 'grape' : 'cyan'}>
                  {TYPE_LABELS[order.type]}
                </Badge>
              </Group>
              <Text size="sm" fw={600} c={elapsedColor}>
                {waiting} min
              </Text>
            </Group>
            {(order.customerName || order.deliveryNeighborhood) && (
              <Text size="sm" fw={500} lineClamp={1}>
                {[order.customerName, order.deliveryNeighborhood].filter(Boolean).join(' · ')}
              </Text>
            )}
            <Text size="xs" c="dimmed" lineClamp={2}>
              {order.itemsSummary}
            </Text>
            <Group justify="space-between">
              <Badge size="sm" variant="dot" color={STATUS_COLORS[order.status]}>
                {STATUS_LABELS[order.status]}
              </Badge>
              <Text size="sm" fw={600}>
                {formatCents(order.totalCents)}
              </Text>
            </Group>
          </Stack>
        </UnstyledButton>
        {actions.length > 0 && (
          // O passo principal ocupa o que sobra; o atalho ("Pronto" sem passar pelo preparo) fica do tamanho do texto.
          <Group gap={6} wrap="nowrap">
            {actions.map((action, index) => (
              <Button
                key={action.status}
                size="xs"
                variant={index === 0 ? 'filled' : 'light'}
                loading={busy && index === 0}
                disabled={busy}
                onClick={() => onAdvance(action.status)}
                style={index === 0 ? { flex: 1 } : undefined}
              >
                {action.label}
              </Button>
            ))}
          </Group>
        )}
      </Stack>
    </Paper>
  );
}
