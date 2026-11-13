// Atualiza o openapi.json. Depois: npm run api:types para regenerar os tipos TypeScript.
// - npm run api:spec: baixa da API rodando localmente (precisa de API_DOCS_ENABLED=true).
// - npm run api:spec:test: usa o arquivo que o teste OpenApiContractTest do backend grava, sem subir a API
//   (rode antes, no backend: ./mvnw test -Dtest=OpenApiContractTest; o teste falha, mas grava o arquivo).
import { readFile, writeFile } from 'node:fs/promises';

const fromTest = process.argv.includes('--from-test');
const source = fromTest
  ? new URL('../../backend/target/openapi-live.json', import.meta.url)
  : (process.env.OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs');

let spec;
if (fromTest) {
  spec = JSON.parse(await readFile(source, 'utf8'));
} else {
  const response = await fetch(source);
  if (!response.ok) {
    console.error(`Falha ao baixar ${source}: HTTP ${response.status}`);
    process.exit(1);
  }
  spec = await response.json();
}
await writeFile(new URL('../openapi.json', import.meta.url), `${JSON.stringify(spec, null, 2)}\n`);
console.log(`openapi.json atualizado a partir de ${fromTest ? 'backend/target/openapi-live.json' : source}`);
