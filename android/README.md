# Hermes Voice Remote

Native Android push-to-talk client for an existing Hermes Voice Gateway.

## Requirements

- Android Studio
- Android phone or emulator
- Hermes Voice Gateway reachable over Tailscale

## Configure

In the app Settings screen:

- Hermes Voice Gateway URL
  Example Tailscale URL: http://100.x.y.z:8789
- API key
- Agent/profile name
  Example: Vex Volt
- Response mode
  Text only or Text + audio
- Audio route
  Auto, Phone mic or Bluetooth headset

## Expected Gateway API

GET /health
POST /voice/session
POST /voice/session/{sessionId}/turn
POST /voice/session/{sessionId}/cancel

## Tailscale testing

For a gateway running on your computer:

1. Install Tailscale on the phone and computer.
2. Log both devices into the same tailnet.
3. Find the computer's Tailscale IP:

```sh
tailscale ip -4
```

4. Enter this in app settings:

```text
http://<tailscale-ip>:8789
```

Debug builds allow cleartext HTTP for private Tailscale testing. Use HTTPS before exposing the gateway beyond a private tailnet.
