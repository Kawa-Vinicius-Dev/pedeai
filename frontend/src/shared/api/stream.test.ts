import { describe, expect, it } from 'vitest';
import { readEventStream, type StreamEvent } from './stream';

function streamOf(...chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
}

describe('leitura do SSE', () => {
  it('entrega os eventos com nome e ignora comentários, mesmo quebrados no meio', async () => {
    const events: StreamEvent[] = [];

    await readEventStream(
      streamOf(':conectado\n\nevent:order.created\nda', 'ta:{"orderId":"a1","number":7}\n\n:keepalive\n\n',
        'event: order.status_changed\r\ndata: {"status":"READY"}\r\n\r\n'),
      (event) => events.push(event),
    );

    expect(events).toEqual([
      { type: 'order.created', data: '{"orderId":"a1","number":7}' },
      { type: 'order.status_changed', data: '{"status":"READY"}' },
    ]);
  });
});
