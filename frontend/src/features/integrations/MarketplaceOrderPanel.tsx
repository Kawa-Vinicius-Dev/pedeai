import { Alert, Badge, Button, Group, Loader, Modal, Radio, Stack, Text, Title } from '@mantine/core';
import { CircleAlert, RotateCcw } from 'lucide-react';
import { useState } from 'react';
import { errorMessage } from '../../shared/api/errors';
import type { MarketplaceAction, Order } from '../../shared/api/types';
import { useSession } from '../auth/auth-context';
import { canRequestMarketplaceCancel, sourceBadge } from '../orders/labels';
import {
  ACTION_LABELS,
  useCancellationReasons,
  useMarketplaceSync,
  useRequestCancellation,
  useRetryAction,
} from './api';

const ACTION_STATUS: Record<MarketplaceAction['status'], { label: string; color: string }> = {
  PENDING: { label: 'Enviando', color: 'blue' },
  DONE: { label: 'Enviado', color: 'green' },
  FAILED: { label: 'Falhou', color: 'red' },
  SKIPPED: { label: 'Não enviado', color: 'gray' },
};

/**
 * No detalhe de um pedido do iFood: o que foi sincronizado com a plataforma e o pedido de cancelamento, que só vale
 * quando o iFood confirma (docs/05-integracoes.md#cancelamento).
 */
export function MarketplaceOrderPanel({ order }: { order: Order }) {
  const { user } = useSession();
  const sync = useMarketplaceSync(order.id);
  const retry = useRetryAction(order.id);
  const [cancelling, setCancelling] = useState(false);
  const cancellationRequested = sync.data?.some(
    (action) => action.action === 'REQUEST_CANCELLATION' && (action.status === 'PENDING' || action.status === 'DONE'),
  );

  return (
    <Stack gap="xs" aria-label="Sincronização com o iFood">
      <Group justify="space-between">
        <Title order={5}>{sourceBadge(order)}</Title>
        {canRequestMarketplaceCancel(order, user.role) && !cancellationRequested && (
          <Button size="xs" variant="subtle" color="red" onClick={() => setCancelling(true)}>
            Solicitar cancelamento
          </Button>
        )}
      </Group>
      {cancellationRequested && order.status !== 'CANCELLED' && (
        <Alert color="orange" variant="light" p="xs">
          Cancelamento solicitado ao iFood. O pedido é cancelado aqui quando o iFood confirmar.
        </Alert>
      )}
      {sync.data?.map((action) => (
        <Group key={action.id} justify="space-between" wrap="nowrap" gap="xs">
          <Stack gap={0}>
            <Text size="sm">{ACTION_LABELS[action.action] ?? action.action}</Text>
            {action.lastError && action.status !== 'DONE' && (
              <Text size="xs" c="dimmed">
                {action.lastError}
              </Text>
            )}
          </Stack>
          <Group gap={6} wrap="nowrap">
            <Badge variant="light" color={ACTION_STATUS[action.status].color}>
              {ACTION_STATUS[action.status].label}
            </Badge>
            {action.status === 'FAILED' && action.action !== 'REQUEST_CANCELLATION' && (
              <Button
                size="xs"
                variant="subtle"
                leftSection={<RotateCcw size={14} />}
                loading={retry.isPending && retry.variables === action.id}
                onClick={() => retry.mutate(action.id)}
              >
                Tentar de novo
              </Button>
            )}
          </Group>
        </Group>
      ))}
      <CancelRequestModal order={order} opened={cancelling} onClose={() => setCancelling(false)} />
    </Stack>
  );
}

function CancelRequestModal({ order, opened, onClose }: { order: Order; opened: boolean; onClose: () => void }) {
  const reasons = useCancellationReasons(order.id, opened);
  const request = useRequestCancellation(order.id);
  const [code, setCode] = useState<string | null>(null);
  const chosen = reasons.data?.find((reason) => reason.code === code);

  return (
    <Modal opened={opened} onClose={onClose} title={`Solicitar cancelamento do pedido ${order.number}`}>
      <Stack>
        <Text size="sm" c="dimmed">
          O iFood só aceita os motivos abaixo para este pedido agora. O pedido é cancelado quando ele confirmar.
        </Text>
        {reasons.isPending && <Loader size="sm" aria-label="Carregando motivos" />}
        {reasons.isError && (
          <Alert color="red" icon={<CircleAlert size={18} />}>
            {errorMessage(reasons.error)}
          </Alert>
        )}
        {reasons.data && (
          <Radio.Group value={code} onChange={setCode} label="Motivo">
            <Stack gap="xs" mt="xs">
              {reasons.data.map((reason) => (
                <Radio key={reason.code} value={reason.code} label={reason.description} />
              ))}
            </Stack>
          </Radio.Group>
        )}
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Voltar
          </Button>
          <Button
            color="red"
            disabled={!chosen}
            loading={request.isPending}
            onClick={() =>
              chosen &&
              request.mutate({ code: chosen.code, description: chosen.description }, { onSuccess: onClose })
            }
          >
            Solicitar cancelamento
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
