/** Corpo de erro padrão da API (ver GlobalExceptionHandler no backend). */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  message: string;
  path: string;
  fields?: Record<string, string>;
}

export class ApiRequestError extends Error {
  readonly status: number;
  readonly fields: Record<string, string>;

  constructor(status: number, body: ApiErrorBody | null) {
    super(body?.message ?? defaultMessage(status));
    this.name = 'ApiRequestError';
    this.status = status;
    this.fields = body?.fields ?? {};
  }
}

interface FetchResult<T> {
  data?: T;
  error?: unknown;
  response: Response;
}

/** Converte o resultado do openapi-fetch em valor, ou em exceção com a mensagem da API. */
export async function unwrap<T>(request: Promise<FetchResult<T>>): Promise<T> {
  const { data, error, response } = await request;
  if (!response.ok) {
    throw new ApiRequestError(response.status, isApiErrorBody(error) ? error : null);
  }
  return data as T;
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    return error.message;
  }
  if (error instanceof TypeError) {
    return 'Sem conexão com o servidor. Verifique a internet e tente de novo.';
  }
  return 'Algo deu errado. Tente novamente.';
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  return typeof value === 'object' && value !== null && 'message' in value && 'status' in value;
}

/** Mensagem para erro sem o corpo padrão da API, ou seja, que não veio da API do PedeAí. */
function defaultMessage(status: number): string {
  if (status >= 500) {
    return 'O servidor teve um problema. Tente novamente em instantes.';
  }
  // A API sempre responde 404 com o corpo padrão. Um 404 sem ele vem da hospedagem: a API não está no ar.
  if (status === 404) {
    return 'Não foi possível falar com o servidor do PedeAí. Tente novamente mais tarde.';
  }
  return 'Não foi possível concluir a operação.';
}
