import { Alert, Badge, Button, Card, Group, Loader, Modal, Select, Stack, Switch, Text, TextInput, Title } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { BookDown, CircleAlert, FlaskConical, Pause, Play, Plug } from 'lucide-react';
import { useState } from 'react';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { CatalogImport, MarketplaceConnection, Platform } from '../../shared/api/types';
import { catalogKeys } from '../catalog/api';
import { ImportResult } from '../catalog/ImportResult';
import { integrationKeys, useConnections, useMerchants, usePlatforms, useSimulateOrder, useUpdateConnection } from './api';

const time = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

/**
 * Integrações da loja com os apps de pedido: iFood pela integração própria, 99Food e outro app compatível pelo padrão
 * Open Delivery (docs/05-integracoes.md#implantação-de-uma-loja).
 */
export function IntegrationsPage() {
  const platforms = usePlatforms();
  const connections = useConnections();
  const available = (platforms.data ?? []).filter((platform) => platform.configured || platform.simulator);
  const linked = new Set((connections.data ?? []).map((connection) => connection.provider));
  const linkable = available.filter((platform) => !linked.has(platform.provider));
  const simulated = available.filter((platform) => !platform.configured);

  return (
    <Stack maw={820} gap="lg">
      <Stack gap={4}>
        <Title order={2}>Integrações</Title>
        <Text c="dimmed">
          Pedidos do iFood e dos apps no padrão Open Delivery (como a 99Food) entram no quadro como os outros, e o aceite,
          o preparo e o pronto voltam para o app sozinhos.
        </Text>
      </Stack>
      {platforms.data && simulated.length > 0 && (
        <Alert color="blue" icon={<CircleAlert size={18} />}>
          Modo simulador: o servidor ainda não tem as credenciais de {simulated.map((platform) => platform.name).join(', ')}.
          Dá para ligar uma loja de teste e simular pedidos, que seguem o mesmo caminho dos reais.
        </Alert>
      )}
      {platforms.data && available.length === 0 && (
        <Alert color="orange" icon={<CircleAlert size={18} />}>
          O servidor ainda não tem credenciais de nenhum app de pedidos. Quando elas forem configuradas, a loja poderá ser
          ligada aqui.
        </Alert>
      )}
      {connections.isPending && <Loader aria-label="Carregando integrações" />}
      {connections.isError && (
        <Alert color="red" icon={<CircleAlert size={18} />}>
          {errorMessage(connections.error)}
        </Alert>
      )}
      {connections.data?.map((connection) => (
        <ConnectionCard
          key={connection.id}
          connection={connection}
          platform={platforms.data?.find((platform) => platform.provider === connection.provider)}
        />
      ))}
      {connections.data && linkable.length > 0 && <ConnectForm platforms={linkable} />}
    </Stack>
  );
}

function ConnectionCard({ connection, platform }: { connection: MarketplaceConnection; platform?: Platform }) {
  const update = useUpdateConnection();
  const simulate = useSimulateOrder();
  const [importing, setImporting] = useState(false);
  const active = connection.status === 'ACTIVE';
  const name = platform?.name ?? connection.provider;
  const simulator = platform !== undefined && !platform.configured && platform.simulator;

  return (
    <Card withBorder radius="lg" aria-label={`Integração ${connection.merchantName ?? connection.externalMerchantId}`}>
      <Stack>
        <Group justify="space-between">
          <Stack gap={0}>
            <Group gap="xs">
              <Title order={4}>{name}</Title>
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
          description="Útil no pico. Os apps cancelam sozinhos o pedido que demora a ser aceito."
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
              ? `1 atualização não chegou ao ${name}. Veja no detalhe do pedido.`
              : `${connection.failedActions} atualizações não chegaram ao ${name}. Veja no detalhe de cada pedido.`}
          </Alert>
        )}
        {connection.lastError && (
          <Text size="xs" c="red">
            {connection.lastError}
          </Text>
        )}
        <Group>
          {connection.provider === 'IFOOD' && (
            <Button variant="default" leftSection={<BookDown size={16} />} onClick={() => setImporting(true)}>
              Importar cardápio do iFood
            </Button>
          )}
          {simulator && (
            <Button
              variant="light"
              leftSection={<FlaskConical size={16} />}
              loading={simulate.isPending}
              disabled={!active}
              onClick={() => simulate.mutate(connection.id)}
            >
              Simular pedido do {name}
            </Button>
          )}
        </Group>
      </Stack>
      <IfoodCatalogImportModal connectionId={connection.id} opened={importing} onClose={() => setImporting(false)} />
    </Card>
  );
}

