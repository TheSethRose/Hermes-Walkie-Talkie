# hermes-voice-gateway

Local Hermes Voice Gateway for the Android app "Hermes Voice Remote".

## Setup

```sh
cp .env.example .env
bun install
bun run dev
```

Default local URL:

```text
http://localhost:8789
```

Set `GATEWAY_API_KEY` in `.env` and use the same key in the Android app Settings screen.

## Hermes CLI

This machine's installed Hermes CLI supports one-shot prompts with:

```sh
hermes -z "<prompt>"
```

The gateway therefore defaults to:

```env
HERMES_CLI_ARGS_TEMPLATE=-z {prompt}
```

The requested generic contract is still supported by changing the template:

```env
HERMES_CLI_ARGS_TEMPLATE=--agent {agent} --prompt {prompt}
```

Arguments are passed without a shell. If the local Hermes CLI changes, update only `HERMES_CLI_ARGS_TEMPLATE`.

## Mock Mode

For Android integration without external STT/TTS/Hermes:

```env
MOCK_MODE=true
GATEWAY_API_KEY=dev-local-key
```

Mock mode returns:

- transcript: `Mock transcript from uploaded audio.`
- responseText: `Hermes mock response received: <transcript>`
- text/audio mode returns `audioUrl: null` with a warning because no fake MP3 is generated.

## Speech-to-Text

The default `STT_PROVIDER=hermes` calls Hermes' built-in STT implementation from the installed Hermes venv:

```env
HERMES_STT_PYTHON=/path/to/.hermes/hermes-agent/venv/bin/python
```

That uses the same Hermes STT provider chain configured in Hermes itself. Set `STT_PROVIDER=openai_compatible` only when pointing this gateway at a separate OpenAI-compatible transcription service.

## Text-to-Speech

The default `TTS_PROVIDER=hermes` calls Hermes' built-in TTS implementation from the installed Hermes venv and writes MP3 files into `storage/audio`:

```env
HERMES_TTS_PYTHON=/path/to/.hermes/hermes-agent/venv/bin/python
```

That uses the same Hermes TTS provider and voice configured in Hermes itself. Set `TTS_PROVIDER=openai_compatible` only when pointing this gateway at a separate OpenAI-compatible speech service.

## API

All endpoints require:

```text
Authorization: Bearer <GATEWAY_API_KEY>
```

`/health` also requires auth by default. Set `HEALTH_REQUIRES_AUTH=false` only for local diagnostics.

### `GET /health`

Returns:

```json
{
  "ok": true,
  "service": "hermes-voice-gateway",
  "mode": "mock",
  "time": "2026-06-25T00:00:00.000Z"
}
```

### `POST /voice/session`

```json
{
  "agent": "Vex Volt",
  "responseMode": "text_audio"
}
```

### `POST /voice/session/:sessionId/turn`

Multipart fields:

- `audio`: `.m4a` upload
- `format`: `m4a`
- `agent`: selected Hermes profile
- `responseMode`: `text` or `text_audio`

### `POST /voice/session/:sessionId/cancel`

Returns `204`.

### `GET /audio/:fileName`

Serves generated MP3 files with bearer auth.

## Tailscale

1. Install Tailscale on the server computer and Android phone.
2. Log both devices into the same tailnet.
3. Start the gateway:

   ```sh
   bun install
   bun run dev
   ```

4. Find the server computer's Tailscale IP:

   ```sh
   tailscale ip -4
   ```

5. Set the Android app Hermes Voice Gateway URL:

   ```text
   http://<tailscale-ip>:8789
   ```

6. Use the same `GATEWAY_API_KEY` in the Android app.
7. Test from a shell:

   ```sh
   curl -H "Authorization: Bearer $GATEWAY_API_KEY" http://<tailscale-ip>:8789/health
   ```

If MagicDNS is enabled, this should also work:

```text
http://<machine-name>.<tailnet>.ts.net:8789
```

TLS is not required for this private Tailscale MVP. Use HTTPS or Tailscale Serve before exposing anything beyond your tailnet. Do not expose this gateway publicly without bearer auth.

To bind only to the Tailscale interface, set:

```env
GATEWAY_HOST=<tailscale-ip>
```

## Local curl tests

Health:

```sh
curl -H "Authorization: Bearer dev-local-key" http://localhost:8789/health
```

Create session:

```sh
curl -X POST http://localhost:8789/voice/session \
  -H "Authorization: Bearer dev-local-key" \
  -H "Content-Type: application/json" \
  -d '{"agent":"Vex Volt","responseMode":"text_audio"}'
```

Voice turn:

```sh
curl -X POST http://localhost:8789/voice/session/<sessionId>/turn \
  -H "Authorization: Bearer dev-local-key" \
  -F "audio=@sample.m4a" \
  -F "format=m4a" \
  -F "agent=Vex Volt" \
  -F "responseMode=text_audio"
```

Text-only mode skips TTS:

```sh
curl -X POST http://localhost:8789/voice/session/<sessionId>/turn \
  -H "Authorization: Bearer dev-local-key" \
  -F "audio=@sample.m4a" \
  -F "format=m4a" \
  -F "agent=Vex Volt" \
  -F "responseMode=text"
```
