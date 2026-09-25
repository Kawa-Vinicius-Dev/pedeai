import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { apiError, type Menu } from './fixtures';

const openStreams = new Set<ReadableStreamDefaultController<Uint8Array>>();

/** Tempo real (GET /api/stream): a conexão fica aberta e sem avisos até o teste mandar um com pushStreamEvent. */
function openStream() {
  let current: ReadableStreamDefaultController<Uint8Array> | null = null;
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      current = controller;
      openStreams.add(controller);
    },
    cancel() {
      if (current) {
        openStreams.delete(current);
      }
    },
  });
  return new HttpResponse(body, { headers: { 'Content-Type': 'text/event-stream' } });
}

/** Quantas conexões de tempo real estão abertas (o aviso só chega em quem já conectou). */
export function connectedStreams(): number {
  return openStreams.size;
}

/** Manda um aviso de tempo real para todas as abas abertas no teste. */
export function pushStreamEvent(type: string, data: unknown): void {
  const chunk = new TextEncoder().encode(`event: ${type}\ndata: ${JSON.stringify(data)}\n\n`);
  openStreams.forEach((controller) => controller.enqueue(chunk));
}

/** Fecha as conexões de tempo real do teste que acabou. */
export function closeStreams(): void {
  openStreams.forEach((controller) => {
    try {
      controller.close();
    } catch {
      // Já estava fechada.
    }
  });
  openStreams.clear();
}

/** A API do cardápio lendo de um objeto que o teste pode alterar (o que foi salvo aparece na lista). */
export function serveMenu(menu: Menu): void {
  server.use(
    http.get('/api/sectors', () => HttpResponse.json(menu.sectors)),
    http.get('/api/categories', () => HttpResponse.json(menu.categories)),
    http.get('/api/option-groups', () => HttpResponse.json(menu.optionGroups)),
    http.get('/api/products', () => HttpResponse.json(menu.products)),
  );
}

/** API simulada. Por padrão ninguém está logado: a renovação de sessão responde 401. */
export const server = setupServer(
  http.post('/api/auth/refresh', () =>
    HttpResponse.json(apiError(401, 'Sua sessão expirou. Faça login novamente.'), { status: 401 }),
  ),
  http.get('/api/stream', openStream),
);
