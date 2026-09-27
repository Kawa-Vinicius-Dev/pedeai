import { Alert, Badge, Button, Card, Group, Loader, Select, Stack, Switch, Text, TextInput, Title } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CircleAlert, FlaskConical, Pause, Play, Plug } from 'lucide-react';
import { useState } from 'react';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { MarketplaceConnection } from '../../shared/api/types';
import { integrationKeys, useConnections, useIfoodSetup, useMerchants, useSimulateOrder, useUpdateConnection } from './api';

const time = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

/** Integrações da loja com marketplaces. Hoje, o iFood (docs/05-integracoes.md#implantação-de-uma-loja). */
export function IntegrationsPage() {
  const setup = useIfoodSetup();
  const connections = useConnections();

  return (
    <Stack maw={820} gap="lg">
      <Stack gap={4}>
        <Title order={2}>Integrações</Title>
        <Text c="dimmed">
          Pedidos do iFood entram no quadro como os outros, e o aceite, o preparo e o pronto voltam para o iFood sozinhos.
        </Text>
      </Stack>
      {setup.data && !setup.data.configured && (
        <Alert color={setup.data.simulator ? 'blue' : 'orange'} icon={<CircleAlert size={18} />}>
          {setup.data.simulator
            ? 'Modo simulador: o servidor ainda não tem as credenciais do iFood. Dá para ligar uma loja de teste e simular pedidos, que seguem o mesmo caminho dos reais.'
            : 'O servidor ainda não tem as credenciais do iFood Developer. Quando elas forem configuradas, a loja poderá ser ligada aqui.'}
        </Alert>
      )}
      {connections.isPending && <Loader aria-label="Carregando integrações" />}
      {connections.isError && (
        <Alert color="red" icon={<CircleAlert size={18} />}>
          {errorMessage(connections.error)}
        </Alert>
      )}
      {connections.data?.map((connection) => (
        <ConnectionCard key={connection.id} connection={connection} simulator={setup.data?.simulator ?? false} />
      ))}
      {setup.data && (setup.data.configured || setup.data.simulator) && connections.data?.length === 0 && (
        <ConnectForm configured={setup.data.configured} />
      )}
    </Stack>
  );
}

function ConnectionCard({ connection, simulator }: { connection: MarketplaceConnection; simulator: boolean }) {
  const update = useUpdateConnection();
  const simulate = useSimulateOrder();
  const active = connection.status === 'ACTIVE';

  return (
    <Card withBorder radius="lg" aria-label={`Integração ${connection.merchantName ?? connection.externalMerchantId}`}>
      <Stack>
        <Group justify="space-between">
          <Stack gap={0}>
            <Group gap="xs">
              <Title order={4}>iFood</Title>
              <Badge color={active ? 'green' : connection.status === 'PAUSED' ? 'gray' : 'red'} variant="light">
                {active ? 'Ativa' : connection.status === 'PAUSED' ? 'Pausada' : 'Com erro'}
              </Badge>
            </Group>
            <Text size="sm" c="dimmed">
              {connection.merchantName} · merchant {connection.externalMerchantId}
            </Text>
          </Stack>
          <Button
            variant="default"
            leftSection={active ? <Pause size={16} /> : <Play size={16} />}
            loading={update.isPending}
            onClick={() =>
              update.mutate({ id: connection.id, status: active ? 'PAUSED' : 'ACTIVE', autoConfirm: connection.autoConfirm })
            }
          >
            {active ? 'Pausar' : 'Reativar'}
          </Button>
        </Group>
        <Switch
          label="Aceitar pedidos automaticamente"
          description="Útil no pico. O iFood cancela sozinho pedido não aceito em 8 minutos."
          checked={connection.autoConfirm}
          onChange={(event) =>
            update.mutate({ id: connection.id, status: connection.status, autoConfirm: event.currentTarget.checked })
          }
        />
        <Text size="sm">
          Último evento: {connection.lastEventAt ? time.format(new Date(connection.lastEventAt)) : 'nenhum ainda'}
        </Text>
        {connection.failedActions > 0 && (
          <Alert color="red" variant="light" p="xs">
            {connection.failedActions === 1
              ? '1 atualização não chegou ao iFood. Veja no detalhe do pedido.'
              : `${connection.failedActions} atualizações não chegaram ao iFood. Veja no detalhe de cada pedido.`}
          </Alert>
        )}
        {connection.lastError && (
          <Text size="xs" c="red">
            {connection.lastError}
          </Text>
        )}
        {simulator && (
          <Group>
            <Button
              variant="light"
              leftSection={<FlaskConical size={16} />}
              loading={simulate.isPending}
              disabled={!active}
              onClick={() => simulate.mutate(connection.id)}
            >
              Simular pedido do iFood
            </Button>
          </Group>
        )}
      </Stack>
    </Card>
  );
}

function ConnectForm({ configured }: { configured: boolean }) {
  const queryClient = useQueryClient();
  const merchants = useMerchants(configured);
  const [merchantId, setMerchantId] = useState<string | null>(configured ? null : 'loja-teste');
  const [autoConfirm, setAutoConfirm] = useState(false);
  const connect = useMutation({
    mutationFn: () =>
      unwrap(api.POST('/api/integrations', { body: { externalMerchantId: merchantId ?? '', autoConfirm } })),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: integrationKeys.connections });
      notifications.show({ color: 'green', message: 'Loja ligada ao iFood.' });
    },
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
  });

  return (
    <Card withBorder radius="lg">
      <Stack>
        <Title order={4}>Ligar ao iFood</Title>
        {configured ? (
          <>
            <Text size="sm" c="dimmed">
              No Portal do Parceiro do iFood, aceite a permissão do aplicativo PedeAí. A loja aparece na lista em alguns
              minutos.
            </Text>
            {merchants.isError && (
              <Alert color="red" icon={<CircleAlert size={18} />}>
                {errorMessage(merchants.error)}
              </Alert>
            )}
            <Select
              label="Loja no iFood"
              placeholder={merchants.isPending ? 'Carregando...' : 'Escolha a loja'}
              data={(merchants.data ?? []).map((merchant) => ({ value: merchant.id, label: merchant.name }))}
              value={merchantId}
              onChange={setMerchantId}
            />
          </>
        ) : (
          <TextInput
            label="Merchant de teste"
            description="No modo simulador, qualquer identificador serve."
            value={merchantId ?? ''}
            onChange={(event) => setMerchantId(event.currentTarget.value)}
          />
        )}
        <Switch
          label="Aceitar pedidos automaticamente"
          checked={autoConfirm}
          onChange={(event) => setAutoConfirm(event.currentTarget.checked)}
        />
        <Group>
          <Button leftSection={<Plug size={16} />} disabled={!merchantId?.trim()} loading={connect.isPending}
                  onClick={() => connect.mutate()}>
            Ligar ao iFood
          </Button>
        </Group>
      </Stack>
    </Card>
  );
}
