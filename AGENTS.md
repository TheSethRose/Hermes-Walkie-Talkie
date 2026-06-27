# AGENTS.md

## Project Overview
- This repo contains Hermes Walkie Talkie: a native Android voice remote plus a local Hermes Voice Gateway.
- `android/` is the Android app. It supports tap-to-talk, push-to-talk, and experimental always-listening mode over HTTP/WebSocket, normally through Tailscale.
- `gateway/` is the Bun/Fastify Hermes Voice Gateway. It exposes the app API and centralizes Hermes CLI/HTTP/mock calls through `gateway/src/services/hermesBridge.ts`.
- The gateway is intended for a private tailnet plus bearer auth. Do not make public-internet exposure or auth changes casually.

## Setup Commands
- Root Make targets: `make gateway-dev`, `make gateway-start`, `make android-build`, `make android-deploy`, `make android-launch`
- Gateway install: `cd gateway && bun install`
- Gateway dev server: `cd gateway && bun run dev`
- Gateway production-like start: `cd gateway && bun run start`
- Android project: open `android/` in Android Studio, or run Gradle from `android/` with `./gradlew`.
- Android deploy uses `ANDROID_JAVA_HOME` from the root `Makefile`; override it per command instead of hardcoding a machine path.

## Test and Validation Commands
- Gateway typecheck: `cd gateway && bun run typecheck`
- Android unit tests: `cd android && ./gradlew testDebugUnitTest`
- Android debug build: `cd android && ./gradlew assembleDebug`
- Android screenshot tests use Roborazzi dependencies already present in Gradle; update screenshot artifacts only when the expected UI intentionally changes.
- Prefer the smallest command that covers the touched area. Use Android Studio/device testing for microphone, Bluetooth, foreground service, and Tailscale behavior.

## Code Style
- Prefer `rtk` for code search when available; use `rg` when it is not.
- Gateway code is TypeScript ESM. Keep imports explicit with `.js` suffixes for local TS modules, matching the existing source.
- Gateway route handlers live under `gateway/src/routes/`; reusable behavior lives under `gateway/src/services/` or `gateway/src/utils/`. Keep Zod validation at route boundaries.
- Android code is Kotlin with Jetpack Compose. Keep UI under `android/app/src/main/java/com/hermes/voiceremote/ui/`, settings under `settings/`, gateway and streaming clients under `network/`, audio/VAD/playback under `audio/`, and voice/session orchestration under `service/` and `state/`.
- Follow existing formatting in touched files: two-space indentation in gateway TypeScript, four-space indentation in Kotlin.

## Architecture Notes
- Treat `hermesBridge.ts` as the gateway's Hermes adapter boundary. CLI/HTTP/mock Hermes behavior belongs there, not in route handlers.
- Gateway profile discovery belongs in `gateway/src/services/profileStore.ts`; STT/TTS provider behavior belongs in `gateway/src/services/stt.ts`, `sttWorker.ts`, and `tts.ts`.
- The Android app must use the same gateway API contract documented in `gateway/README.md`: `/health`, `/profiles`, `/tts/voices`, `/voice/session`, `/voice/session/:sessionId/turn`, `/voice/session/:sessionId/cancel`, `/voice/session/:sessionId/reset`, `/voice/session/:sessionId/end`, experimental `WS /voice/stream`, and authenticated `/audio/:fileName`.
- Keep the HTTP turn API as the stable baseline. WebSocket streaming is experimental and should reuse the same STT, Hermes, TTS, auth, profile, and session services instead of creating a parallel stack.
- Keep app settings runtime-configurable. The Android app stores gateway URL, API key, profile, TTS voice, response mode, audio route preference, talk interaction mode, VAD settings, and barge-in settings in encrypted preferences. Do not add build-time gateway URLs or API keys.
- Text-only response mode should skip TTS. Text + audio mode may return `audioUrl: null` when audio generation is unavailable.
- Always-listening mode sends PCM16 mono 16 kHz audio chunks and depends on local VAD. `android/app/src/main/assets/silero_vad.onnx` is a model asset, not source code; replace it only when intentionally updating the bundled VAD model.

## Safety and Data Rules
- Do not read, print, or commit `.env`, `.env.*`, `local.properties`, keystores, API keys, or tailnet-specific private values.
- Use `.env.example` files and README examples for documented configuration.
- Do not weaken bearer auth, upload limits, cleartext/network rules, or Tailscale/private-network assumptions as a workaround.
- Do not add plaintext settings fallback on Android. `SettingsRepository` intentionally fails closed when encrypted storage cannot initialize.
- `gateway/storage/audio/` and `gateway/storage/uploads/` are runtime output directories. Do not commit generated audio/uploads.
- `android/app/src/test/screenshots/` contains test screenshot artifacts. Update them only when the visual test expectation intentionally changes.
- Keep generated Gradle output, `.kotlin/`, `node_modules/`, repomix output, launcher icon binaries, and local IDE state out of commits.
- Keep `gateway/bun.lock` with dependency changes. Do not add dependencies unless existing platform, stdlib, or installed packages are insufficient.
- Use non-interactive diffs: `git --no-pager diff` or `git diff | cat`.

## Custom Agent Skills

This repository includes two specialized agent skills to guide task execution:

1. **build-android-app** ([SKILL.md](file:///.agents/skills/build-android-app/SKILL.md))
   - **When to use**: Whenever you modify or debug the native Android app under `android/`, Jetpack Compose UI, ViewModels, Room local database, network API clients, or Gradle build files.
   - **How to use**: Refer to guidelines in `SKILL.md` and the `references/` subdirectory (covering compose/Material 3, edge-to-edge, state flow, and runtime permissions). Run validations inside the `android/` directory (e.g. `./gradlew testDebugUnitTest` and Roborazzi screenshot test commands).

2. **build-bun-fastify-api** ([SKILL.md](file:///.agents/skills/build-bun-fastify-api/SKILL.md))
   - **When to use**: Whenever you modify, debug, or validate the local voice gateway under `gateway/`, including Fastify route handlers, Zod schema validation, bearer authentication, child process execution, or service logic.
   - **How to use**: Refer to guidelines in `SKILL.md` and the `references/` subdirectory (covering fastify routes, config/auth/security, child process spawning, and runtime boundaries). Run validations inside the `gateway/` directory (e.g. `bun run typecheck`).
