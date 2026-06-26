# Fastify Routes

## Shape

- Register route modules from `src/index.ts` or the existing composition root.
- Keep route handlers focused on HTTP parsing, auth hooks, status codes, and response shape.
- Move reusable work into `services/` or `utils/`.
- Type params/body when it improves safety:

```ts
app.post<{ Params: { sessionId: string } }>("/items/:sessionId", async (request, reply) => {
  return { sessionId: request.params.sessionId };
});
```

## Validation

- Use Zod or existing schemas for body/query/form data.
- Use `safeParse` at the route boundary and return `400` for invalid client input.
- Avoid trusting multipart field values; normalize them before passing to services.

## Errors

- Return 4xx for bad client requests and 5xx/502 for upstream or internal failures.
- Log internal errors with request logger or existing logger.
- Keep response errors useful but not secret-bearing.
- Preserve any central Fastify error handler. Add local handling only when the route can return a better domain status.

## Multipart and Files

- Keep global multipart limits strict.
- Consume or resume unused file streams so requests finish cleanly.
- Validate file type and extension before writing.
- Generate stored filenames server-side.
- Resolve served files inside the configured storage directory; never concatenate untrusted paths directly.

## API Contracts

- Update README/API docs and client models when response shapes or endpoints change.
- Preserve existing Android/app contract unless the user explicitly requests a contract change.