/** Traz categorias, produtos (com o código PDV) e adicionais do iFood. Pré-visualiza antes de gravar. */
function IfoodCatalogImportModal({ connectionId, opened, onClose }: {
  connectionId: string;
  opened: boolean;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [result, setResult] = useState<{ data: CatalogImport; preview: boolean } | null>(null);
  const run = useMutation({
    mutationFn: (dryRun: boolean) =>
      unwrap(api.POST('/api/integrations/{id}/catalog-import', { params: { path: { id: connectionId } }, body: { dryRun } })),
    onSuccess: async (data, dryRun) => {
      setResult({ data, preview: dryRun });
      if (data.applied) {
        await queryClient.invalidateQueries({ queryKey: catalogKeys.all });
      }
    },
  });
  const close = () => {
    setResult(null);
    onClose();
  };

  return (
    <Modal opened={opened} onClose={close} title="Importar cardápio do iFood" size="lg">
      <Stack>
        <Text size="sm" c="dimmed">
          Traz as categorias, os produtos com o código PDV e os grupos de adicionais. Produto com o mesmo código é
          atualizado; o resto do cardápio do PedeAí fica como está.
        </Text>
        {run.isError && (
          <Alert color="red" icon={<CircleAlert size={18} />}>
            {errorMessage(run.error)}
          </Alert>
        )}
        {result && <ImportResult result={result.data} preview={result.preview} />}
        <Group justify="flex-end">
          {result && !result.preview && result.data.applied ? (
            <Button onClick={close}>Fechar</Button>
          ) : (
            <>
              <Button variant="default" loading={run.isPending && run.variables} onClick={() => run.mutate(true)}>
                Pré-visualizar
              </Button>
              <Button
                disabled={!(result?.preview && result.data.errors.length === 0)}
                loading={run.isPending && !run.variables}
                onClick={() => run.mutate(false)}
              >
                Importar
              </Button>
            </>
          )}
        </Group>
      </Stack>
    </Modal>
  );
}

function ConnectForm({ platforms }: { platforms: Platform[] }) {
  const queryClient = useQueryClient();
  const [provider, setProvider] = useState<Platform['provider']>(platforms[0].provider);
  const platform = platforms.find((candidate) => candidate.provider === provider) ?? platforms[0];
  const listMerchants = platform.provider === 'IFOOD' && platform.configured;
  const merchants = useMerchants(listMerchants);
  const [merchantId, setMerchantId] = useState<string | null>(platform.configured ? null : 'loja-teste');
  const [autoConfirm, setAutoConfirm] = useState(false);
  const connect = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST('/api/integrations', {
          body: { provider: platform.provider, externalMerchantId: merchantId ?? '', autoConfirm },
        }),
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: integrationKeys.connections });
      notifications.show({ color: 'green', message: `Loja ligada ao ${platform.name}.` });
    },
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
  });

  return (
    <Card withBorder radius="lg">
      <Stack>
        <Title order={4}>Ligar a um app de pedidos</Title>
        {platforms.length > 1 && (
          <Select
            label="App"
            data={platforms.map((candidate) => ({ value: candidate.provider, label: candidate.name }))}
            value={platform.provider}
            onChange={(value) => {
              if (value) {
                const next = platforms.find((candidate) => candidate.provider === value);
                setProvider(value as Platform['provider']);
                setMerchantId(next?.configured ? null : 'loja-teste');
              }
            }}
            allowDeselect={false}
          />
        )}
        {listMerchants ? (
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
            label={platform.configured ? `Id da loja no ${platform.name}` : 'Merchant de teste'}
            description={
              platform.configured
                ? 'O identificador da loja (merchant) que aparece no painel do app.'
                : 'No modo simulador, qualquer identificador serve.'
            }
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
          <Button
            leftSection={<Plug size={16} />}
            disabled={!merchantId?.trim()}
            loading={connect.isPending}
            onClick={() => connect.mutate()}
          >
            Ligar ao {platform.name}
          </Button>
        </Group>
      </Stack>
    </Card>
  );
}
