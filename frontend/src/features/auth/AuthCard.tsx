import { Center, Paper, Stack, Text, Title } from '@mantine/core';
import type { ReactNode } from 'react';

/** Moldura das telas de login e cadastro. */
export function AuthCard({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <Center mih="100vh" p="md" bg="gray.0">
      <Paper withBorder shadow="sm" radius="lg" p="xl" w="100%" maw={420}>
        <Stack gap="lg">
          <Stack gap={4}>
            <Text fw={900} fz={28} c="orange.7">
              PedeAí
            </Text>
            <Title order={2} fz="xl">
              {title}
            </Title>
            <Text c="dimmed" size="sm">
              {subtitle}
            </Text>
          </Stack>
          {children}
        </Stack>
      </Paper>
    </Center>
  );
}
