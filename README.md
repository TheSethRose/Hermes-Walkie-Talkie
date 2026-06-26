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

From the repo root, the same dev server can be launched with:

```sh
make gateway-dev
```

For production-like local startup:

```sh
make gateway-start
```

Set a real API key in `gateway/.env`:

```env
GATEWAY_API_KEY=replace-with-a-long-random-value
```

The Android app must use the same API key.

## Connecting To Hermes Profiles

The default gateway mode calls the local Hermes CLI:

```env
HERMES_MODE=cli
HERMES_CLI_COMMAND=hermes
HERMES_CLI_ARGS_TEMPLATE=-z {prompt}
HERMES_PROFILE_SOURCE=auto
```

The gateway exposes `GET /profiles` and discovers local Hermes profiles with `hermes profile list` by default. The Android app loads that list in Settings and stores the selected profile id/name.

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
   Profile: load and select one from the Hermes Profile list
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

The app stores the gateway URL, API key, selected profile, response mode, and audio route preference in its settings screen. No build-time API key is required.

To build the debug APK from the repo root:

```sh
make android-build
```

To build, install, and launch it on a USB debugging device:

```sh
make android-deploy
```

To launch an already-installed build:

```sh
make android-launch
```

If `JAVA_HOME` needs to point somewhere else:

```sh
make android-deploy ANDROID_JAVA_HOME=/path/to/jbr-or-jdk
```
