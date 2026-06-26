# Networking, Secrets, and Permissions

## Networking

- Add `android.permission.INTERNET` for network calls.
- Reuse the existing HTTP stack when present. Common choices are Retrofit, Ktor, OkHttp, or platform APIs.
- Keep DTOs and API clients outside Composables.
- Switch blocking work off the main thread with coroutines or the client library's suspend support.
- Model loading, empty, success, and error states explicitly.

## Secrets

- Never hardcode API keys, bearer tokens, private URLs, keystores, or local credentials.
- Prefer runtime settings for user-provided endpoints and keys in private-network apps.
- For build-time keys, use the project's existing secrets mechanism or the Secrets Gradle Plugin.
- Treat missing keys as a user-visible setup state, not a crash.

## Dangerous and Runtime Permissions

For camera, microphone, Bluetooth, location, notifications, contacts, files, or nearby devices:

1. **Declare the manifest permission**:
   - For Android 13+ (API 33+), declare and request `POST_NOTIFICATIONS` at runtime before showing notifications.
   - For Android 12+ (API 31+), declare and request `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` at runtime instead of relying on legacy manifest-only permissions.
   - For foreground services with microphone access, declare `RECORD_AUDIO` and `FOREGROUND_SERVICE_MICROPHONE`.
2. **Request runtime permission**: Use AndroidX Activity Result APIs (`RequestPermission` or `RequestMultiplePermissions` contracts) or the project's existing permission helper.
3. **Handle states**: Handle granted, denied, and show permission rationale if needed.
4. **Graceful degradation**: Degrade gracefully when permission is denied rather than crashing.

## Hardware and Platform Integrations

- **Real Implementations**: Use real SDK callbacks or platform services for sensors, GPS, media, Bluetooth, notifications, and foreground services. Do not fake hardware telemetry unless explicitly asked.
- **Foreground Services (API 34/35/36+)**:
   - Declare the matching `android:foregroundServiceType` attribute in the manifest `<service>` tag (e.g. `microphone`, `mediaPlayback`, `dataSync`).
   - Request the corresponding permission (e.g., `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE`).
   - Call `startForeground(id, notification, type)` with the correct `ServiceInfo.FOREGROUND_SERVICE_TYPE_*` parameter for API 29+.
   - Handle Android 15 (API 35)+ timeouts: `dataSync` and `mediaProcessing` foreground service types have a 6-hour runtime limit per 24 hours. Override `Service.onTimeout(startId, fsi)` to call `stopSelf()` gracefully and avoid system crashes (`RemoteServiceException`).
