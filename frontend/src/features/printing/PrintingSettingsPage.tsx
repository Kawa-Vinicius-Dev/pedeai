import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Badge,
  Button,
  Card,
  Code,
  Group,
  Loader,
  Modal,
  NumberInput,
  SegmentedControl,
  Select,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CircleAlert, Pencil, Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { PairingCode, PrintAgent, Printer, PrinterRequest, Sector, SectorPrinter } from '../../shared/api/types';
import { applyApiError } from '../../shared/lib/forms';
import { useSectors } from '../catalog/api';
import {
  CODEPAGE_OPTIONS,
  CUT_OPTIONS,
  printingKeys,
  STATUS_COLORS,
  STATUS_LABELS,
  useAssignSectorPrinter,
  usePrintAgents,
  usePrinters,
  useRevokeAgent,
  useSectorPrinters,
} from './api';

const time = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' });

/** Configurações de impressão: computadores com o agente, impressoras e a impressora de cada setor. */
export function PrintingSettingsPage() {
  return (
    <Stack maw={900} gap="lg">
      <Stack gap={4}>
        <Title order={2}>Impressão</Title>
        <Text c="dimmed">
          O agente de impressão roda num computador da loja e manda os pedidos para as impressoras térmicas.
        </Text>
      </Stack>
      <AgentsCard />
      <PrintersCard />
      <SectorsCard />
    </Stack>
  );
}

function AgentsCard() {
  const agents = usePrintAgents();
  const queryClient = useQueryClient();
  const revoke = useRevokeAgent();
  const [pairing, setPairing] = useState<PairingCode | null>(null);
  const [removing, setRemoving] = useState<PrintAgent | null>(null);
  const createCode = useMutation({
    mutationFn: () => unwrap(api.POST('/api/print-agents/pairing-codes')),
    onSuccess: setPairing,
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
  });

  return (
    <Card withBorder radius="lg">
      <Stack>
        <Group justify="space-between">
          <Title order={4}>Computadores de impressão</Title>
          <Button leftSection={<Plus size={18} />} loading={createCode.isPending} onClick={() => createCode.mutate()}>
            Adicionar computador
          </Button>
        </Group>
        <QueryState query={agents} label="Carregando computadores" />
        {agents.data?.length === 0 && (
          <Text c="dimmed">Nenhum computador ainda. Instale o agente no computador do caixa e pareie com um código.</Text>
        )}
        {agents.data?.map((agent) => (
          <Group key={agent.id} justify="space-between" wrap="nowrap" aria-label={`Computador ${agent.name}`}>
            <Stack gap={0}>
              <Text fw={600}>{agent.name}</Text>
              <Text size="sm" c="dimmed">
                {[agent.os, agent.agentVersion && `agente ${agent.agentVersion}`].filter(Boolean).join(' · ')}
              </Text>
            </Stack>
            <Group gap="xs" wrap="nowrap">
              {agent.outdated && (
                <Badge color="orange" variant="outline" title="Instale a versão nova do agente neste computador.">
                  Desatualizado
                </Badge>
              )}
              <Badge color={agent.online ? 'green' : 'red'} variant="light">
                {agent.online
                  ? 'Online'
                  : agent.lastSeenAt
                    ? `Offline desde ${time.format(new Date(agent.lastSeenAt))}`
                    : 'Offline'}
              </Badge>
              <Button
                variant="subtle"
                color="red"
                size="xs"
                leftSection={<Trash2 size={14} />}
                onClick={() => setRemoving(agent)}
                aria-label={`Remover ${agent.name}`}
              >
                Remover
              </Button>
            </Group>
          </Group>
        ))}
      </Stack>

      <Modal
        opened={pairing !== null}
        onClose={() => {
          setPairing(null);
          void queryClient.invalidateQueries({ queryKey: printingKeys.agents });
        }}
        title="Parear computador"
      >
        {pairing && (
          <Stack>
            <Text>No computador da loja, abra o agente de impressão e digite:</Text>
            <Text size="sm">
              Endereço: <Code>{window.location.origin}</Code>
            </Text>
            <Code block fz={36} ta="center" fw={800} aria-label="Código de pareamento">
              {pairing.code}
            </Code>
            <Text size="sm" c="dimmed">
              Vale até {time.format(new Date(pairing.expiresAt))} e uma vez só.
            </Text>
          </Stack>
        )}
      </Modal>

      <Modal opened={removing !== null} onClose={() => setRemoving(null)} title={`Remover ${removing?.name ?? ''}`}>
        <Stack>
          <Text>
            O agente desse computador para de imprimir na hora. As impressoras continuam cadastradas para você passar
            para outro computador.
          </Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setRemoving(null)}>
              Cancelar
            </Button>
            <Button
              color="red"
              loading={revoke.isPending}
              onClick={() => removing && revoke.mutate(removing.id, { onSuccess: () => setRemoving(null) })}
            >
              Remover
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Card>
  );
}

