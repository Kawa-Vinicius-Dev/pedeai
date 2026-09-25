import { AppShell, Badge, Burger, Button, Group, NavLink, Stack, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  Armchair,
  BookOpen,
  ChartColumn,
  ChefHat,
  ClipboardList,
  House,
  LogOut,
  type LucideIcon,
  Store,
  Users,
} from 'lucide-react';
import { NavLink as RouterNavLink, Outlet, useMatch } from 'react-router';
import { useAuth, useSession } from '../features/auth/auth-context';
import { ROLE_LABELS } from '../shared/lib/roles';

/** Áreas que chegam nas próximas etapas do roadmap (docs/06-roadmap.md). */
const UPCOMING: { label: string; icon: LucideIcon }[] = [
  { label: 'Pedidos', icon: ClipboardList },
  { label: 'Cozinha', icon: ChefHat },
  { label: 'Salão', icon: Armchair },
  { label: 'Cardápio', icon: BookOpen },
  { label: 'Relatórios', icon: ChartColumn },
];

export function AppLayout() {
  const [opened, { toggle, close }] = useDisclosure();
  const { logout } = useAuth();
  const { user, store } = useSession();

  return (
    <AppShell
      header={{ height: 60 }}
      navbar={{ width: 240, breakpoint: 'sm', collapsed: { mobile: !opened } }}
      padding="md"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between" wrap="nowrap">
          <Group gap="sm" wrap="nowrap">
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" aria-label="Abrir menu" />
            <Text fw={900} fz="xl" c="orange.7">
              PedeAí
            </Text>
            <Text c="dimmed" truncate visibleFrom="xs">
              {store.name}
            </Text>
          </Group>
          <Group gap="sm" wrap="nowrap">
            <Stack gap={0} align="flex-end" visibleFrom="sm">
              <Text size="sm" fw={600}>
                {user.name}
              </Text>
              <Text size="xs" c="dimmed">
                {ROLE_LABELS[user.role]}
              </Text>
            </Stack>
            <Button variant="subtle" color="gray" leftSection={<LogOut size={16} />} onClick={() => void logout()}>
              Sair
            </Button>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="sm">
        <NavItem to="/" label="Início" icon={House} onNavigate={close} end />
        {UPCOMING.map(({ label, icon: Icon }) => (
          <NavLink
            key={label}
            label={label}
            leftSection={<Icon size={18} />}
            rightSection={
              <Badge size="xs" variant="light" color="gray">
                em breve
              </Badge>
            }
            disabled
          />
        ))}
        {user.role === 'OWNER' && (
          <>
            <Text size="xs" c="dimmed" tt="uppercase" fw={700} mt="md" mb={4} px="sm">
              Configurações
            </Text>
            <NavItem to="/configuracoes/loja" label="Loja" icon={Store} onNavigate={close} />
            <NavItem to="/configuracoes/equipe" label="Equipe" icon={Users} onNavigate={close} />
          </>
        )}
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}

function NavItem({
  to,
  label,
  icon: Icon,
  onNavigate,
  end = false,
}: {
  to: string;
  label: string;
  icon: LucideIcon;
  onNavigate: () => void;
  end?: boolean;
}) {
  const active = useMatch({ path: to, end }) !== null;
  return (
    <NavLink
      component={RouterNavLink}
      to={to}
      end={end}
      label={label}
      leftSection={<Icon size={18} />}
      active={active}
      onClick={onNavigate}
    />
  );
}
