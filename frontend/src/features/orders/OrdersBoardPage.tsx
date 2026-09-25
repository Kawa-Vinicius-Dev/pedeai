import { Alert, Badge, Button, Group, Loader, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { Bell, BellRing, CircleAlert, History, Plus } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { errorMessage } from '../../shared/api/errors';
import type { OrderStatus, OrderSummary } from '../../shared/api/types';
import { ORDER_TAKERS } from '../../shared/lib/roles';
import { useSession } from '../auth/auth-context';
import { AlertSound } from './alertSound';
import { useActiveOrders, useChangeStatus } from './api';
import { OrderCard } from './OrderCard';
import { OrderDetailDrawer } from './OrderDetailDrawer';
import { onOrderEvent } from './realtime';
import { useNow } from './useNow';

const COLUMNS: { title: string; statuses: OrderStatus[]; color: string }[] = [
  { title: 'Recebidos', statuses: ['RECEIVED'], color: 'red' },
  { title: 'Em produção', statuses: ['CONFIRMED', 'IN_PREPARATION'], color: 'orange' },
  { title: 'Prontos', statuses: ['READY'], color: 'green' },
  { title: 'Em entrega', statuses: ['DISPATCHED'], color: 'grape' },
];

/** Quadro de pedidos em andamento. Atualiza sozinho pelo tempo real, em todas as telas abertas. */
export function OrdersBoardPage() {
  const { user } = useSession();
  const orders = useActiveOrders();
  const changeStatus = useChangeStatus();
  const [openId, setOpenId] = useState<string | null>(null);
  const [alertsOn, setAlertsOn] = useState(false);
  const sound = useRef(new AlertSound());
  const now = useNow();

  useEffect(
    () =>
      onOrderEvent((event) => {
        if (event.type === 'order.created') {
          sound.current.play();
        }
      }),
    [],
  );

  function advance(order: OrderSummary, status: OrderStatus) {
    changeStatus.mutate({ id: order.id, status, version: order.version });
  }

  return (
    <Stack gap="md">
      <Group justify="space-between" align="flex-end">
        <Stack gap={4}>
          <Title order={2}>Pedidos</Title>
          <Text c="dimmed">Em andamento, do mais antigo para o mais novo. Atualiza sozinho.</Text>
        </Stack>
        <Group gap="xs">
          <Button
            variant={alertsOn ? 'light' : 'default'}
            color={alertsOn ? 'green' : 'gray'}
            leftSection={alertsOn ? <BellRing size={16} /> : <Bell size={16} />}
            onClick={() => setAlertsOn(sound.current.enable())}
          >
            {alertsOn ? 'Alertas ligados' : 'Ativar alertas'}
          </Button>
          <Button component={Link} to="/pedidos/historico" variant="default" leftSection={<History size={16} />}>
            Histórico
          </Button>
          {ORDER_TAKERS.includes(user.role) && (
            <Button component={Link} to="/pedidos/novo" leftSection={<Plus size={18} />}>
              Novo pedido
            </Button>
          )}
        </Group>
      </Group>

      {orders.isPending && <Loader aria-label="Carregando pedidos" />}
      {orders.isError && (
        <Alert color="red" icon={<CircleAlert size={18} />}>
          {errorMessage(orders.error)}
        </Alert>
      )}
      {orders.data && (
        <SimpleGrid cols={{ base: 1, sm: 2, lg: 4 }} spacing="md">
          {COLUMNS.map((column) => {
            const inColumn = orders.data.filter((order) => column.statuses.includes(order.status));
            return (
              <Stack key={column.title} gap="sm" aria-label={column.title}>
                <Group gap="xs">
                  <Title order={4}>{column.title}</Title>
                  <Badge color={column.color} variant="light">
                    {inColumn.length}
                  </Badge>
                </Group>
                {inColumn.length === 0 && (
                  <Text size="sm" c="dimmed">
                    Nenhum pedido.
                  </Text>
                )}
                {inColumn.map((order) => (
                  <OrderCard
                    key={order.id}
                    order={order}
                    role={user.role}
                    now={now}
                    busy={changeStatus.isPending && changeStatus.variables?.id === order.id}
                    onOpen={() => setOpenId(order.id)}
                    onAdvance={(status) => advance(order, status)}
                  />
                ))}
              </Stack>
            );
          })}
        </SimpleGrid>
      )}

      <OrderDetailDrawer orderId={openId} onClose={() => setOpenId(null)} />
    </Stack>
  );
}