function PrintersCard() {
  const printers = usePrinters();
  const agents = usePrintAgents();
  const [editing, setEditing] = useState<Printer | 'new' | null>(null);
  const hasAgent = (agents.data?.length ?? 0) > 0;

  return (
    <Card withBorder radius="lg">
      <Stack>
        <Group justify="space-between">
          <Title order={4}>Impressoras</Title>
          <Button leftSection={<Plus size={18} />} disabled={!hasAgent} onClick={() => setEditing('new')}>
            Nova impressora
          </Button>
        </Group>
        <QueryState query={printers} label="Carregando impressoras" />
        {printers.data?.length === 0 && (
          <Text c="dimmed">
            {hasAgent ? 'Nenhuma impressora ainda.' : 'Adicione um computador de impressão antes das impressoras.'}
          </Text>
        )}
        {printers.data && printers.data.length > 0 && (
          <Table verticalSpacing="sm">
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Impressora</Table.Th>
                <Table.Th>Ligada por</Table.Th>
                <Table.Th>Situação</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {printers.data.map((printer) => (
                <Table.Tr key={printer.id}>
                  <Table.Td>
                    <Text fw={500}>{printer.name}</Text>
                    <Text size="xs" c="dimmed">
                      {printer.paperWidthMm}mm · {printer.columns} colunas
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    {printer.connectionType === 'NETWORK'
                      ? `Rede ${printer.host}:${printer.port}`
                      : `USB (${printer.systemName})`}
                  </Table.Td>
                  <Table.Td>
                    <Badge color={printer.active ? STATUS_COLORS[printer.status] : 'gray'} variant="light">
                      {printer.active ? STATUS_LABELS[printer.status] : 'Inativa'}
                    </Badge>
                    {printer.active && printer.statusDetail && (
                      <Text size="xs" c="dimmed">
                        {printer.statusDetail}
                      </Text>
                    )}
                  </Table.Td>
                  <Table.Td ta="right">
                    <Button
                      variant="subtle"
                      size="xs"
                      leftSection={<Pencil size={14} />}
                      onClick={() => setEditing(printer)}
                      aria-label={`Editar ${printer.name}`}
                    >
                      Editar
                    </Button>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Stack>
      <Modal
        opened={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'Nova impressora' : editing ? `Editar ${editing.name}` : ''}
      >
        {editing && (
          <PrinterForm
            key={editing === 'new' ? 'new' : editing.id}
            printer={editing === 'new' ? null : editing}
            agents={agents.data ?? []}
            onClose={() => setEditing(null)}
          />
        )}
      </Modal>
    </Card>
  );
}

const printerSchema = z
  .object({
    agentId: z.string().min(1, 'Escolha o computador.'),
    name: z.string().trim().min(1, 'Informe o nome.').max(60, 'Use até 60 caracteres.'),
    connectionType: z.enum(['NETWORK', 'SYSTEM']),
    host: z.string().trim(),
    port: z.number().int().min(1, 'Porta inválida.').max(65535, 'Porta inválida.'),
    systemName: z.string().trim(),
    paperWidthMm: z.union([z.literal(58), z.literal(80)]),
    columns: z.number().int().min(24, 'Use de 24 a 64.').max(64, 'Use de 24 a 64.'),
    codepage: z.enum(['PC437', 'PC850', 'PC860', 'WPC1252', 'PC858', 'NO_ACCENTS']),
    cutMode: z.enum(['PARTIAL', 'FULL', 'NONE']),
    active: z.boolean(),
  })
  .superRefine((form, context) => {
    if (form.connectionType === 'NETWORK' && !form.host) {
      context.addIssue({ code: 'custom', path: ['host'], message: 'Informe o IP da impressora.' });
    }
    if (form.connectionType === 'SYSTEM' && !form.systemName) {
      context.addIssue({ code: 'custom', path: ['systemName'], message: 'Informe o nome exato no Windows.' });
    }
  });

type PrinterForm = z.infer<typeof printerSchema>;

function PrinterForm({ printer, agents, onClose }: { printer: Printer | null; agents: PrintAgent[]; onClose: () => void }) {
  const queryClient = useQueryClient();
  const {
    register,
    control,
    handleSubmit,
    setError,
    setValue,
    formState: { errors },
  } = useForm<PrinterForm>({
    resolver: zodResolver(printerSchema),
    defaultValues: {
      agentId: printer?.agentId ?? (agents.length === 1 ? agents[0].id : ''),
      name: printer?.name ?? '',
      connectionType: printer?.connectionType ?? 'NETWORK',
      host: printer?.host ?? '',
      port: printer?.port ?? 9100,
      systemName: printer?.systemName ?? '',
      paperWidthMm: printer?.paperWidthMm === 58 ? 58 : 80,
      columns: printer?.columns ?? 48,
      codepage: printer?.codepage ?? 'PC860',
      cutMode: printer?.cutMode ?? 'PARTIAL',
      active: printer?.active ?? true,
    },
  });
  const connectionType = useWatch({ control, name: 'connectionType' });
  const save = useMutation({
    mutationFn: (body: PrinterRequest) =>
      printer
        ? unwrap(api.PUT('/api/printers/{id}', { params: { path: { id: printer.id } }, body }))
        : unwrap(api.POST('/api/printers', { body })),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: printingKeys.all });
      notifications.show({ color: 'green', message: `Impressora ${saved.name} salva.` });
      onClose();
    },
    onError: (error) => applyApiError(error, setError),
  });

  return (
    <form
      onSubmit={handleSubmit((form) =>
        save.mutate({
          ...form,
          host: form.connectionType === 'NETWORK' ? form.host : undefined,
          port: form.connectionType === 'NETWORK' ? form.port : undefined,
          systemName: form.connectionType === 'SYSTEM' ? form.systemName : undefined,
        }),
      )}
      noValidate
    >
      <Stack>
        {errors.root && (
          <Alert color="red" icon={<CircleAlert size={18} />}>
            {errors.root.message}
          </Alert>
        )}
        <TextInput label="Nome" placeholder="Cozinha" data-autofocus {...register('name')} error={errors.name?.message} />
        <Controller
          control={control}
          name="agentId"
          render={({ field }) => (
            <Select
              label="Computador"
              data={agents.map((agent) => ({ value: agent.id, label: agent.name }))}
              value={field.value || null}
              onChange={(value) => field.onChange(value ?? '')}
              error={errors.agentId?.message}
            />
          )}
        />
        <Controller
          control={control}
          name="connectionType"
          render={({ field }) => (
            <SegmentedControl
              aria-label="Conexão"
              value={field.value}
              onChange={(value) => field.onChange(value)}
              data={[
                { value: 'NETWORK', label: 'Rede (IP)' },
                { value: 'SYSTEM', label: 'USB pelo Windows' },
              ]}
            />
          )}
        />
        {connectionType === 'NETWORK' ? (
          <Group grow align="flex-start">
            <TextInput label="IP" placeholder="192.168.0.50" {...register('host')} error={errors.host?.message} />
            <Controller
              control={control}
              name="port"
              render={({ field }) => (
                <NumberInput
                  label="Porta"
                  value={field.value}
                  onChange={(value) => field.onChange(Number(value))}
                  error={errors.port?.message}
                />
              )}
            />
          </Group>
        ) : (
          <TextInput
            label="Nome no Windows"
            description="Igual ao que o agente mostra em listar."
            {...register('systemName')}
            error={errors.systemName?.message}
          />
        )}
        <Group grow align="flex-start">
          <Controller
            control={control}
            name="paperWidthMm"
            render={({ field }) => (
              <Select
                label="Papel"
                data={[
                  { value: '80', label: '80mm' },
                  { value: '58', label: '58mm' },
                ]}
                value={String(field.value)}
                allowDeselect={false}
                onChange={(value) => {
                  const width = value === '58' ? 58 : 80;
                  field.onChange(width);
                  setValue('columns', width === 58 ? 32 : 48);
                }}
              />
            )}
          />
          <Controller
            control={control}
            name="columns"
            render={({ field }) => (
              <NumberInput
                label="Colunas"
                value={field.value}
                onChange={(value) => field.onChange(Number(value))}
                error={errors.columns?.message}
              />
            )}
          />
        </Group>
        <Controller
          control={control}
          name="codepage"
          render={({ field }) => (
            <Select
              label="Tabela de caracteres"
              description="A linha da página de teste em que os acentos saíram certos."
              data={CODEPAGE_OPTIONS}
              value={field.value}
              allowDeselect={false}
              onChange={(value) => value && field.onChange(value)}
            />
          )}
        />
        <Controller
          control={control}
          name="cutMode"
          render={({ field }) => (
            <Select
              label="Corte do papel"
              data={CUT_OPTIONS}
              value={field.value}
              allowDeselect={false}
              onChange={(value) => value && field.onChange(value)}
            />
          )}
        />
        {printer && (
          <Controller
            control={control}
            name="active"
            render={({ field }) => (
              <Switch
                label="Impressora ativa"
                checked={field.value}
                onChange={(event) => field.onChange(event.currentTarget.checked)}
              />
            )}
          />
        )}
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" loading={save.isPending}>
            Salvar
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

function SectorsCard() {
  const sectors = useSectors();
  const printers = usePrinters();
  const assigned = useSectorPrinters();
  const activeSectors = (sectors.data ?? []).filter((sector) => sector.active);
  const printerOptions = (printers.data ?? [])
    .filter((printer) => printer.active)
    .map((printer) => ({ value: printer.id, label: printer.name }));

  return (
    <Card withBorder radius="lg">
      <Stack>
        <Stack gap={4}>
          <Title order={4}>Impressora de cada setor</Title>
          <Text size="sm" c="dimmed">
            Para onde vai o ticket de produção. A reserva recebe quando a principal está offline.
          </Text>
        </Stack>
        <QueryState query={assigned} label="Carregando setores" />
        {activeSectors.length === 0 && <Text c="dimmed">Cadastre os setores no cardápio.</Text>}
        {printerOptions.length === 0 && activeSectors.length > 0 && (
          <Text c="dimmed">Cadastre uma impressora para escolher aqui.</Text>
        )}
        {assigned.data &&
          printerOptions.length > 0 &&
          activeSectors.map((sector) => (
            <SectorRow
              key={sector.id}
              sector={sector}
              current={assigned.data.find((item) => item.sectorId === sector.id)}
              printerOptions={printerOptions}
            />
          ))}
      </Stack>
    </Card>
  );
}

function SectorRow({
  sector,
  current,
  printerOptions,
}: {
  sector: Sector;
  current: SectorPrinter | undefined;
  printerOptions: { value: string; label: string }[];
}) {
  const assign = useAssignSectorPrinter();
  const [printerId, setPrinterId] = useState<string | null>(current?.printerId ?? null);
  const [backupId, setBackupId] = useState<string | null>(current?.backupPrinterId ?? null);
  const [copies, setCopies] = useState(current?.copies ?? 1);
  const [enabled, setEnabled] = useState(current?.enabled ?? true);

  return (
    <Group align="flex-end" wrap="wrap" aria-label={`Setor ${sector.name}`}>
      <Text fw={600} w={120}>
        {sector.name}
      </Text>
      <Select
        label="Impressora"
        placeholder="Não imprime"
        data={printerOptions}
        value={printerId}
        onChange={setPrinterId}
        w={180}
      />
      <Select
        label="Reserva"
        placeholder="Nenhuma"
        clearable
        data={printerOptions.filter((option) => option.value !== printerId)}
        value={backupId}
        onChange={setBackupId}
        w={180}
      />
      <NumberInput label="Cópias" min={1} max={5} value={copies} onChange={(value) => setCopies(Number(value))} w={80} />
      <Switch label="Ligada" checked={enabled} onChange={(event) => setEnabled(event.currentTarget.checked)} mb={8} />
      <Button
        variant="light"
        disabled={!printerId}
        loading={assign.isPending}
        onClick={() =>
          printerId &&
          assign.mutate({
            sectorId: sector.id,
            body: { printerId, backupPrinterId: backupId ?? undefined, copies, enabled },
          })
        }
      >
        Salvar
      </Button>
    </Group>
  );
}

function QueryState({ query, label }: { query: { isPending: boolean; isError: boolean; error: unknown }; label: string }) {
  if (query.isPending) {
    return <Loader size="sm" aria-label={label} />;
  }
  if (query.isError) {
    return (
      <Alert color="red" icon={<CircleAlert size={18} />}>
        {errorMessage(query.error)}
      </Alert>
    );
  }
  return null;
}
