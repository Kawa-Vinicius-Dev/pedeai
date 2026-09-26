import {
  Alert,
  Badge,
  Button,
  Divider,
  Drawer,
  Group,
  Loader,
  Modal,
  Select,
  Stack,
  Switch,
  Text,
  Textarea,
  Timeline,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CircleAlert } from 'lucide-react';
import { useState } from 'react';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { Order, OrderStatus, Payment } from '../../shared/api/types';
import { formatCents, parseDecimal } from '../../shared/lib/numbers';
import { ORDER_TAKERS } from '../../shared/lib/roles';
import { MoneyInput } from '../../shared/ui/MoneyInput';
import { useSession } from '../auth/auth-context';
import { PrintMenu } from '../printing/PrintMenu';
import { orderKeys, useChangeStatus, useOrder, useOrderHistory, useOrderPayments, usePaymentMethods } from './api';
import { canCancel, formatPhone, nextActions, STATUS_COLORS, STATUS_LABELS, TYPE_LABELS } from './labels';

const time = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' });
const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

const PAYMENT_STATUS: Record<Payment['status'], { label: string; color: string }> = {
  PAID: { label: 'Pago', color: 'green' },
  PENDING: { label: 'A receber', color: 'yellow' },
  CANCELLED: { label: 'Cancelado', color: 'gray' },
};

export function OrderDetailDrawer({ orderId, onClose }: { orderId: string | null; onClose: () => void }) {
  const order = useOrder(orderId);
  return (
    <Drawer
      opened={orderId !== null}
      onClose={onClose}
      position="right"
      size="lg"
      title={order.data ? `Pedido ${order.data.number}` : 'Pedido'}
    >
      {order.isPending && orderId !== null && <Loader aria-label="Carregando pedido" />}
      {order.isError && (
        <Alert color="red" icon={<CircleAlert size={18} />}>
          {errorMessage(order.error)}
        </Alert>
      )}
      {order.data && <OrderDetail key={order.data.id} order={order.data} />}
    </Drawer>
  );
}

function OrderDetail({ order }: { order: Order }) {
  const { user } = useSession();
  const changeStatus = useChangeStatus();
  const [cancelling, setCancelling] = useState(false);
  const actions = nextActions(order, user.role);
  const takesOrders = ORDER_TAKERS.includes(user.role);

  function advance(status: OrderStatus) {
    changeStatus.mutate({ id: order.id, status, version: order.version });
  }

  return (
    <Stack gap="md">
      <Group gap="xs">
        <Badge color={STATUS_COLORS[order.status]}>{STATUS_LABELS[order.status]}</Badge>
        <Badge variant="light" color={order.type === 'DELIVERY' ? 'grape' : 'cyan'}>
          {TYPE_LABELS[order.type]}
        </Badge>
        <Text size="sm" c="dimmed">
          Lançado às {time.format(new Date(order.createdAt))}
        </Text>
      </Group>
      {order.status === 'CANCELLED' && order.cancelReason && (
        <Alert color="gray" title="Pedido cancelado">
          {order.cancelReason}
        </Alert>
      )}

      {(order.customerName || order.deliveryAddress) && (
        <Stack gap={2}>
          {order.customerName && (
            <Text fw={600}>
              {order.customerName}
              {order.customerPhone && (
                <Text span c="dimmed" fw={400}>
                  {' '}
                  · {formatPhone(order.customerPhone)}
                </Text>
              )}
            </Text>
          )}
          {order.deliveryAddress && (
            <Text size="sm">
              {order.deliveryAddress.street}, {order.deliveryAddress.number}
              {order.deliveryAddress.complement && `, ${order.deliveryAddress.complement}`} ·{' '}
              {order.deliveryAddress.neighborhood}
              {order.deliveryAddress.reference && (
                <Text span size="sm" c="dimmed">
                  {' '}
                  (ref.: {order.deliveryAddress.reference})
                </Text>
              )}
            </Text>
          )}
        </Stack>
      )}
      {order.notes && (
        <Alert color="orange" variant="light" p="xs">
          {order.notes}
        </Alert>
      )}

      <Stack gap={6} aria-label="Itens">
        {order.items.map((item) => (
          <Group key={item.id} justify="space-between" align="flex-start" wrap="nowrap">
            <Stack gap={0}>
              <Text fw={500}>
                {item.quantity}x {item.name}
              </Text>
              {item.options.length > 0 && (
                <Text size="xs" c="dimmed">
                  {item.options.map((option) => (option.quantity > 1 ? `${option.quantity}x ${option.name}` : option.name)).join(', ')}
                </Text>
              )}
              {item.notes && (
                <Text size="xs" c="orange.8">
                  Obs.: {item.notes}
                </Text>
              )}
            </Stack>
            <Text size="sm">{formatCents(item.totalCents)}</Text>
          </Group>
        ))}
      </Stack>
      <Stack gap={2}>
        <TotalLine label="Itens" cents={order.subtotalCents} />
        {order.discountCents > 0 && <TotalLine label="Desconto" cents={-order.discountCents} />}
        {order.type === 'DELIVERY' && <TotalLine label="Entrega" cents={order.deliveryFeeCents} />}
        <TotalLine label="Total" cents={order.totalCents} strong />
      </Stack>

      <Group gap="xs">
        <PrintMenu order={order} />
        {actions.map((action, index) => (
          <Button
            key={action.status}
            variant={index === 0 ? 'filled' : 'light'}
            loading={changeStatus.isPending && changeStatus.variables?.status === action.status}
            onClick={() => advance(action.status)}
          >
            {action.label}
          </Button>
        ))}
        {canCancel(order, user.role) && (
          <Button variant="subtle" color="red" onClick={() => setCancelling(true)}>
            Cancelar pedido
          </Button>
        )}
      </Group>

      {takesOrders && (
        <>
          <Divider />
          <Payments order={order} />
        </>
      )}
      <Divider />
      <OrderTimeline orderId={order.id} />

      <CancelModal order={order} opened={cancelling} onClose={() => setCancelling(false)} />
    </Stack>
  );
}

