---
version: alpha
name: Hermes Voice Design System
colors:
  primary: "#FFFFD600"       # HermesYellow
  primary-muted: "#FF806F00" # HermesYellowMuted
  background: "#FF121212"    # HermesBg (Pure Dark Theme)
  surface: "#FF1C1C1C"       # HermesSurface
  surface-variant: "#FF242424" # HermesSurfaceVariant
  border: "#FF333333"        # HermesBorder
  text-primary: "#FFF2F2F2"  # HermesTextPrimary
  text-secondary: "#FFA8A8A8" # HermesTextSecondary
  error: "#FFFF5252"         # HermesError
  success: "#FF22C55E"       # HermesSuccess
typography:
  header:
    fontFamily: "System"
    fontSize: "20px"
    fontWeight: 700
  body-large:
    fontFamily: "System"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: "24px"
  body-small:
    fontFamily: "System"
    fontSize: "11px"
    fontWeight: 400
    lineHeight: "14px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
rounded:
  sm: "4px"
  md: "8px"
  lg: "12px"
  circle: "9999px"
---

# Hermes Voice Design System

This design document defines the visual identity, tokens, guidelines, and interactive constraints for the Hermes Walkie-Talkie client interface, ensuring consistency across all screens, states, and components.

## Overview

Hermes Walkie-Talkie uses a premium, dark-mode-first aesthetic optimized for fast, screen-free or quick-interaction push-to-talk (PTT) use cases. The primary key brand element is **Hermes Yellow** against high-contrast slate dark fields, creating an action-oriented, premium hardware-like feel.

---

## Colors

The application operates exclusively on a dark mode color palette. The color scheme mimics high-quality audio hardware interfaces.

| Token | Hex Value | Android Theme Equivalent | Usage / Rationale |
| :--- | :--- | :--- | :--- |
| `primary` | `#FFFFD600` | `HermesYellow` | Main accent, interactive states, and active PTT actions. |
| `primary-muted` | `#FF806F00` | `HermesYellowMuted` | Subdued states, inactive borders, and secondary accents. |
| `background` | `#FF121212` | `HermesBg` | Screen background. Promotes deep contrast and battery savings on OLED displays. |
| `surface` | `#FF1C1C1C` | `HermesSurface` | Cards, dialog backdrops, and secondary content blocks. |
| `surface-variant`| `#FF242424` | `HermesSurfaceVariant` | Inner containers, state-specific button backgrounds. |
| `border` | `#FF333333` | `HermesBorder` | Divider lines and container outlines. |
| `text-primary` | `#FFF2F2F2` | `HermesTextPrimary` | High-readability titles, body text, and active labels. |
| `text-secondary` | `#FFA8A8A8` | `HermesTextSecondary` | Subtitles, descriptive captions, and disabled helper text. |
| `error` | `#FFFF5252` | `HermesError` | Error indicators, active recording state backgrounds. |
| `success` | `#FF22C55E` | `HermesSuccess` | Confirmed network checks, successfully synced state indicators. |

---

## Typography

Typography relies on Android's default system fonts (`FontFamily.Default`), styled to prioritize legibility under walkie-talkie runtime conditions (e.g. outdoors, moving).

*   **Header (`header`)**: `20sp` Bold. Used for TopAppBars, primary settings headers, and main dialog titles.
*   **Body Large (`body-large`)**: `16sp` Normal. Applied to primary text content, transcript bodies, and settings menu options.
*   **Body Small (`body-small`)**: `11sp` Normal. Used for secondary status captions, settings summaries, and loading context helper text.

---

## Layout and Spacing

Spacing is structured around a predictable **4dp/8dp grid** to create clear visual hierarchies and appropriate padding:

*   **Touch Targets**: Interactive controls must maintain a minimum bounding box of `48dp` x `48dp` to prevent input collisions.
*   **Default Padding**:
    *   Outer screen padding: `16dp` (`lg`)
    *   Inside cards or grouped panels: `12dp` (`md`)
    *   Space between related components: `8dp` (`sm`)
    *   Sub-label / element inline margins: `4dp` (`xs`)

---

## Elevation & Depth

To match a hardware device feel, depth is represented through color layering (higher surface variance) rather than shadows:

1.  **Level 0 (Background)**: `#121212` (Base screen)
2.  **Level 1 (Surfaces)**: `#1C1C1C` (Cards, sheet structures)
3.  **Level 2 (Containers)**: `#242424` (Sub-panels, button boundaries)

---

## Components

### 1. Talk Button (`TalkButton`)
*   **Visual Structure**: Circle shape (`CircleShape`), diameter of `180dp`, bound by a `4dp` border.
*   **Dynamic Scale**: Scales by `1.08f` using a looping reverse animation (`800ms` duration, `FastOutSlowInEasing`) during **LISTENING** or **SPEAKING** states.
*   **State-Dependent Colors**:
    *   `IDLE`: `primaryContainer` / `#242424` background, `primary` / `#FFFFD600` content/text.
    *   `LISTENING`: `error` / `#FFFF5252` background, `onError` / `#111111` content/text.
    *   `UPLOADING` & `THINKING`: `surfaceVariant` / `#242424` background, `onSurfaceVariant` / `#FFA8A8A8` content. Shows a `CircularProgressIndicator` (size `36dp`).
    *   `SPEAKING`: `primary` / `#FFFFD600` background, `onPrimary` / `#111111` content/text.
    *   `ERROR`: `error` / `#FFFF5252` background, `onError` / `#111111` content.

### 2. Status Pill (`StatusPill`)
*   Provides instant connection diagnostics (health, latency, state). Uses a rounded badge structure with `HermesSuccess` (connected) or `HermesError` (offline).

### 3. Response Card (`ResponseCard`)
*   Groups text and voice playback options. Standard `12dp` internal padding, outlined by the `HermesBorder`.

---

## Do's and Don'ts

### Do
*   **Do** use standard Material 3 semantic properties (`MaterialTheme.colorScheme.*`) that map back to the colors defined in this system.
*   **Do** ensure all interactive components provide a touch target of at least `48dp`.
*   **Do** handle system bars using `WindowInsets.safeDrawing` or corresponding inset configurations to maintain edge-to-edge layout safety.

### Don't
*   **Don't** hardcode hexadecimal colors inside individual screen composables; use tokens from `Color.kt` or `MaterialTheme.colorScheme`.
*   **Don't** use drop shadows, multi-colored gradients, or bright light-themed components; the app is strictly unified under a flat, premium dark palette.
*   **Don't** add random animations or transition curves; stick to standard `tween(...)` and physics-based transitions matching existing UI animations.
