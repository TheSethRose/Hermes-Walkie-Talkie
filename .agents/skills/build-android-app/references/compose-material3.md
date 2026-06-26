# Compose and Material 3

## App Metadata

- For new apps, set a unique `applicationId` such as `com.aistudio.<theme>.<suffix>` to avoid device conflicts.
- Keep `namespace` and source package layout consistent with the project unless the user asks for a rename.
- Sync visible app name in `res/values/strings.xml` with any app metadata file the project already uses.

## Theme

- Put colors, typography, and shapes in the existing theme package.
- Use `MaterialTheme.colorScheme`, `MaterialTheme.typography`, and `MaterialTheme.shapes` in composables.
- Avoid hardcoded hex colors inside feature composables. Add named tokens in theme files when a color is reusable.
- Support dynamic color on Android 12+ for new Material 3 apps; provide static light/dark fallback schemes.

## Layout and System UI

- Call `enableEdgeToEdge()` in `MainActivity.onCreate()` for backward compatibility. Starting with Android 15 (API 35), edge-to-edge is enforced by default, and starting with Android 16 (API 36), it cannot be opted out of (opt-out attributes like `windowOptOutEdgeToEdgeEnforcement` are ignored).
- Use `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)` or the project's equivalent inset strategy.
- Apply `navigationBarsPadding()` or `windowInsetsPadding(WindowInsets.navigationBars)` to custom bottom controls.
- Support Predictive Back Navigation: Predictive back animations are enabled by default for apps targeting Android 16 (API 36). Avoid legacy `onBackPressed()` overrides and use Compose's back handlers or AndroidX `OnBackPressedCallback`.
- Build adaptive, responsive layouts for large screens: On devices with smallest width >= 600dp, Android 16 overrides orientation, aspect-ratio, or resizability restrictions. UIs must adapt dynamically.
- Use an 8dp spacing rhythm. Common defaults: `16.dp` page padding, `12.dp` card padding, `8.dp` nearby element spacing.
- Keep touch targets at least `48.dp` in both dimensions for interactive controls.

## Accessibility and Tests

- Give meaningful `contentDescription` values to actionable icons and images.
- Use `contentDescription = null` only for decorative visuals.
- Add stable `Modifier.testTag(...)` values to primary actions, fields, navigation targets, and high-value state containers.
- Prefer clear labels over icon-only controls unless the icon is standard and still accessible.

## Visual Assets

- Replace default launcher assets for new polished apps when scope includes visual polish.
- Use actual product/state imagery or generated raster assets only when they improve the requested experience.
- Do not add decorative image generation for small internal tools unless asked.
