import { Alert, Badge, Button, Card, Group, Loader, Menu, Pagination, Stack, Table, Text, Title } from '@mantine/core';
import { CircleAlert, RotateCcw } from 'lucide-react';
import { useState } from 'react';
import { errorMessage } from '../../shared/api/errors';
import type { PrintJob, Printer } from '../../shared/api/types';
import { useSession } from '../auth/auth-context';
import {
  JOB_STATUS_COLORS,
  JOB_STATUS_LABELS,
  NEEDS_ATTENTION,
  usePrinters,
  useRecentPrintJobs,
  useRetryPrintJob,
} from './api';

const time = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' });

/** Impressões das últimas 24 h. O que falhou, ficou incerto ou expirou vem primeiro, com "imprimir de novo". */
export function PrintJobsPage() {
  const [page, setPage] = useState(0);
  const jobs = useRecentPrintJobs(page);
  const printers = usePrinters();
  const printerName = (id: string) => printers.data?.find((printer) => printer.id === id)?.name ?? '—';
  const sorted = [...(jobs.data?.content ?? [])].sort(
    (a, b) => Number(NEEDS_ATTENTION.includes(b.status)) - Number(NEEDS_ATTENTION.includes(a.status)),
  );

  return (
    <Stack maw={960} gap="lg">
      <Stack gap={4}>
        <Title order={2}>Impressões</Title>
        <Text c="dimmed">
          Últimas 24 horas, das mais novas para as mais antigas. O que falhou, ficou incerto ou expirou não sai
          sozinho: decida se imprime de novo.
        </Text>
      </Stack>
      <Card withBorder radius="lg" padding={0}>
        {jobs.isPending && <Loader m="md" aria-label="Carregando impressões" />}
        {jobs.isError && (
          <Alert m="md" color="red" icon={<CircleAlert size={18} />}>
            {errorMessage(jobs.error)}
          </Alert>
        )}
        {jobs.data?.totalElements === 0 && (
          <Text c="dimmed" p="md">
            Nenhuma impressão nas últimas 24 horas.
          </Text>
        )}
        {sorted.length > 0 && (
          <Table verticalSpacing="sm">
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Impressão</Table.Th>
                <Table.Th>Impressora</Table.Th>
                <Table.Th>Situação</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {sorted.map((job) => (
                <Table.Tr key={job.id} aria-label={job.title}>
                  <Table.Td>
                    <Text fw={500}>{job.title}</Text>
                    <Text size="xs" c="dimmed">
                      {time.format(new Date(job.createdAt))}
                    </Text>
                  </Table.Td>
                  <Table.Td>{printerName(job.printerId)}</Table.Td>
                  <Table.Td>
                    <Badge color={JOB_STATUS_COLORS[job.status]} variant="light">
                      {JOB_STATUS_LABELS[job.status]}
                    </Badge>
                    {job.lastError && NEEDS_ATTENTION.includes(job.status) && (
                      <Text size="xs" c="dimmed">
                        {job.lastError}
                      </Text>
                    )}
                  </Table.Td>
                  <Table.Td ta="right">
                    {NEEDS_ATTENTION.includes(job.status) && (
                      <RetryButton job={job} printers={printers.data ?? []} />
                    )}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
        {jobs.data && jobs.data.totalPages > 1 && (
          <Group justify="center" p="md">
            <Pagination total={jobs.data.totalPages} value={page + 1} onChange={(value) => setPage(value - 1)} />
          </Group>
        )}
      </Card>
    </Stack>
  );
}

/** Na mesma impressora, ou escolhendo outra (quando a de origem está fora do ar). */
export function RetryButton({ job, printers }: { job: PrintJob; printers: Printer[] }) {
  const { user } = useSession();
  const retry = useRetryPrintJob();
  const others = printers.filter((printer) => printer.active && printer.id !== job.printerId);
  if (user.role === 'KITCHEN' && job.documentType === 'ORDER_TICKET') {
    return null;
  }
  return (
    <Group gap={4} justify="flex-end" wrap="nowrap">
      <Button
        size="xs"
        leftSection={<RotateCcw size={14} />}
        loading={retry.isPending}
        onClick={() => retry.mutate({ id: job.id })}
      >
        Imprimir de novo
      </Button>
      {others.length > 0 && (
        <Menu position="bottom-end">
          <Menu.Target>
            <Button size="xs" variant="default" disabled={retry.isPending}>
              Em outra
            </Button>
          </Menu.Target>
          <Menu.Dropdown>
            {others.map((printer) => (
              <Menu.Item key={printer.id} onClick={() => retry.mutate({ id: job.id, printerId: printer.id })}>
                {printer.name}
              </Menu.Item>
            ))}
          </Menu.Dropdown>
        </Menu>
      )}
    </Group>
  );
}
