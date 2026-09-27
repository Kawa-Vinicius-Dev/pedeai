import { Badge, Button, Group, Stack, Text, Title } from '@mantine/core';
import { Printer as PrinterIcon } from 'lucide-react';
import type { Order } from '../../shared/api/types';
import { JOB_STATUS_COLORS, JOB_STATUS_LABELS, NEEDS_ATTENTION, useOrderPrintJobs, usePrinters, useReprint } from './api';
import { RetryButton } from './PrintJobsPage';

/** No detalhe do pedido: onde cada ticket foi impresso, e reimprimir na mesma impressora com a faixa REIMPRESSÃO. */
export function OrderPrintJobs({ order }: { order: Order }) {
  const jobs = useOrderPrintJobs(order.id);
  const printers = usePrinters();
  const reprint = useReprint(order.id);
  if (!jobs.data || jobs.data.length === 0) {
    return null;
  }
  const printerName = (id: string) => printers.data?.find((printer) => printer.id === id)?.name ?? 'impressora';

  return (
    <Stack gap="xs" aria-label="Impressões do pedido">
      <Title order={5}>Impressões</Title>
      {jobs.data.map((job) => (
        <Group key={job.id} justify="space-between" wrap="nowrap" gap="xs">
          <Stack gap={0}>
            <Text size="sm" fw={500}>
              {job.title}
            </Text>
            <Text size="xs" c="dimmed">
              {printerName(job.printerId)}
              {job.lastError && NEEDS_ATTENTION.includes(job.status) ? ` · ${job.lastError}` : ''}
            </Text>
          </Stack>
          <Group gap={6} wrap="nowrap">
            <Badge color={JOB_STATUS_COLORS[job.status]} variant="light">
              {JOB_STATUS_LABELS[job.status]}
            </Badge>
            {NEEDS_ATTENTION.includes(job.status) ? (
              <RetryButton job={job} printers={printers.data ?? []} />
            ) : (
              job.status === 'PRINTED' && (
                <Button
                  size="xs"
                  variant="subtle"
                  leftSection={<PrinterIcon size={14} />}
                  loading={reprint.isPending && reprint.variables?.printerId === job.printerId}
                  onClick={() =>
                    reprint.mutate({
                      documentType: job.documentType,
                      sectorId: job.sectorId ?? undefined,
                      printerId: job.printerId,
                    })
                  }
                  aria-label={`Reimprimir ${job.title}`}
                >
                  Reimprimir
                </Button>
              )
            )}
          </Group>
        </Group>
      ))}
    </Stack>
  );
}
