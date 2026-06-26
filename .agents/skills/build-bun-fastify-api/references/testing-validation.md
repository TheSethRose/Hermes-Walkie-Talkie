# Testing and Validation

## Choose the Smallest Check

- Type-only or refactor change: `bun run typecheck`.
- Route contract change: run typecheck plus a focused HTTP check against the route if a local server is practical.
- Config/env change: typecheck and start once with safe example config when needed.
- File upload/download change: exercise the exact upload/download path with a small sample fixture or Fastify inject test if present.
- Child-process/provider change: validate mock mode or a harmless command path before touching live external services.

## Common Commands

Run from the Bun/Fastify project root:

```bash
bun install
bun run typecheck
bun run dev
bun run start
```

Use only the commands that fit the change. Do not run long-lived dev servers unless runtime behavior needs verification; stop them before final response.

## Curl Pattern

```bash
curl -i -H "Authorization: Bearer <safe-dev-key>" http://localhost:<port>/health
```

Do not paste real tokens into final output.

## Final Report

- Report exact validation commands and outcomes.
- Say when runtime behavior was not exercised.
- Do not claim external provider, microphone, TTS/STT, tailnet, or device behavior works unless it was tested in that environment.
