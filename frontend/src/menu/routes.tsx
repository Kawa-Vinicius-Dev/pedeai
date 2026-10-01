import type { RouteObject } from 'react-router';
import { StorePage } from './StorePage';
import { TrackingPage } from './TrackingPage';

/**
 * O cardápio digital é uma página separada do painel da loja (menu.html): sem login e sem o código do painel, para
 * abrir rápido no celular do cliente.
 */
export const menuRoutes: RouteObject[] = [
  { path: '/loja/:slug', element: <StorePage /> },
  { path: '/loja/:slug/pedido/:code', element: <TrackingPage /> },
];
