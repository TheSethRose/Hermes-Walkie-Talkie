# Config, Auth, and Security

## Env Config

- Parse env once in `src/config.ts` or the existing config module.
- Use Zod for runtime validation and defaults.
- Convert strings to booleans/numbers deliberately.
- Trim trailing slashes from base URLs in config, not in every caller.
- Document new env vars in `.env.example` without real secrets.

## Secrets

- Do not read, print, log, or commit `.env` values.
- Do not hardcode API keys, bearer tokens, private tailnet hosts, or local credential paths.
- Error messages may name the missing variable but not its value.

## Bearer Auth

- Require `Authorization: Bearer <token>` for private endpoints unless repo docs explicitly exempt them.
- Compare tokens with a timing-safe helper when available.
- Do not add public unauthenticated endpoints casually.
- If a health endpoint can be unauthenticated for diagnostics, keep that controlled by config and default private.

## Private Network Assumptions

- Treat Tailscale/private-network deployment as a security boundary plus bearer auth, not a replacement for auth.
- Do not loosen CORS, bind hosts, auth, upload limits, or path rules to make local testing easier.
- Prefer runtime settings and `.env.example` documentation over checked-in machine-specific values.