function TotalLine({ label, cents, strong = false }: { label: string; cents: number; strong?: boolean }) {
  return (
    <Group justify="space-between">
      <Text size="sm" fw={strong ? 700 : undefined}>
        {label}
      </Text>
      <Text size="sm" fw={strong ? 700 : undefined}>
        {cents < 0 ? `- ${formatCents(-cents)}` : formatCents(cents)}
      </Text>
    </Group>
  );
}

function Payments({ order }: { order: Order }) {
  const queryClient = useQueryClient();
  const payments = useOrderPayments(order.id);
  const methods = usePaymentMethods();
  const [methodId, setMethodId] = useState<string | null>(null);
  const [amount, setAmount] = useState<number | string>('');
  const [paid, setPaid] = useState(true);

  const counted = (payments.data ?? []).filter((payment) => payment.status !== 'CANCELLED');
  const remaining = order.totalCents - counted.reduce((sum, payment) => sum + payment.amountCents, 0);

  const refresh = () => queryClient.invalidateQueries({ queryKey: orderKeys.payments(order.id) });
  const receive = useMutation({
    mutationFn: (paymentId: string) =>
      unwrap(
        api.PATCH('/api/orders/{orderId}/payments/{paymentId}', {
          params: { path: { orderId: order.id, paymentId } },
          body: { status: 'PAID' },
        }),
      ),
    onSuccess: (payment) => {
      notifications.show({ color: 'green', message: `${payment.methodName}: ${formatCents(payment.amountCents)} recebido.` });
      void refresh();
    },
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
  });
  const register = useMutation({
    mutationFn: (body: { paymentMethodId: string; amountCents: number; paid: boolean }) =>
      unwrap(api.POST('/api/orders/{orderId}/payments', { params: { path: { orderId: order.id } }, body })),
    onSuccess: (payment) => {
      notifications.show({ color: 'green', message: `Pagamento de ${formatCents(payment.amountCents)} registrado.` });
      setMethodId(null);
      setAmount('');
      void refresh();
    },
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
  });

  const typedAmount = typeof amount === 'number' ? amount : parseDecimal(amount);
  const amountCents = Number.isFinite(typedAmount) ? Math.round(typedAmount * 100) : remaining;

  return (
    <Stack gap="xs">
      <Title order={5}>Pagamentos</Title>
      {payments.isPending && <Loader size="sm" aria-label="Carregando pagamentos" />}
      {(payments.data ?? []).map((payment) => (
        <Group key={payment.id} justify="space-between" wrap="nowrap">
          <Stack gap={0}>
            <Text size="sm" fw={500} td={payment.status === 'CANCELLED' ? 'line-through' : undefined}>
              {payment.methodName} · {formatCents(payment.amountCents)}
            </Text>
            {payment.changeCents !== null && payment.changeCents > 0 && (
              <Text size="xs" c="dimmed">
                Troco para {formatCents(payment.changeForCents ?? 0)}: levar {formatCents(payment.changeCents)}
              </Text>
            )}
          </Stack>
          <Group gap={6} wrap="nowrap">
            <Badge size="sm" variant="light" color={PAYMENT_STATUS[payment.status].color}>
              {PAYMENT_STATUS[payment.status].label}
            </Badge>
            {payment.status === 'PENDING' && order.status !== 'CANCELLED' && (
              <Button
                size="compact-xs"
                variant="light"
                loading={receive.isPending && receive.variables === payment.id}
                disabled={receive.isPending}
                onClick={() => receive.mutate(payment.id)}
              >
                Receber
              </Button>
            )}
          </Group>
        </Group>
      ))}
      {payments.data && payments.data.length === 0 && (
        <Text size="sm" c="dimmed">
          Nenhum pagamento registrado.
        </Text>
      )}
      {payments.data && remaining > 0 && order.status !== 'CANCELLED' && (
        <Stack gap={6}>
          <Text size="sm" c="orange.8">
            Falta receber {formatCents(remaining)}.
          </Text>
          <Group align="flex-end" gap="xs" wrap="nowrap">
            <Select
              aria-label="Forma de pagamento"
              placeholder="Forma"
              data={(methods.data ?? []).filter((method) => method.active).map((method) => ({ value: method.id, label: method.name }))}
              value={methodId}
              onChange={setMethodId}
              style={{ flex: 1 }}
            />
            <MoneyInput aria-label="Valor" placeholder={formatCents(remaining)} value={amount} onChange={setAmount} w={130} />
          </Group>
          <Group justify="space-between">
            <Switch label="Já pago" checked={paid} onChange={(event) => setPaid(event.currentTarget.checked)} />
            <Button
              size="xs"
              disabled={methodId === null || amountCents <= 0}
              loading={register.isPending}
              onClick={() => methodId && register.mutate({ paymentMethodId: methodId, amountCents, paid })}
            >
              Registrar pagamento
            </Button>
          </Group>
        </Stack>
      )}
    </Stack>
  );
}

