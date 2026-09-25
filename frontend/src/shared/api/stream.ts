import { authorizedFetch } from './client';

export interface StreamEvent {
  type: string;
  data: string;
}

/**
 * Lê um corpo text/event-stream e entrega cada evento com nome. Comentários (": keepalive") são ignorados.
 * Termina quando o servidor fecha a conexão.
 */
export async function readEventStream(
  body: ReadableStream<Uint8Array>,
  onEvent: (event: StreamEvent) => void,
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { value, done } = await reader.read();
    if (done) {
      return;
    }
    buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n');
    let end = buffer.indexOf('\n\n');
    while (end >= 0) {
      const event = parseBlock(buffer.slice(0, end));
      if (event) {
        onEvent(event);
      }
      buffer = buffer.slice(end + 2);
      end = buffer.indexOf('\n\n');
    }
  }
}

function parseBlock(block: string): StreamEvent | null {
  let type = 'message';
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (line.startsWith(':')) {
      continue;
    }
    const colon = line.indexOf(':');
    const field = colon < 0 ? line : line.slice(0, colon);
    const value = colon < 0 ? '' : line.slice(colon + 1).replace(/^ /, '');
    if (field === 'event') {
      type = value;
    } else if (field === 'data') {
      data.push(value);
    }
  }
  return data.length > 0 ? { type, data: data.join('\n') } : null;
}

/**
 * Mantém uma conexão com GET /api/stream e reconecta sozinho, esperando mais a cada falha (até 30 s).
 * O EventSource nativo não manda o token de acesso, por isso a leitura é feita com fetch.
 *
 * @return função que fecha a conexão
 */
export function subscribeToStream(onEvent: (event: StreamEvent) => void, onReconnect?: () => void): () => void {
  const controller = new AbortController();
  void (async () => {
    let failures = 0;
    let connectedBefore = false;
    while (!controller.signal.aborted) {
      try {
        const response = await authorizedFetch('/api/stream', {
          headers: { Accept: 'text/event-stream' },
          signal: controller.signal,
        });
        if (response.ok && response.body) {
          failures = 0;
          // Voltou depois de cair: pode ter perdido avisos, então a tela recarrega tudo.
          if (connectedBefore) {
            onReconnect?.();
          }
          connectedBefore = true;
          await readEventStream(response.body, onEvent);
        } else {
          failures += 1;
        }
      } catch {
        if (controller.signal.aborted) {
          return;
        }
        failures += 1;
      }
      if (controller.signal.aborted) {
        return;
      }
      await wait(Math.min(30_000, 1_000 * 2 ** Math.min(failures, 5)), controller.signal);
    }
  })();
  return () => controller.abort();
}

function wait(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, ms);
    signal.addEventListener('abort', () => {
      clearTimeout(timer);
      resolve();
    });
  });
}
