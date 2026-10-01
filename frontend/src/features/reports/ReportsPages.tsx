import { Alert, Box, Card, Group, Loader, SimpleGrid, Stack, Table, Text, TextInput, Title, Tooltip } from '@mantine/core';
import { CircleAlert } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { errorMessage } from '../../shared/api/errors';
import type { RevenueGroup, RevenueSummary } from '../../shared/api/types';
import { formatCents } from '../../shared/lib/numbers';
import { PAYMENT_TYPE_LABELS, SOURCE_LABELS, TYPE_LABELS } from '../orders/labels';
import { useDashboard, useRevenue } from './api';

const day = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeZone: 'UTC' });

/** Painel do dia: pedidos, faturamento, preparo, pedidos por hora e o que mais saiu. */
export function DashboardPage() {
  const [date, setDate] = useState<string | null>(null);
  const dashboard = useDashboard(date);
  const data = dashboard.data;

  return (
    <Stack maw={1100} gap="lg">
      <Group justify="space-between" align="flex-end">
        <Stack gap={4}>
          <Title order={2}>Painel do dia</Title>
          <Text c="dimmed">Pedidos cancelados ficam fora dos números, menos da contagem de cancelados.</Text>
        </Stack>
        <TextInput
          type="date"
          label="Dia"
          value={date ?? data?.date ?? ''}
          onChange={(event) => setDate(event.currentTarget.value || null)}
        />
      </Group>
      <QueryState query={dashboard} label="Carregando o painel" />
      {data && (
        <>
          <SummaryCards summary={data.summary}>
            <Stat
              label="Tempo médio de preparo"
              value={data.averagePreparationSeconds === null ? '—' : `${Math.round(data.averagePreparationSeconds / 60)} min`}
            />
          </SummaryCards>
          <Card withBorder radius="lg">
            <Text fw={600} mb="sm">
              Pedidos por hora
            </Text>
            <HourBars counts={data.ordersByHour} />
          </Card>
          <SimpleGrid cols={{ base: 1, md: 2 }}>
            <Card withBorder radius="lg" padding={0}>
              <Text fw={600} m="md" mb={0}>
                Mais vendidos
              </Text>
              {data.topProducts.length === 0 ? (
                <Text m="md" c="dimmed">
                  Nenhum item vendido neste dia.
                </Text>
              ) : (
                <Table verticalSpacing="xs" aria-label="Mais vendidos">
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th>Produto</Table.Th>
                      <Table.Th ta="right">Qtd</Table.Th>
                      <Table.Th ta="right">Total</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {data.topProducts.map((product) => (
                      <Table.Tr key={product.name}>
                        <Table.Td>{product.name}</Table.Td>
                        <Table.Td ta="right">{product.quantity}</Table.Td>
                        <Table.Td ta="right">{formatCents(product.totalCents)}</Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              )}
            </Card>
            <GroupTable title="Por canal" groups={data.bySource} labels={SOURCE_LABELS} />
          </SimpleGrid>
        </>
      )}
    </Stack>
  );
}

/** Faturamento por período, canal, tipo e forma de pagamento. */
export function RevenuePage() {
  const [from, setFrom] = useState(firstOfMonth);
  const [to, setTo] = useState(today);
  const revenue = useRevenue(from, to);
  const data = revenue.data;

  return (
    <Stack maw={1100} gap="lg">
      <Group justify="space-between" align="flex-end">
        <Stack gap={4}>
          <Title order={2}>Faturamento</Title>
          <Text c="dimmed">Por dia operacional. Taxa de entrega e de serviço já estão no faturamento.</Text>
        </Stack>
        <Group>
          <TextInput type="date" label="De" value={from} onChange={(event) => setFrom(event.currentTarget.value)} />
          <TextInput
            type="date"
            label="Até"
            value={to}
            onChange={(event) => setTo(event.currentTarget.value)}
            error={from > to ? 'Antes da data inicial.' : undefined}
          />
        </Group>
      </Group>
      <QueryState query={revenue} label="Carregando o faturamento" />
      {data && (
        <>
          <SummaryCards summary={data.summary} />
          <Card withBorder radius="lg" padding={0}>
            <Table verticalSpacing="xs" aria-label="Composição do faturamento">
              <Table.Tbody>
                <Row label="Itens (subtotal)" cents={data.summary.subtotalCents} />
                <Row label="Descontos" cents={-data.summary.discountCents} />
                <Row label="Taxa de entrega" cents={data.summary.deliveryFeeCents} />
                <Row label="Taxa de serviço / adicional" cents={data.summary.additionalFeeCents} />
                <Row label="Faturamento" cents={data.summary.grossCents} bold />
                <Row label="Subsídio de plataforma (não entra no total)" cents={data.summary.platformSubsidyCents} />
              </Table.Tbody>
            </Table>
          </Card>
          <SimpleGrid cols={{ base: 1, md: 2 }}>
            <GroupTable
              title="Por dia"
              groups={data.byDay}
              labels={Object.fromEntries(data.byDay.map((group) => [group.key, day.format(new Date(group.key))]))}
            />
            <Stack>
              <GroupTable title="Por canal" groups={data.bySource} labels={SOURCE_LABELS} />
              <GroupTable title="Por tipo" groups={data.byType} labels={TYPE_LABELS} />
            </Stack>
          </SimpleGrid>
          <Card withBorder radius="lg" padding={0}>
            <Text fw={600} m="md" mb={0}>
              Por forma de pagamento
            </Text>
            <Table verticalSpacing="xs" aria-label="Por forma de pagamento">
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Forma</Table.Th>
                  <Table.Th ta="right">Pagamentos</Table.Th>
                  <Table.Th ta="right">Total</Table.Th>
                  <Table.Th ta="right">A receber</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {data.byPaymentMethod.map((method) => (
                  <Table.Tr key={method.paymentMethodId}>
                    <Table.Td>
                      {method.name}
                      <Text span size="xs" c="dimmed">
                        {' '}
                        {PAYMENT_TYPE_LABELS[method.type as keyof typeof PAYMENT_TYPE_LABELS] ?? method.type}
                      </Text>
                    </Table.Td>
                    <Table.Td ta="right">{method.payments}</Table.Td>
                    <Table.Td ta="right">{formatCents(method.totalCents)}</Table.Td>
                    <Table.Td ta="right">{method.pendingCents > 0 ? formatCents(method.pendingCents) : '—'}</Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Card>
        </>
      )}
    </Stack>
  );
}

function QueryState({ query, label }: { query: { isPending: boolean; isError: boolean; error: unknown; fetchStatus: string }; label: string }) {
  if (query.isError) {
    return (
      <Alert color="red" icon={<CircleAlert size={18} />}>
        {errorMessage(query.error)}
      </Alert>
    );
  }
  // Consulta desligada (datas inválidas) fica "pending" sem buscar: nada de carregando infinito.
  return query.isPending && query.fetchStatus === 'fetching' ? <Loader aria-label={label} /> : null;
}

function SummaryCards({ summary, children }: { summary: RevenueSummary; children?: ReactNode }) {
  return (
    <SimpleGrid cols={{ base: 2, md: children ? 5 : 4 }}>
      <Stat label="Pedidos" value={String(summary.orders)} />
      <Stat label="Faturamento" value={formatCents(summary.grossCents)} />
      <Stat label="Ticket médio" value={formatCents(summary.averageTicketCents)} />
      {children}
      <Stat
        label="Cancelados"
        value={String(summary.cancelledOrders)}
        hint={summary.cancelledOrders > 0 ? formatCents(summary.cancelledCents) : undefined}
      />
    </SimpleGrid>
  );
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Card withBorder radius="lg" aria-label={label}>
      <Text size="sm" c="dimmed">
        {label}
      </Text>
      <Text fw={700} fz="xl">
        {value}
      </Text>
      {hint && (
        <Text size="xs" c="dimmed">
          {hint}
        </Text>
      )}
    </Card>
  );
}

function Row({ label, cents, bold }: { label: string; cents: number; bold?: boolean }) {
  return (
    <Table.Tr>
      <Table.Td fw={bold ? 700 : undefined}>{label}</Table.Td>
      <Table.Td ta="right" fw={bold ? 700 : undefined}>
        {formatCents(cents)}
      </Table.Td>
    </Table.Tr>
  );
}

function GroupTable({ title, groups, labels }: { title: string; groups: RevenueGroup[]; labels: Record<string, string> }) {
  return (
    <Card withBorder radius="lg" padding={0}>
      <Text fw={600} m="md" mb={0}>
        {title}
      </Text>
      {groups.length === 0 ? (
        <Text m="md" c="dimmed">
          Nenhum pedido.
        </Text>
      ) : (
        <Table verticalSpacing="xs" aria-label={title}>
          <Table.Tbody>
            {groups.map((group) => (
              <Table.Tr key={group.key}>
                <Table.Td>{labels[group.key] ?? group.key}</Table.Td>
                <Table.Td ta="right">
                  {group.orders} {group.orders === 1 ? 'pedido' : 'pedidos'}
                </Table.Td>
                <Table.Td ta="right">{formatCents(group.totalCents)}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </Card>
  );
}

/** Barras em CSS: 24 horas não pedem uma biblioteca de gráficos. */
function HourBars({ counts }: { counts: number[] }) {
  const max = Math.max(1, ...counts);
  return (
    <Group gap={2} align="flex-end" h={120} wrap="nowrap" aria-label="Pedidos por hora">
      {counts.map((count, hour) => (
        <Tooltip key={hour} label={`${hour}h: ${count} ${count === 1 ? 'pedido' : 'pedidos'}`}>
          <Stack gap={2} align="center" style={{ flex: 1 }}>
            <Box
              w="100%"
              h={Math.round((count / max) * 90)}
              mih={count > 0 ? 3 : 0}
              bg="orange.5"
              style={{ borderRadius: 2 }}
            />
            <Text size="10px" c="dimmed">
              {hour}
            </Text>
          </Stack>
        </Tooltip>
      ))}
    </Group>
  );
}

function today(): string {
  return localDate(new Date());
}

function firstOfMonth(): string {
  const now = new Date();
  return localDate(new Date(now.getFullYear(), now.getMonth(), 1));
}

function localDate(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
