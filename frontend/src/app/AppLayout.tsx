import { AppShell, Badge, Burger, Button, Group, NavLink, Stack, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  Armchair,
  Bike,
  BookOpen,
  ChartColumn,
  ChefHat,
  ClipboardList,
  CreditCard,
  House,
  LogOut,
  type LucideIcon,
  Store,
  Users,
} from 'lucide-react';
import { NavLink as RouterNavLink, Outlet, useMatch } from 'react-router';
import { useAuth, useSession } from '../features/auth/auth-context';
import { useOrderStream } from '../features/orders/realtime';
import { AVAILABILITY_TOGGLERS, ORDER_VIEWERS, ROLE_LABELS, SETTINGS_MANAGERS } from '../shared/lib/roles';

/** Áreas que chegam nas próximas etapas do roadmap (docs/06-roadmap.md). */
const UPCOMING: { label: string; icon: LucideIcon }[] = [
  { label: 'Salão', icon: Armchair },
  { label: 'Relatórios', icon: ChartColumn },
];

export function AppLayout() {
  const [opened, { toggle, close }] = useDisclosure();
  const { logout } = useAuth();
  const { user, store } = useSession();
  const seesOrders = ORDER_VIEWERS.includes(user.role);
  // Uma conexão de tempo real por aba: o quadro, o detalhe e o histórico se atualizam sozinhos.
  useOrderStream(seesOrders);

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
        {seesOrders && <NavItem to="/pedidos" label="Pedidos" icon={ClipboardList} onNavigate={close} />}
        {seesOrders && <NavItem to="/cozinha" label="Cozinha" icon={ChefHat} onNavigate={close} />}
        {AVAILABILITY_TOGGLERS.includes(user.role) && (
          <NavItem to="/cardapio" label="Cardápio" icon={BookOpen} onNavigate={close} />
        )}
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
        {SETTINGS_MANAGERS.includes(user.role) && (
          <>
            <Text size="xs" c="dimmed" tt="uppercase" fw={700} mt="md" mb={4} px="sm">
              Configurações
            </Text>
            {user.role === 'OWNER' && (
              <>
                <NavItem to="/configuracoes/loja" label="Loja" icon={Store} onNavigate={close} />
                <NavItem to="/configuracoes/equipe" label="Equipe" icon={Users} onNavigate={close} />
              </>
            )}
            <NavItem to="/configuracoes/pagamentos" label="Pagamentos" icon={CreditCard} onNavigate={close} />
            <NavItem to="/configuracoes/taxas" label="Taxas de entrega" icon={Bike} onNavigate={close} />
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
