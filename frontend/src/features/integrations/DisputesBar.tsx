import { Alert, Button, Group, Select, Stack, Text } from '@mantine/core';
import { MessageCircleWarning } from 'lucide-react';
import { useState } from 'react';
import type { Dispute } from '../../shared/api/types';
import { SOURCE_LABELS } from '../orders/labels';
import { useAnswerDispute, useDisputes } from './api';

const hour = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' });

/**
 * Faixa no topo quando o cliente pede no app para cancelar: o prazo para responder é curto e, sem resposta, quem
 * decide é o app (docs/05-integracoes.md#cancelamento-pedido-pelo-cliente).
 */
export function DisputesBar({ enabled }: { enabled: boolean }) {
  const disputes = useDisputes(enabled);
  if (!disputes.data || disputes.data.length === 0) {
    return null;
  }
  return (
    <Stack gap="xs" mb="md">
      {disputes.data.map((dispute) => (
        <DisputeAlert key={dispute.id} dispute={dispute} />
      ))}
    </Stack>
  );
}

function DisputeAlert({ dispute }: { dispute: Dispute }) {
  const answer = useAnswerDispute();
  const [reason, setReason] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState(false);
  const app = SOURCE_LABELS[dispute.provider] ?? dispute.provider;

  return (
    <Alert
      color="orange"
      variant="light"
      icon={<MessageCircleWarning size={18} />}
      title={`O cliente pediu para cancelar o pedido #${dispute.orderNumber} (${app})`}
    >
      <Stack gap="xs">
        {dispute.message && <Text size="sm">“{dispute.message}”</Text>}
        <Text size="sm" c="dimmed">
          {dispute.expiresAt
            ? `Responda até ${hour.format(new Date(dispute.expiresAt))}. Depois disso, quem decide é o app.`
            : 'Sem resposta, quem decide é o app.'}
        </Text>
        {rejecting ? (
          <Group align="flex-end">
            <Select
              label="Motivo da recusa"
              placeholder="Escolha o motivo"
              data={dispute.rejectReasons.map((option) => ({ value: option.code, label: option.description }))}
              value={reason}
              onChange={setReason}
              w={280}
            />
            <Button
              color="orange"
              disabled={!reason}
              loading={answer.isPending}
              onClick={() => answer.mutate({ id: dispute.id, accept: false, rejectCode: reason ?? undefined })}
            >
              Recusar cancelamento
            </Button>
            <Button variant="subtle" color="gray" onClick={() => setRejecting(false)}>
              Voltar
            </Button>
          </Group>
        ) : (
          <Group>
            <Button color="red" loading={answer.isPending} onClick={() => answer.mutate({ id: dispute.id, accept: true })}>
              Aceitar cancelamento
            </Button>
            <Button variant="default" onClick={() => setRejecting(true)}>
              Recusar
            </Button>
          </Group>
        )}
      </Stack>
    </Alert>
  );
}