function OrderTimeline({ orderId }: { orderId: string }) {
  const history = useOrderHistory(orderId);
  return (
    <Stack gap="xs">
      <Title order={5}>Linha do tempo</Title>
      {history.isPending && <Loader size="sm" aria-label="Carregando linha do tempo" />}
      {history.data && (
        <Timeline bulletSize={14} lineWidth={2} active={history.data.length - 1}>
          {history.data.map((entry, index) => (
            <Timeline.Item key={`${entry.createdAt}-${index}`} title={STATUS_LABELS[entry.toStatus]}>
              <Text size="xs" c="dimmed">
                {dateTime.format(new Date(entry.createdAt))}
                {entry.actorName ? ` · ${entry.actorName}` : entry.actorType === 'SYSTEM' ? ' · automático' : ''}
              </Text>
              {entry.reason && <Text size="xs">Motivo: {entry.reason}</Text>}
            </Timeline.Item>
          ))}
        </Timeline>
      )}
    </Stack>
  );
}

function CancelModal({ order, opened, onClose }: { order: Order; opened: boolean; onClose: () => void }) {
  const changeStatus = useChangeStatus();
  const [reason, setReason] = useState('');
  return (
    <Modal opened={opened} onClose={onClose} title={`Cancelar pedido ${order.number}`}>
      <Stack>
        <Textarea
          label="Motivo"
          description="Fica na linha do tempo do pedido."
          placeholder="Ex.: cliente desistiu"
          maxLength={300}
          autosize
          minRows={2}
          value={reason}
          onChange={(event) => setReason(event.currentTarget.value)}
          data-autofocus
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Voltar
          </Button>
          <Button
            color="red"
            disabled={reason.trim() === ''}
            loading={changeStatus.isPending}
            onClick={() =>
              changeStatus.mutate(
                { id: order.id, status: 'CANCELLED', reason: reason.trim(), version: order.version },
                { onSuccess: onClose },
              )
            }
          >
            Cancelar pedido
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
