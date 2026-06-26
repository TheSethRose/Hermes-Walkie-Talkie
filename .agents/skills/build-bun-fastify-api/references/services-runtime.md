# Services and Runtime Boundaries

## Services

- Put external API, CLI, filesystem, transcription, synthesis, session, profile, and storage logic behind services.
- Keep adapters narrow. Route handlers should not know command templates, provider-specific payloads, or storage internals.
- Add an interface only when there is already more than one real implementation or tests need a seam already present in the repo.

## Child Processes

- Use `spawn` with `shell: false` for commands built from config or user-adjacent input.
- Pass arguments as arrays.
- Enforce timeouts and kill stuck children.
- Capture bounded stderr/stdout for diagnostics.
- Do not log command output if it may contain secrets.

## File Storage

- Ensure storage directories exist at startup.
- Treat upload and generated-output directories as runtime data.
- Generate filenames with `randomUUID()` or equivalent.
- Validate served filenames and resolve them inside the configured directory.
- Do not commit generated uploads, generated audio, or runtime caches.

## Standard APIs

- Use standard `fetch` for HTTP calls unless the project already uses a client.
- Use `node:fs/promises`, streams, `pipeline`, `URL`, and `AbortController` where they cover the need.
- Add dependencies only when the built-in runtime or existing packages are insufficient.
