import { Alert, Badge, Card, Group, Loader, Pagination, Select, SimpleGrid, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { CircleAlert, Search } from 'lucide-react';
import { useState } from 'react';
import { errorMessage } from '../../shared/api/errors';
import type { OrderStatus, OrderType } from '../../shared/api/types';
import { formatCents } from '../../shared/lib/numbers';
import { type OrderFilters, useOrdersPage } from './api';
import { STATUS_COLORS, STATUS_LABELS, TYPE_LABELS } from './labels';
import { OrderDetailDrawer } from './OrderDetailDrawer';

const date = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeZone: 'UTC' });
const time = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' });

const STATUS_OPTIONS = (Object.keys(STATUS_LABELS) as OrderStatus[]).map((value) => ({ value, label: STATUS_LABELS[value] }));
const TYPE_OPTIONS = (['TAKEOUT', 'DELIVERY'] as OrderType[]).map((value) => ({ value, label: TYPE_LABELS[value] }));

/** Histórico de pedidos, do mais novo para o mais antigo, com filtros. */
export function OrdersHistoryPage() {
  const [filters, setFilters] = useState<Omit<OrderFilters, 'q'>>({ businessDate: null, status: null, type: null, page: 0 });
  const [search, setSearch] = useState('');
  // A busca vai para a API quando a pessoa para de digitar, não a cada tecla.
  const [q] = useDebouncedValue(search.trim(), 300);
  const [openId, setOpenId] = useState<string | null>(null);
  const page = useOrdersPage({ ...filters, q });

  function change(partial: Partial<Omit<OrderFilters, 'q'>>) {
    setFilters((current) => ({ ...current, ...partial, page: 0 }));
  }

  return (
    <Stack gap="md" maw={1100}>
      <Stack gap={4}>
        <Title order={2}>Histórico de pedidos</Title>
        <Text c="dimmed">Todos os pedidos, inclusive os concluídos e os cancelados.</Text>
      </Stack>
      <SimpleGrid cols={{ base: 1, sm: 2, md: 4 }} spacing="sm">
        <TextInput
          aria-label="Buscar pedido"
          placeholder="Número, nome ou telefone"
          leftSection={<Search size={16} />}
          value={search}
          onChange={(event) => {
            setSearch(event.currentTarget.value);
            change({});
          }}
        />
        <TextInput
          aria-label="Dia"
          type="date"
          value={filters.businessDate ?? ''}
          onChange={(event) => change({ businessDate: event.currentTarget.value || null })}
        />
        <Select
          aria-label="Status"
          placeholder="Todos os status"
          data={STATUS_OPTIONS}
          value={filters.status}
          onChange={(value) => change({ status: value as OrderStatus | null })}
          clearable
        />
        <Select
          aria-label="Tipo"
          placeholder="Todos os tipos"
          data={TYPE_OPTIONS}
          value={filters.type}
          onChange={(value) => change({ type: value as OrderType | null })}
          clearable
        />
      </SimpleGrid>

      {page.isPending && <Loader aria-label="Carregando histórico" />}
      {page.isError && (
        <Alert color="red" icon={<CircleAlert size={18} />}>
          {errorMessage(page.error)}
        </Alert>
      )}
      {page.data && (
        <Card withBorder radius="lg" padding={0}>
          <Table.ScrollContainer minWidth={640}>
            <Table verticalSpacing="sm" highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Pedido</Table.Th>
                  <Table.Th>Dia</Table.Th>
                  <Table.Th>Cliente</Table.Th>
                  <Table.Th>Tipo</Table.Th>
                  <Table.Th>Status</Table.Th>
                  <Table.Th ta="right">Total</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {page.data.content.length === 0 && (
                  <Table.Tr>
                    <Table.Td colSpan={6}>
                      <Text c="dimmed">Nenhum pedido encontrado.</Text>
                    </Table.Td>
                  </Table.Tr>
                )}
                {page.data.content.map((order) => (
                  <Table.Tr key={order.id} style={{ cursor: 'pointer' }} onClick={() => setOpenId(order.id)}>
                    <Table.Td fw={600}>#{order.number}</Table.Td>
                    <Table.Td>
                      {date.format(new Date(`${order.businessDate}T00:00:00Z`))}{' '}
                      <Text span size="xs" c="dimmed">
                        {time.format(new Date(order.createdAt))}
                      </Text>
                    </Table.Td>
                    <Table.Td>{order.customerName ?? '—'}</Table.Td>
                    <Table.Td>{TYPE_LABELS[order.type]}</Table.Td>
                    <Table.Td>
                      <Badge size="sm" variant="light" color={STATUS_COLORS[order.status]}>
                        {STATUS_LABELS[order.status]}
                      </Badge>
                    </Table.Td>
                    <Table.Td ta="right">{formatCents(order.totalCents)}</Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        </Card>
      )}
      {page.data && page.data.totalPages > 1 && (
        <Group justify="center">
          <Pagination
            total={page.data.totalPages}
            value={filters.page + 1}
            onChange={(value) => setFilters((current) => ({ ...current, page: value - 1 }))}
          />
        </Group>
      )}

      <OrderDetailDrawer orderId={openId} onClose={() => setOpenId(null)} />
    </Stack>
  );
}
