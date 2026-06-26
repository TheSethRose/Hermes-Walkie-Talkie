# Testing and Validation

## Choose the Smallest Check

- UI-only Compose change: run the relevant Compose test if present, then `./gradlew :app:compileDebugKotlin` or `./gradlew assembleDebug` when practical.
- ViewModel/repository logic: add or run focused JVM unit tests.
- Room schema or DAO behavior: add or run Room/JVM tests when existing test infrastructure supports it.
- Manifest, permissions, or Gradle changes: run `./gradlew assembleDebug`.
- Screenshot expectations: use the project's existing screenshot tool, such as Roborazzi, only when visual baselines are part of the task.

## Common Commands

Run from the `android/` directory:

```bash
# Run unit tests and compare Roborazzi screenshot tests
./gradlew testDebugUnitTest

# Record/overwrite Roborazzi screenshot baseline artifacts
./gradlew recordRoborazziDebug

# Compare screenshot baselines against current outputs
./gradlew compareRoborazziDebug

# Compile Kotlin files
./gradlew :app:compileDebugKotlin

# Build debug APK
./gradlew assembleDebug
```

When building or testing from the command line on macOS, if you run into JDK version or path issues, you can prefix Gradle commands with the Android Studio bundled JDK (as defined in the root `Makefile`):
```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest
```

If the environment provides an Android-specific compile helper such as `compile_applet`, use it only when repo instructions or the current harness expects it.

## Fix Loop

- Read the first compiler error and fix the root cause.
- Check imports, package declarations, catalog aliases, plugin setup, and API-level compatibility before changing architecture.
- Stop after three build-fix attempts and report the exact remaining blocker.

## Final Report

Report exact commands and outcomes. Do not claim device behavior such as microphone, Bluetooth, GPS, notifications, or Tailscale works unless it was tested on a matching device or environment.
