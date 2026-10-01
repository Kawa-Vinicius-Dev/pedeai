import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Badge,
  Button,
  Card,
  Group,
  Loader,
  Menu,
  Modal,
  Pagination,
  Stack,
  Table,
  Text,
  Textarea,
  TextInput,
  Title,
} from '@mantine/core';
import { ArrowDownToLine, ArrowUpFromLine, CircleAlert, Lock, Printer, Wallet } from 'lucide-react';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { errorMessage } from '../../shared/api/errors';
import type { CashLine, CashSession } from '../../shared/api/types';
import { applyApiError } from '../../shared/lib/forms';
import { formatCents, moneyField, parseDecimal } from '../../shared/lib/numbers';
import { MoneyInput } from '../../shared/ui/MoneyInput';
import { usePrinters } from '../printing/api';
import {
  useCashHistory,
  useCashMovement,
  useCashSession,
  useCloseCash,
  useCurrentCash,
  useOpenCash,
  usePrintCashReport,
} from './api';

const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
const time = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' });

type MovementType = 'WITHDRAWAL' | 'DEPOSIT';
const MOVEMENT_LABELS: Record<MovementType, string> = { WITHDRAWAL: 'Sangria', DEPOSIT: 'Suprimento' };

/**
 * Caixa (docs/01-fluxos.md): abrir com o troco, sangria e suprimento durante o turno, e fechar contando cada forma de
 * pagamento. O esperado vem dos pagamentos recebidos na loja desde a abertura.
 */
export function CashPage() {
  const current = useCurrentCash();
  const [viewing, setViewing] = useState<string | null>(null);

  return (
    <Stack maw={960} gap="lg">
      <Stack gap={4}>
        <Title order={2}>Caixa</Title>
        <Text c="dimmed">Abertura, sangria, suprimento e fechamento com a conferência de cada forma de pagamento.</Text>
      </Stack>
      {current.isPending && <Loader aria-label="Carregando o caixa" />}
      {current.isError && (
        <Alert color="red" icon={<CircleAlert size={18} />}>
          {errorMessage(current.error)}
        </Alert>
      )}
      {current.data === null && <OpenCashCard />}
      {current.data && <OpenSession session={current.data} />}
      <CashHistory onView={setViewing} />
      <Modal opened={viewing !== null} onClose={() => setViewing(null)} title="Caixa" size="lg">
        {viewing && <SessionDetail id={viewing} />}
      </Modal>
    </Stack>
  );
}

const openSchema = z.object({ opening: moneyField('Informe o troco na gaveta.') });

