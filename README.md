# Hermes Walkie Talkie

Hermes Walkie Talkie pairs an Android push-to-talk client with a local Hermes Voice Gateway.

## Project Status

This is an unaffiliated third-party project for use with [Hermes Agent](https://github.com/NousResearch/hermes-agent). It is not an official Nous Research or Hermes Agent project.

If Nous Research or the Hermes Agent maintainers want any of this code, they are welcome to use it, absorb it, relicense it, rename it, or claim it as their own project.

Use it on a private Tailscale network:

- `android/` contains the Android app.
- `gateway/` contains the Bun/Fastify server the app talks to.

The gateway is intended to run on the same computer as Hermes. The Android app connects to it over Tailscale.

## Layout

```text
Hermes-Walkie-Talkie/
  android/   Android voice remote app
  gateway/   Local Hermes Voice Gateway server
```

## Gateway Setup

```sh
cd gateway
cp .env.example .env
bun install
bun run dev
```

Set a real API key in `gateway/.env`:

```env
GATEWAY_API_KEY=replace-with-a-long-random-value
```

The Android app must use the same API key.

## Connecting To A Hermes Profile

The default gateway mode calls the local Hermes CLI:

```env
HERMES_MODE=cli
HERMES_CLI_COMMAND=hermes
HERMES_CLI_ARGS_TEMPLATE=-z {prompt}
HERMES_AGENT=main
```

`HERMES_AGENT` is stored with gateway sessions and forwarded to adapters, but the default CLI template uses the currently active Hermes profile. To use a specific Hermes profile, set `HERMES_CLI_COMMAND` or `HERMES_CLI_ARGS_TEMPLATE` to the command shape your Hermes install supports.

Examples:

```env
# Default/current Hermes profile
HERMES_CLI_COMMAND=hermes
HERMES_CLI_ARGS_TEMPLATE=-z {prompt}

# Wrapper script or alias for a specific profile
HERMES_CLI_COMMAND=hermes-my-profile
HERMES_CLI_ARGS_TEMPLATE=-z {prompt}
```

Speech-to-text and text-to-speech default to Hermes built-ins:

```env
STT_PROVIDER=hermes
TTS_PROVIDER=hermes
HERMES_STT_PYTHON=/path/to/.hermes/hermes-agent/venv/bin/python
HERMES_TTS_PYTHON=/path/to/.hermes/hermes-agent/venv/bin/python
```

Set those Python paths to the Python binary inside the Hermes installation venv on the server computer.

## Tailscale

1. Install Tailscale on the server computer and Android phone.
2. Log both into the same tailnet.
3. Start the gateway:

   ```sh
   cd gateway
   bun run dev
   ```

4. Find the server computer's Tailscale IP:

   ```sh
   tailscale ip -4
   ```

5. In the Android app settings, set:

   ```text
   Base URL: http://<tailscale-ip>:8789
   API key: <same value as GATEWAY_API_KEY>
   Agent/profile: main
   ```

MagicDNS also works when enabled:

```text
http://<machine-name>.<tailnet>.ts.net:8789
```

Do not expose the gateway to the public internet. Tailscale private networking plus bearer auth is the MVP security boundary.

## Local Checks

Health:

```sh
curl -H "Authorization: Bearer $GATEWAY_API_KEY" http://localhost:8789/health
```

Over Tailscale:

```sh
curl -H "Authorization: Bearer $GATEWAY_API_KEY" http://<tailscale-ip>:8789/health
```

## Android App

Open `android/` in Android Studio.

The app stores the gateway URL, API key, agent/profile name, response mode, and audio route preference in its settings screen. No build-time API key is required.
