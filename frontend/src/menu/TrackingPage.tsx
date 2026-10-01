import { Alert, Anchor, Badge, Card, Container, Divider, Group, Loader, Stack, Stepper, Text, Title } from '@mantine/core';
import { CircleAlert } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { errorMessage } from '../shared/api/errors';
import type { OrderTracking } from '../shared/api/types';
import { formatCents } from '../shared/lib/numbers';
import { useTracking } from './api';

type Status = OrderTracking['status'];

/** Os passos que o cliente vê, na ordem. Retirada não tem "saiu para entrega". */
function steps(type: OrderTracking['type']): { status: Status; label: string }[] {
  return [
    { status: 'RECEIVED', label: 'Enviado' },
    { status: 'CONFIRMED', label: 'Aceito pela loja' },
    { status: 'IN_PREPARATION', label: 'Em preparo' },
    { status: 'READY', label: type === 'DELIVERY' ? 'Pronto' : 'Pronto para retirar' },
    ...(type === 'DELIVERY' ? [{ status: 'DISPATCHED' as Status, label: 'Saiu para entrega' }] : []),
    { status: 'COMPLETED', label: type === 'DELIVERY' ? 'Entregue' : 'Retirado' },
  ];
}

const HEADLINES: Record<Status, string> = {
  RECEIVED: 'Esperando a loja aceitar',
  CONFIRMED: 'A loja aceitou seu pedido',
  IN_PREPARATION: 'Seu pedido está sendo preparado',
  READY: 'Seu pedido está pronto',
  DISPATCHED: 'Seu pedido saiu para entrega',
  COMPLETED: 'Pedido concluído',
  CANCELLED: 'Pedido cancelado',
};

export function TrackingPage() {
  const { slug = '', code = '' } = useParams();
  const tracking = useTracking(code);

  return (
    <Container size="sm" py="md">
      {tracking.isPending && <Loader aria-label="Carregando o pedido" />}
      {tracking.isError && (
        <Alert color="red" icon={<CircleAlert size={18} />}>
          {errorMessage(tracking.error)}
        </Alert>
      )}
      {tracking.data && <OrderView order={tracking.data} />}
      <Anchor component={Link} to={`/loja/${tracking.data?.storeSlug ?? slug}`} mt="lg" display="block">
        Voltar ao cardápio
      </Anchor>
    </Container>
  );
}

function OrderView({ order }: { order: OrderTracking }) {
  const cancelled = order.status === 'CANCELLED';
  const list = steps(order.type);
  const active = list.findIndex((step) => step.status === order.status);

  return (
    <Stack gap="lg">
      <Stack gap={2}>
        <Text c="dimmed">{order.storeName}</Text>
        <Group justify="space-between" align="center">
          <Title order={1} fz={26}>
            Pedido {order.number}
          </Title>
          <Badge size="lg" color={cancelled ? 'red' : 'orange'} variant="light">
            {HEADLINES[order.status]}
          </Badge>
        </Group>
      </Stack>
      {cancelled ? (
        <Alert color="red" icon={<CircleAlert size={18} />} title="A loja cancelou o pedido">
          {order.cancelReason ?? 'Fale com a loja para saber o motivo.'}
        </Alert>
      ) : (
        <Stepper active={order.status === 'COMPLETED' ? list.length : active} orientation="vertical" size="sm">
          {list.map((step) => (
            <Stepper.Step key={step.status} label={step.label} />
          ))}
        </Stepper>
      )}
      <Card withBorder radius="md">
        <Stack gap="xs">
          {order.items.map((item, index) => (
            <Stack key={index} gap={0}>
              <Text>
                {item.quantity}x {item.name}
              </Text>
              {item.details && (
                <Text size="sm" c="dimmed">
                  {item.details}
                </Text>
              )}
            </Stack>
          ))}
          <Divider />
          {order.deliveryFeeCents > 0 && (
            <Group justify="space-between">
              <Text size="sm">Entrega</Text>
              <Text size="sm">{formatCents(order.deliveryFeeCents)}</Text>
            </Group>
          )}
          <Group justify="space-between">
            <Text fw={700}>Total a pagar</Text>
            <Text fw={700}>{formatCents(order.totalCents)}</Text>
          </Group>
        </Stack>
      </Card>
      {order.storePhone && (
        <Text size="sm">
          Dúvidas? Ligue para a loja: <Anchor href={`tel:${order.storePhone}`}>{order.storePhone}</Anchor>
        </Text>
      )}
    </Stack>
  );
}
