import createClient from 'openapi-fetch';
import type { paths } from './schema';
import type { AuthResponse } from './types';

/** Em desenvolvimento a API responde na mesma origem, pelo proxy do Vite. */
const baseUrl = import.meta.env.VITE_API_URL || window.location.origin;

/** O token de acesso vive só na memória da aba. O refresh token fica num cookie HttpOnly. */
let accessToken: string | null = null;
let refreshInFlight: Promise<AuthResponse | null> | null = null;
const sessionExpiredListeners = new Set<() => void>();

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

/** Avisa quando a sessão acabou de vez (a renovação falhou), para a tela voltar ao login. */
export function onSessionExpired(listener: () => void): () => void {
  sessionExpiredListeners.add(listener);
  return () => {
    sessionExpiredListeners.delete(listener);
  };
}

/** Renova a sessão pelo cookie. Chamadas simultâneas compartilham a mesma requisição. */
export function refreshSession(): Promise<AuthResponse | null> {
  refreshInFlight ??= requestRefresh().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

async function requestRefresh(): Promise<AuthResponse | null> {
  try {
    const response = await fetch(`${baseUrl}/api/auth/refresh`, { method: 'POST', credentials: 'include' });
    if (!response.ok) {
      accessToken = null;
      return null;
    }
    const session = (await response.json()) as AuthResponse;
    accessToken = session.accessToken;
    return session;
  } catch {
    return null;
  }
}

/**
 * Anexa o token de acesso. Se a API responder 401 (token vencido), renova a sessão uma vez e repete
 * a requisição. Login, renovação e logout não levam token.
 */
async function authFetch(request: Request): Promise<Response> {
  if (isAuthEndpoint(request)) {
    return fetch(request);
  }
  const retry = request.clone();
  const response = await fetch(withAccessToken(request));
  if (response.status !== 401) {
    return response;
  }
  const session = await refreshSession();
  if (!session) {
    sessionExpiredListeners.forEach((listener) => listener());
    return response;
  }
  return fetch(withAccessToken(retry));
}

function withAccessToken(request: Request): Request {
  if (accessToken) {
    request.headers.set('Authorization', `Bearer ${accessToken}`);
  }
  return request;
}

function isAuthEndpoint(request: Request): boolean {
  return new URL(request.url).pathname.startsWith('/api/auth/');
}

export const api = createClient<paths>({ baseUrl, credentials: 'include', fetch: authFetch });
