// Baixa a especificação OpenAPI da API rodando localmente e salva em openapi.json.
// Depois: npm run api:types para regenerar os tipos TypeScript.
import { writeFile } from 'node:fs/promises';

const url = process.env.OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs';
const response = await fetch(url);
if (!response.ok) {
  console.error(`Falha ao baixar ${url}: HTTP ${response.status}`);
  process.exit(1);
}
const spec = await response.json();
await writeFile(new URL('../openapi.json', import.meta.url), `${JSON.stringify(spec, null, 2)}\n`);
console.log(`openapi.json atualizado a partir de ${url}`);