function OpenCashCard() {
  const open = useOpenCash();
  const {
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<z.input<typeof openSchema>, unknown, z.output<typeof openSchema>>({
    resolver: zodResolver(openSchema),
    defaultValues: { opening: '' },
  });

  return (
    <Card withBorder radius="lg">
      <form
        onSubmit={handleSubmit(({ opening }) => open.mutate(opening, { onError: (error) => applyApiError(error, setError) }))}
        noValidate
      >
        <Stack>
          <Group gap="xs">
            <Wallet size={20} />
            <Text fw={600}>O caixa está fechado</Text>
          </Group>
          {errors.root && (
            <Alert color="red" icon={<CircleAlert size={18} />}>
              {errors.root.message}
            </Alert>
          )}
          <Group align="flex-end">
            <Controller
              control={control}
              name="opening"
              render={({ field }) => (
                <MoneyInput
                  label="Troco na gaveta"
                  value={field.value}
                  onChange={field.onChange}
                  error={errors.opening?.message}
                  w={200}
                />
              )}
            />
            <Button type="submit" loading={open.isPending}>
              Abrir caixa
            </Button>
          </Group>
        </Stack>
      </form>
    </Card>
  );
}

function OpenSession({ session }: { session: CashSession }) {
  const [movement, setMovement] = useState<MovementType | null>(null);
  const [closing, setClosing] = useState(false);

  return (
    <Card withBorder radius="lg">
      <Stack>
        <Group justify="space-between" align="flex-start">
          <Stack gap={2}>
            <Group gap="xs">
              <Badge color="green" variant="light">
                Aberto
              </Badge>
              <Text fw={600}>desde {dateTime.format(new Date(session.openedAt))}</Text>
            </Group>
            <Text size="sm" c="dimmed">
              {session.openedByName ? `Aberto por ${session.openedByName} · ` : ''}troco inicial{' '}
              {formatCents(session.openingAmountCents)}
            </Text>
          </Stack>
          <Group gap="xs">
            <Button variant="default" leftSection={<ArrowUpFromLine size={16} />} onClick={() => setMovement('WITHDRAWAL')}>
              Sangria
            </Button>
            <Button variant="default" leftSection={<ArrowDownToLine size={16} />} onClick={() => setMovement('DEPOSIT')}>
              Suprimento
            </Button>
            <PrintReportButton sessionId={session.id} label="Imprimir parcial" />
            <Button color="red" leftSection={<Lock size={16} />} onClick={() => setClosing(true)}>
              Fechar caixa
            </Button>
          </Group>
        </Group>
        <LinesTable session={session} />
        <Movements session={session} />
      </Stack>
      <Modal
        opened={movement !== null}
        onClose={() => setMovement(null)}
        title={movement ? MOVEMENT_LABELS[movement] : ''}
      >
        {movement && <MovementForm sessionId={session.id} type={movement} onClose={() => setMovement(null)} />}
      </Modal>
      <Modal opened={closing} onClose={() => setClosing(false)} title="Fechar caixa" size="lg">
        {closing && <CloseForm session={session} onClose={() => setClosing(false)} />}
      </Modal>
    </Card>
  );
}

function LinesTable({ session }: { session: CashSession }) {
  const closed = session.status === 'CLOSED';
  return (
    <Table verticalSpacing="xs" aria-label="Conferência por forma de pagamento">
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Forma</Table.Th>
          <Table.Th ta="right">Pagamentos</Table.Th>
          <Table.Th ta="right">Recebido</Table.Th>
          <Table.Th ta="right">Esperado</Table.Th>
          {closed && <Table.Th ta="right">Contado</Table.Th>}
          {closed && <Table.Th ta="right">Diferença</Table.Th>}
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {session.lines.map((line) => (
          <Table.Tr key={line.paymentMethodId}>
            <Table.Td fw={500}>{line.name}</Table.Td>
            <Table.Td ta="right">{line.payments}</Table.Td>
            <Table.Td ta="right">{formatCents(line.paymentsCents)}</Table.Td>
            <Table.Td ta="right">{formatCents(line.expectedCents)}</Table.Td>
            {closed && <Table.Td ta="right">{formatCents(line.countedCents ?? 0)}</Table.Td>}
            {closed && (
              <Table.Td ta="right">
                <Difference cents={line.differenceCents ?? 0} />
              </Table.Td>
            )}
          </Table.Tr>
        ))}
      </Table.Tbody>
      <Table.Tfoot>
        <Table.Tr>
          <Table.Th colSpan={3}>Total</Table.Th>
          <Table.Th ta="right">{formatCents(session.expectedCents)}</Table.Th>
          {closed && <Table.Th ta="right">{formatCents(session.countedCents ?? 0)}</Table.Th>}
          {closed && (
            <Table.Th ta="right">
              <Difference cents={session.differenceCents ?? 0} />
            </Table.Th>
          )}
        </Table.Tr>
      </Table.Tfoot>
    </Table>
  );
}

function Movements({ session }: { session: CashSession }) {
  if (session.movements.length === 0) {
    return null;
  }
  return (
    <Stack gap={4}>
      <Text fw={600} size="sm">
        Sangrias e suprimentos
      </Text>
      {session.movements.map((movement) => (
        <Group key={movement.id} justify="space-between" wrap="nowrap">
          <Text size="sm">
            {time.format(new Date(movement.createdAt))} · {MOVEMENT_LABELS[movement.type]} · {movement.reason}
            {movement.createdByName ? ` (${movement.createdByName})` : ''}
          </Text>
          <Text size="sm" fw={500} c={movement.type === 'WITHDRAWAL' ? 'red' : 'green'}>
            {movement.type === 'WITHDRAWAL' ? '−' : '+'}
            {formatCents(movement.amountCents)}
          </Text>
        </Group>
      ))}
    </Stack>
  );
}

/** Sobra em verde, falta em vermelho. */
function Difference({ cents }: { cents: number }) {
  return (
    <Text span inherit c={cents === 0 ? undefined : cents > 0 ? 'green' : 'red'}>
      {cents > 0 ? '+' : ''}
      {formatCents(cents)}
    </Text>
  );
}

const movementSchema = z.object({
  amount: moneyField('Informe o valor.').pipe(z.number().min(1, 'O valor deve ser maior que zero.')),
  reason: z.string().trim().min(1, 'Informe o motivo.').max(160, 'Use até 160 caracteres.'),
});

function MovementForm({ sessionId, type, onClose }: { sessionId: string; type: MovementType; onClose: () => void }) {
  const move = useCashMovement(sessionId);
  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<z.input<typeof movementSchema>, unknown, z.output<typeof movementSchema>>({
    resolver: zodResolver(movementSchema),
    defaultValues: { amount: '', reason: '' },
  });

  return (
    <form
      onSubmit={handleSubmit(({ amount, reason }) =>
        move.mutate({ type, amountCents: amount, reason }, { onSuccess: onClose, onError: (error) => applyApiError(error, setError) }),
      )}
      noValidate
    >
      <Stack>
        {errors.root && (
          <Alert color="red" icon={<CircleAlert size={18} />}>
            {errors.root.message}
          </Alert>
        )}
        <Controller
          control={control}
          name="amount"
          render={({ field }) => (
            <MoneyInput label="Valor" value={field.value} onChange={field.onChange} error={errors.amount?.message} data-autofocus />
          )}
        />
        <TextInput
          label="Motivo"
          placeholder={type === 'WITHDRAWAL' ? 'Depósito no banco, pagamento de fornecedor…' : 'Reforço de troco…'}
          {...register('reason')}
          error={errors.reason?.message}
        />
        <Button type="submit" loading={move.isPending}>
          Registrar {MOVEMENT_LABELS[type].toLowerCase()}
        </Button>
      </Stack>
    </form>
  );
}

function CloseForm({ session, onClose }: { session: CashSession; onClose: () => void }) {
  const close = useCloseCash(session.id);
  const [counted, setCounted] = useState<Record<string, number | string>>({});
  const [notes, setNotes] = useState('');

  const cents = (line: CashLine) => {
    const value = counted[line.paymentMethodId];
    const reais = typeof value === 'number' ? value : parseDecimal(value ?? '');
    return Number.isFinite(reais) ? Math.round(reais * 100) : 0;
  };
  const difference = session.lines.reduce((sum, line) => sum + cents(line) - line.expectedCents, 0);

  function submit() {
    close.mutate(
      {
        counts: session.lines.map((line) => ({ paymentMethodId: line.paymentMethodId, countedCents: cents(line) })),
        notes: notes.trim() || undefined,
      },
      { onSuccess: onClose },
    );
  }

  return (
    <Stack>
      <Text size="sm" c="dimmed">
        Conte o dinheiro da gaveta e some os comprovantes de cada forma de pagamento.
      </Text>
      {close.isError && (
        <Alert color="red" icon={<CircleAlert size={18} />}>
          {errorMessage(close.error)}
        </Alert>
      )}
      {session.lines.map((line) => (
        <Group key={line.paymentMethodId} justify="space-between" align="flex-end" wrap="nowrap">
          <MoneyInput
            label={`${line.name} contado`}
            value={counted[line.paymentMethodId] ?? ''}
            onChange={(value) => setCounted((all) => ({ ...all, [line.paymentMethodId]: value }))}
            w={200}
          />
          <Text size="sm" c="dimmed" pb={8}>
            esperado {formatCents(line.expectedCents)}
          </Text>
        </Group>
      ))}
      <Textarea label="Observação" value={notes} onChange={(event) => setNotes(event.currentTarget.value)} maxLength={300} />
      <Group justify="space-between">
        <Text fw={600}>
          Diferença: <Difference cents={difference} />
        </Text>
        <Button color="red" onClick={submit} loading={close.isPending}>
          Confirmar fechamento
        </Button>
      </Group>
    </Stack>
  );
}

function PrintReportButton({ sessionId, label }: { sessionId: string; label: string }) {
  const printers = usePrinters();
  const print = usePrintCashReport(sessionId);
  const active = (printers.data ?? []).filter((printer) => printer.active);

  return (
    <Menu position="bottom-end">
      <Menu.Target>
        <Button variant="default" leftSection={<Printer size={16} />} loading={print.isPending}>
          {label}
        </Button>
      </Menu.Target>
      <Menu.Dropdown>
        {active.length === 0 && <Menu.Label>Nenhuma impressora ativa</Menu.Label>}
        {active.map((printer) => (
          <Menu.Item key={printer.id} onClick={() => print.mutate(printer.id)}>
            {printer.name}
          </Menu.Item>
        ))}
      </Menu.Dropdown>
    </Menu>
  );
}

function SessionDetail({ id }: { id: string }) {
  const session = useCashSession(id);
  if (session.isPending) {
    return <Loader aria-label="Carregando o caixa" />;
  }
  if (session.isError) {
    return (
      <Alert color="red" icon={<CircleAlert size={18} />}>
        {errorMessage(session.error)}
      </Alert>
    );
  }
  const data = session.data;
  return (
    <Stack>
      <Text size="sm">
        Aberto em {dateTime.format(new Date(data.openedAt))}
        {data.openedByName ? ` por ${data.openedByName}` : ''} · troco inicial {formatCents(data.openingAmountCents)}
        {data.closedAt && ` · fechado em ${dateTime.format(new Date(data.closedAt))}`}
        {data.closedByName ? ` por ${data.closedByName}` : ''}
      </Text>
      <LinesTable session={data} />
      <Movements session={data} />
      {data.notes && <Text size="sm">Observação: {data.notes}</Text>}
      <Group justify="flex-end">
        <PrintReportButton sessionId={data.id} label="Imprimir relatório" />
      </Group>
    </Stack>
  );
}

function CashHistory({ onView }: { onView: (id: string) => void }) {
  const [page, setPage] = useState(1);
  const history = useCashHistory(page - 1);
  const closed = (history.data?.content ?? []).filter((session) => session.status === 'CLOSED');

  return (
    <Stack gap="xs">
      <Title order={4}>Caixas anteriores</Title>
      <Card withBorder radius="lg" padding={0}>
        {history.isPending && <Loader m="md" aria-label="Carregando caixas anteriores" />}
        {history.isError && (
          <Alert m="md" color="red" icon={<CircleAlert size={18} />}>
            {errorMessage(history.error)}
          </Alert>
        )}
        {history.data && closed.length === 0 && (
          <Text m="md" c="dimmed">
            Nenhum caixa fechado ainda.
          </Text>
        )}
        {closed.length > 0 && (
          <Table verticalSpacing="sm" highlightOnHover aria-label="Caixas anteriores">
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Abertura</Table.Th>
                <Table.Th>Fechamento</Table.Th>
                <Table.Th ta="right">Esperado</Table.Th>
                <Table.Th ta="right">Contado</Table.Th>
                <Table.Th ta="right">Diferença</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {closed.map((session) => (
                <Table.Tr key={session.id}>
                  <Table.Td>{dateTime.format(new Date(session.openedAt))}</Table.Td>
                  <Table.Td>{session.closedAt ? dateTime.format(new Date(session.closedAt)) : ''}</Table.Td>
                  <Table.Td ta="right">{formatCents(session.expectedCents ?? 0)}</Table.Td>
                  <Table.Td ta="right">{formatCents(session.countedCents ?? 0)}</Table.Td>
                  <Table.Td ta="right">
                    <Difference cents={session.differenceCents ?? 0} />
                  </Table.Td>
                  <Table.Td ta="right">
                    <Button variant="subtle" size="xs" onClick={() => onView(session.id)}>
                      Ver
                    </Button>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Card>
      {history.data && history.data.totalPages > 1 && (
        <Pagination value={page} onChange={setPage} total={history.data.totalPages} />
      )}
    </Stack>
  );
}
