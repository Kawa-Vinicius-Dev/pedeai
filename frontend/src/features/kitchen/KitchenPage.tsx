import { Alert, Badge, Button, Group, Loader, Paper, SegmentedControl, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { Bell, BellRing, CircleAlert, Maximize, Undo2 } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { Order, OrderStatus } from '../../shared/api/types';
import { useSectors } from '../catalog/api';
import { AlertSound } from '../orders/alertSound';
import { orderKeys, useChangeStatus } from '../orders/api';
import { minutesSince, TYPE_LABELS } from '../orders/labels';
import { onOrderEvent } from '../orders/realtime';
import { useNow } from '../orders/useNow';

/** Tempo para desfazer um toque errado antes de o status ir para a API (que só anda para frente). */
const UNDO_MS = 5_000;
const SECTOR_KEY = 'pedeai.kitchen.sector';
const ALL = 'all';

interface Pending {
  status: OrderStatus;
  timer: ReturnType<typeof setTimeout>;
  fire: () => void;
}

/** Cozinha (KDS): cartões grandes, filtro por setor, cronômetro com cores, iniciar, pronto e desfazer. */
export function KitchenPage() {
  const sectors = useSectors();
  const [stored, setSector] = useState(() => readSector());
  const activeSectors = (sectors.data ?? []).filter((item) => item.active);
  // Setor guardado que foi excluído ou desativado: volta para todos, em vez de uma tela sempre vazia.
  const sector = sectors.data && !activeSectors.some((item) => item.id === stored) ? ALL : stored;
  const sectorId = sector === ALL ? undefined : sector;
  const orders = useQuery({
    // Sob ['orders']: o aviso do tempo real recarrega a cozinha junto com o quadro.
    queryKey: [...orderKeys.all, 'kitchen', sector],
    queryFn: () => unwrap(api.GET('/api/kitchen/orders', { params: { query: { sectorId } } })),
    refetchInterval: 60_000,
  });
  const changeStatus = useChangeStatus();
  const [pending, setPending] = useState<Record<string, Pending>>({});
  const [alertsOn, setAlertsOn] = useState(false);
  const sound = useRef(new AlertSound());
  const now = useNow(15_000);
  useWakeLock();

  // Saiu da tela com um toque ainda no prazo de desfazer: o toque vale.
  const pendingRef = useRef(pending);
  useEffect(() => {
    pendingRef.current = pending;
  }, [pending]);
  useEffect(
    () => () =>
      Object.values(pendingRef.current).forEach(({ timer, fire }) => {
        clearTimeout(timer);
        fire();
      }),
    [],
  );

  useEffect(() => onOrderEvent((event) => event.type === 'order.created' && sound.current.play()), []);

  function chooseSector(value: string) {
    setSector(value);
    try {
      localStorage.setItem(SECTOR_KEY, value);
    } catch {
      // Sem armazenamento: o filtro vale só enquanto a tela está aberta.
    }
  }

  function advance(order: Order, status: OrderStatus) {
    const fire = () => changeStatus.mutate({ id: order.id, status, version: order.version });
    const timer = setTimeout(() => {
      setPending((current) => without(current, order.id));
      fire();
    }, UNDO_MS);
    setPending((current) => ({ ...current, [order.id]: { status, timer, fire } }));
  }

  function undo(orderId: string) {
    clearTimeout(pending[orderId]?.timer);
    setPending((current) => without(current, orderId));
  }

  return (
    <Stack gap="md">
      <Group justify="space-between" align="flex-end">
        <Stack gap={4}>
          <Title order={2}>Cozinha</Title>
          <Text c="dimmed">Do mais antigo para o mais novo. Atualiza sozinho.</Text>
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
          <Button
            variant="default"
            leftSection={<Maximize size={16} />}
            onClick={() => void document.documentElement.requestFullscreen?.().catch(() => undefined)}
          >
            Tela cheia
          </Button>
        </Group>
      </Group>

      {activeSectors.length > 1 && (
        <SegmentedControl
          aria-label="Setor"
          value={sector}
          onChange={chooseSector}
          data={[{ value: ALL, label: 'Todos' }, ...activeSectors.map((item) => ({ value: item.id, label: item.name }))]}
        />
      )}

      {orders.isPending && <Loader aria-label="Carregando pedidos" />}
      {orders.isError && (
        <Alert color="red" icon={<CircleAlert size={18} />}>
          {errorMessage(orders.error)}
        </Alert>
      )}
      {orders.data?.length === 0 && <Text c="dimmed">Nada para preparar agora.</Text>}
      {orders.data && (
        <SimpleGrid cols={{ base: 1, sm: 2, lg: 3, xl: 4 }} spacing="md">
          {orders.data.map((order) => (
            <KitchenCard
              key={order.id}
              order={order}
              now={now}
              pending={pending[order.id]?.status}
              onAdvance={(status) => advance(order, status)}
              onUndo={() => undo(order.id)}
            />
          ))}
        </SimpleGrid>
      )}
    </Stack>
  );
}

function KitchenCard({
  order,
  now,
  pending,
  onAdvance,
  onUndo,
}: {
  order: Order;
  now: number;
  pending: OrderStatus | undefined;
  onAdvance: (status: OrderStatus) => void;
  onUndo: () => void;
}) {
  const minutes = minutesSince(order.confirmedAt ?? order.createdAt, now);
  const color = timerColor(minutes);
  const started = order.status === 'IN_PREPARATION';

  return (
    <Paper
      withBorder
      radius="md"
      p="md"
      aria-label={`Pedido ${order.number}`}
      style={{ borderColor: `var(--mantine-color-${color}-6)`, borderWidth: 3, opacity: pending ? 0.6 : 1 }}
    >
      <Stack gap="sm">
        <Group justify="space-between" wrap="nowrap">
          <Group gap={8} wrap="nowrap">
            <Text fw={900} fz={28}>
              #{order.number}
            </Text>
            <Badge variant="light" color={order.type === 'DELIVERY' ? 'grape' : 'cyan'}>
              {TYPE_LABELS[order.type]}
            </Badge>
          </Group>
          <Text fw={800} fz="xl" c={`${color}.7`}>
            {minutes} min
          </Text>
        </Group>
        {order.customerName && (
          <Text fw={500} lineClamp={1}>
            {order.customerName}
          </Text>
        )}
        <Stack gap={6}>
          {order.items.map((item) => (
            <div key={item.id}>
              <Text fw={700} fz="lg">
                {item.quantity}x {item.name}
              </Text>
              {item.options.map((option, index) => (
                <Text key={index} pl="md">
                  {option.quantity > 1 ? `${option.quantity}x ` : ''}
                  {option.name}
                </Text>
              ))}
              {item.notes && (
                <Text pl="md" fw={700} c="red.7">
                  {item.notes}
                </Text>
              )}
            </div>
          ))}
        </Stack>
        {order.notes && (
          <Text size="sm" fw={600} c="red.7">
            Obs.: {order.notes}
          </Text>
        )}
        {pending ? (
          <Button size="lg" variant="default" leftSection={<Undo2 size={20} />} onClick={onUndo}>
            Desfazer
          </Button>
        ) : (
          <Button size="lg" color={started ? 'green' : 'orange'} onClick={() => onAdvance(started ? 'READY' : 'IN_PREPARATION')}>
            {started ? 'Pronto' : 'Iniciar'}
          </Button>
        )}
      </Stack>
    </Paper>
  );
}

/** Verde até 15 min, laranja até 25, vermelho depois. Contado desde a confirmação. */
function timerColor(minutes: number): 'green' | 'orange' | 'red' {
  return minutes >= 25 ? 'red' : minutes >= 15 ? 'orange' : 'green';
}

function without(pending: Record<string, Pending>, orderId: string): Record<string, Pending> {
  const rest = { ...pending };
  delete rest[orderId];
  return rest;
}

function readSector(): string {
  try {
    return localStorage.getItem(SECTOR_KEY) ?? ALL;
  } catch {
    return ALL;
  }
}

/** Não deixa a tela apagar enquanto a cozinha está aberta. O navegador solta o bloqueio ao trocar de aba. */
function useWakeLock() {
  useEffect(() => {
    let lock: WakeLockSentinel | null = null;
    const acquire = () => {
      if (document.visibilityState === 'visible' && 'wakeLock' in navigator) {
        navigator.wakeLock.request('screen').then((sentinel) => (lock = sentinel), () => undefined);
      }
    };
    acquire();
    document.addEventListener('visibilitychange', acquire);
    return () => {
      document.removeEventListener('visibilitychange', acquire);
      void lock?.release();
    };
  }, []);
}
