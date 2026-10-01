import { Alert, List, SimpleGrid, Stack, Text } from '@mantine/core';
import { CircleAlert, CircleCheck } from 'lucide-react';
import type { CatalogImport } from '../../shared/api/types';

/** O que a importação fez (ou faria, na pré-visualização), e os problemas linha a linha. */
export function ImportResult({ result, preview }: { result: CatalogImport; preview: boolean }) {
  const hasErrors = result.errors.length > 0;
  return (
    <Stack gap="sm">
      {hasErrors ? (
        <Alert color="red" icon={<CircleAlert size={18} />} title="Nada foi importado">
          Corrija os pontos abaixo e tente de novo. A importação é tudo ou nada.
        </Alert>
      ) : (
        <Alert color={preview ? 'blue' : 'green'} icon={<CircleCheck size={18} />}>
          {preview ? 'Pré-visualização: nada foi gravado ainda.' : 'Cardápio importado.'}
        </Alert>
      )}
      <SimpleGrid cols={{ base: 2, sm: 4 }}>
        <Count label="Categorias novas" value={result.categoriesCreated} />
        <Count label="Produtos novos" value={result.productsCreated} />
        <Count label="Produtos atualizados" value={result.productsUpdated} />
        <Count label="Grupos de adicionais novos" value={result.optionGroupsCreated} />
      </SimpleGrid>
      {hasErrors && (
        <List size="sm" spacing={4} aria-label="Problemas da importação">
          {result.errors.slice(0, 50).map((issue, index) => (
            <List.Item key={index}>
              <Text span fw={600} size="sm">
                {issue.origin}:
              </Text>{' '}
              {issue.message}
            </List.Item>
          ))}
        </List>
      )}
    </Stack>
  );
}

function Count({ label, value }: { label: string; value: number }) {
  return (
    <Stack gap={0} aria-label={label}>
      <Text fw={700} fz="xl">
        {value}
      </Text>
      <Text size="xs" c="dimmed">
        {label}
      </Text>
    </Stack>
  );
}
