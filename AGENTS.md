# AGENTS.md

## Project Overview
- This repo contains Hermes Walkie Talkie: a native Android push-to-talk app plus a local Hermes Voice Gateway.
- `android/` is the Android app. It talks to the gateway over HTTP, normally through Tailscale.
- `gateway/` is the Bun/Fastify Hermes Voice Gateway. It exposes the app API and calls Hermes only through `gateway/src/services/hermesBridge.ts`.
- The gateway is intended for a private tailnet plus bearer auth. Do not make public-internet exposure or auth changes casually.

## Setup Commands
- Gateway install: `cd gateway && bun install`
- Gateway dev server: `cd gateway && bun run dev`
- Gateway production-like start: `cd gateway && bun run start`
- Android project: open `android/` in Android Studio, or run Gradle from `android/` with `./gradlew`.

## Test and Validation Commands
- Gateway typecheck: `cd gateway && bun run typecheck`
- Android unit tests: `cd android && ./gradlew testDebugUnitTest`
- Android debug build: `cd android && ./gradlew assembleDebug`
- Prefer the smallest command that covers the touched area. Use Android Studio/device testing for microphone, Bluetooth, foreground service, and Tailscale behavior.

## Code Style
- Gateway code is TypeScript ESM. Keep imports explicit with `.js` suffixes for local TS modules, matching the existing source.
- Gateway route handlers live under `gateway/src/routes/`; reusable behavior lives under `gateway/src/services/` or `gateway/src/utils/`.
- Android code is Kotlin with Jetpack Compose. Keep UI under `android/app/src/main/java/com/hermes/voiceremote/ui/`, settings under `settings/`, gateway networking under `network/`, and voice/session orchestration under `service/` and `state/`.
- Follow existing formatting in touched files: two-space indentation in gateway TypeScript, four-space indentation in Kotlin.

## Architecture Notes
- Treat `hermesBridge.ts` as the gateway's Hermes adapter boundary. CLI/HTTP/mock Hermes behavior belongs there, not in route handlers.
- The Android app must use the same gateway API contract documented in `gateway/README.md`: `/health`, `/voice/session`, `/voice/session/:sessionId/turn`, `/voice/session/:sessionId/cancel`, and authenticated `/audio/:fileName`.
- Keep app settings runtime-configurable. The Android app does not require a build-time gateway URL or API key.
- Text-only response mode should skip TTS. Text + audio mode may return `audioUrl: null` when audio generation is unavailable.

## Safety and Data Rules
- Do not read, print, or commit `.env`, `.env.*`, `local.properties`, keystores, API keys, or tailnet-specific private values.
- Use `.env.example` files and README examples for documented configuration.
- Do not weaken bearer auth, upload limits, cleartext/network rules, or Tailscale/private-network assumptions as a workaround.
- `gateway/storage/audio/` and `gateway/storage/uploads/` are runtime output directories. Do not commit generated audio/uploads.
- `android/app/src/test/screenshots/` contains test screenshot artifacts. Update them only when the visual test expectation intentionally changes.
- Keep `gateway/bun.lock` with dependency changes. Do not add dependencies unless existing platform, stdlib, or installed packages are insufficient.
