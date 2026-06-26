---
name: build-android-app
description: Build, extend, or repair native Android apps using Kotlin, Jetpack Compose, Android SDK, Material 3, Room, networking, permissions, and Gradle. Use when Codex is asked to create an Android app, add Android features, implement Compose UI, wire ViewModels/state, integrate local persistence, handle Android permissions or APIs, configure Android build files, or validate Android projects.
---

# Build Android App

Build the smallest native Android app or feature that satisfies the request. Prefer existing project patterns over new structure, platform APIs over extra libraries, and real integrations over fake placeholders.

## Workflow

1. Read the active repo instructions and inspect the Android surface before editing:
   - `settings.gradle*`, root `build.gradle*`, `gradle/libs.versions.toml`
   - `app/build.gradle*`, `AndroidManifest.xml`, `MainActivity`
   - existing `ui/`, `theme/`, `network/`, `data/`, `repository/`, `viewmodel/`, `service/`, and test packages when present
2. Run `scripts/inspect_android_project.py <project-or-app-dir>` when the project shape is unclear.
3. Keep scope tight. A simple utility app is usually one screen, one ViewModel only if state has behavior, and no navigation graph unless the request needs multiple destinations.
4. Use unidirectional data flow:
   - state lives in ViewModels or a repository-backed store
   - expose read-only `StateFlow`
   - collect with `collectAsStateWithLifecycle()`
   - pass state down and lambdas/events up
5. Build real platform behavior. If the request names GPS, camera, Bluetooth, notifications, sensors, Spotify, Fitbit, SQLite, or a remote API, implement the actual SDK/API path or clearly report the missing prerequisite. Do not simulate telemetry or credentials.
6. Validate with the smallest command that covers the touched code. Prefer repo-documented commands (run from the `android/` directory). Typical fallbacks:
   - `./gradlew testDebugUnitTest` (runs Robolectric unit tests and Roborazzi comparison checks)
   - `./gradlew recordRoborazziDebug` (to record new screenshot baseline artifacts)
   - `./gradlew assembleDebug`
   - `./gradlew :app:compileDebugKotlin`

## Reference Routing

- Read `references/compose-material3.md` for Compose UI, theming, edge-to-edge, accessibility, and app metadata.
- Read `references/state-architecture.md` for ViewModel, repository, Flow, and UDF patterns.
- Read `references/room.md` when adding local persistence.
- Read `references/network-permissions.md` when adding HTTP clients, API keys, dangerous permissions, hardware, or background-capable platform integrations.
- Read `references/testing-validation.md` before adding tests or reporting validation.

## Defaults

- Use Kotlin 2.x+ and Jetpack Compose unless the existing project is XML/View-based.
- Configure Compose compiler using the Compose Gradle plugin (`org.jetbrains.kotlin.plugin.compose` for Kotlin 2.0.0+) instead of the deprecated `composeOptions { kotlinCompilerExtensionVersion = ... }`.
- Use Material 3 tokens from `MaterialTheme`; put project colors, typography, and shapes in theme files.
- Enable edge-to-edge in `MainActivity` for new apps. Keep in mind that edge-to-edge is enforced by default starting on Android 15 (API 35), and cannot be opted out of on Android 16 (API 36). Always handle system bars with `WindowInsets.safeDrawing` or equivalent safe-area padding.
- Keep `namespace` aligned with existing packages. Give new apps a unique `applicationId`; do not change package directories unless requested.
- Use Gradle version catalogs when already present. In Kotlin DSL, convert catalog aliases from kebab case to dot notation, such as `androidx-core-ktx` to `libs.androidx.core.ktx`.
- Add dependencies only when platform APIs or existing dependencies are insufficient. Keep lockfiles/catalogs consistent.
- Never hardcode secrets, API keys, private URLs, keystores, or local machine values.
