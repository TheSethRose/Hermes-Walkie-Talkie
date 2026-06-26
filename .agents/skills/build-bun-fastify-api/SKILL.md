---
name: build-bun-fastify-api
description: Build, extend, debug, or validate Bun + Fastify APIs written in TypeScript ESM. Use when Codex is asked to work on Fastify routes, Bun runtime services, multipart/file upload endpoints, bearer-auth private APIs, Zod/env validation, child-process integrations, local storage paths, API contracts, or TypeScript backend validation in a Bun project.
---

# Build Bun Fastify API

Build the smallest API change that satisfies the request. Prefer existing routes, services, utilities, schemas, and Node/Bun standard APIs before adding abstractions or dependencies.

## Workflow

1. Read repo instructions and inspect the backend surface before editing:
   - `package.json`, `tsconfig.json`, `.env.example`, README/API docs
   - `src/index.ts`, `src/config.ts`, `src/auth.ts`
   - touched `src/routes/`, `src/services/`, and `src/utils/` files
2. Run `scripts/inspect_bun_fastify_project.py <project-dir>` when the project shape is unclear.
3. Keep route handlers thin. Put reusable behavior in existing `services/` or `utils/` modules.
4. Validate inputs at the boundary with Zod or existing schemas. Return explicit 4xx errors for bad client input.
5. Keep secrets and private values in env/config. Never print `.env` values, bearer tokens, private URLs, or local machine paths.
6. Preserve auth, upload limits, private-network assumptions, path traversal protections, and runtime storage boundaries.
7. Validate with the smallest command that covers the change. Prefer repo scripts:
   - `bun run typecheck`
   - focused curl/inject checks when behavior changed
   - `bun run start` or `bun run dev` only when live runtime verification is needed

## Reference Routing

- Read `references/fastify-routes.md` for route shape, error handling, multipart uploads, and API contracts.
- Read `references/config-auth-security.md` for Zod env config, bearer auth, secret handling, and private-network constraints.
- Read `references/services-runtime.md` for child processes, file storage, standard APIs, and service boundaries.
- Read `references/testing-validation.md` before adding checks or reporting validation.

## Defaults

- Use TypeScript ESM and explicit `.js` suffixes for local imports.
- Use two-space indentation unless the touched file differs.
- Use `node:` imports for Node standard modules.
- Use `fetch`, `URL`, streams, `node:fs/promises`, and Bun/Node standard APIs before adding packages.
- Keep dependency changes rare and update `bun.lock` when dependencies change.
- Do not weaken auth, CORS, upload limits, path validation, logging privacy, or error handling as a workaround.
