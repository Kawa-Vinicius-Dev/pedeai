import { Alert, Anchor, Stack, Text } from '@mantine/core';
import { Printer } from 'lucide-react';
import { Link, useMatch } from 'react-router';
import { usePrintAlerts } from './api';

/** Faixa no topo de todas as telas da loja quando a impressão tem problema (docs/04-impressao.md#como-reagir). */
export function PrintAlertsBar({ enabled }: { enabled: boolean }) {
  const alerts = usePrintAlerts(enabled);
  const onPanel = useMatch('/impressoes');
  if (!alerts.data || alerts.data.length === 0) {
    return null;
  }
  return (
    <Alert color="red" variant="light" icon={<Printer size={18} />} mb="md" role="alert" aria-label="Alertas de impressão">
      <Stack gap={2}>
        {alerts.data.map((alert) => (
          <Text key={alert.printerId ?? alert.message} size="sm" fw={500}>
            {alert.message}
          </Text>
        ))}
        {!onPanel && (
          <Anchor component={Link} to="/impressoes" size="sm">
            Ver impressões
          </Anchor>
        )}
      </Stack>
    </Alert>
  );
}
