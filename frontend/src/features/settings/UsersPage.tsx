import { Alert, Badge, Button, Card, Group, Loader, Stack, Table, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { CircleAlert, Pencil, UserPlus } from 'lucide-react';
import { useState } from 'react';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { User } from '../../shared/api/types';
import { ROLE_LABELS } from '../../shared/lib/roles';
import { useSession } from '../auth/auth-context';
import { CreateUserModal, EditUserModal } from './UserModals';

export function UsersPage() {
  const { user: currentUser } = useSession();
  const usersQuery = useQuery({ queryKey: ['users'], queryFn: () => unwrap(api.GET('/api/users')) });
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<User | null>(null);

  return (
    <Stack maw={960} gap="lg">
      <Group justify="space-between" align="flex-end">
        <Stack gap={4}>
          <Title order={2}>Equipe</Title>
          <Text c="dimmed">Quem acessa o PedeAí e o que cada pessoa pode fazer.</Text>
        </Stack>
        <Button leftSection={<UserPlus size={18} />} onClick={() => setCreating(true)}>
          Adicionar pessoa
        </Button>
      </Group>

      <Card withBorder radius="lg" padding={0}>
        {usersQuery.isPending && (
          <Group p="lg">
            <Loader size="sm" aria-label="Carregando equipe" />
          </Group>
        )}
        {usersQuery.isError && (
          <Alert m="md" color="red" icon={<CircleAlert size={18} />}>
            {errorMessage(usersQuery.error)}
          </Alert>
        )}
        {usersQuery.data && (
          <Table.ScrollContainer minWidth={560}>
            <Table verticalSpacing="sm" highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Nome</Table.Th>
                  <Table.Th>E-mail</Table.Th>
                  <Table.Th>Papel</Table.Th>
                  <Table.Th>Situação</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {usersQuery.data.map((user) => (
                  <Table.Tr key={user.id}>
                    <Table.Td>
                      {user.name}
                      {user.id === currentUser.id && (
                        <Text span c="dimmed" size="sm">
                          {' '}
                          (você)
                        </Text>
                      )}
                    </Table.Td>
                    <Table.Td>{user.email}</Table.Td>
                    <Table.Td>{ROLE_LABELS[user.role]}</Table.Td>
                    <Table.Td>
                      <Badge variant="light" color={user.active ? 'green' : 'gray'}>
                        {user.active ? 'Ativo' : 'Inativo'}
                      </Badge>
                    </Table.Td>
                    <Table.Td ta="right">
                      <Button
                        variant="subtle"
                        size="xs"
                        leftSection={<Pencil size={14} />}
                        onClick={() => setEditing(user)}
                        aria-label={`Editar ${user.name}`}
                      >
                        Editar
                      </Button>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </Card>

      <CreateUserModal opened={creating} onClose={() => setCreating(false)} />
      <EditUserModal user={editing} onClose={() => setEditing(null)} />
    </Stack>
  );
}
