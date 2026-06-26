# Future Streaming Voice Plan

## Goal

Add a future voice mode that feels closer to a live assistant:

- user can start talking without waiting for a full upload cycle
- assistant can begin responding before the full answer is complete
- user can interrupt with voice or button
- interruption is not overly sensitive to background noise
- current HTTP gateway/app flow keeps working

This is not a near-term rewrite. The current request/response gateway remains the stable baseline.

## Current Limitation

Today the app does one complete turn at a time:

1. Android records audio locally.
2. Android uploads the finished audio file.
3. Gateway runs STT.
4. Gateway sends text to Hermes.
5. Gateway waits for the full Hermes response.
6. Gateway runs TTS.
7. Android downloads and plays the finished audio.

That flow is simple and reliable, but it cannot support natural barge-in or partial response playback. At best, the button can stop playback after the response has already been generated.

## What True Streaming Would Mean

Streaming needs a second transport alongside the existing HTTP endpoints. Good candidates:

- WebSocket: best fit for bidirectional audio, partial transcripts, partial assistant text, control events, and interruption.
- SSE plus HTTP upload chunks: simpler server-to-client streaming, weaker for live microphone/control events.
- Chunked HTTP audio: useful for response audio only, not enough for full duplex voice.

Recommended path: WebSocket for the future streaming mode, while keeping the current HTTP API unchanged.

## Proposed Session Flow

Android opens:

```text
WS /voice/stream?sessionId=...&profileId=...
```

Auth stays the same as the gateway unless pairing replaces API keys later.

Client sends events:

```json
{ "type": "audio_chunk", "seq": 1, "format": "pcm16", "sampleRate": 16000, "data": "base64..." }
{ "type": "speech_start" }
{ "type": "speech_end" }
{ "type": "interrupt" }
{ "type": "end_session" }
```

Gateway sends events:

```json
{ "type": "partial_transcript", "text": "..." }
{ "type": "final_transcript", "text": "..." }
{ "type": "assistant_text_delta", "text": "..." }
{ "type": "assistant_text_final", "text": "..." }
{ "type": "audio_chunk", "seq": 1, "format": "mp3", "data": "base64..." }
{ "type": "state", "status": "listening|thinking|speaking|interrupted|idle" }
{ "type": "error", "message": "..." }
```

## Voice Interrupt Behavior

The app should support two interruption paths:

- button interrupt: immediate, always available
- voice interrupt: gated by local voice activity detection and only active while the assistant is speaking

Voice interrupt should not be a wake word. It should behave like barge-in:

1. Assistant is speaking.
2. Android detects sustained user speech above threshold.
3. Android lowers or stops playback immediately.
4. Android sends `interrupt`.
5. Gateway cancels active TTS/Hermes generation where possible.
6. Android starts collecting the follow-up utterance in the same session.

To avoid false triggers:

- require speech for roughly 300-600 ms before interrupting
- ignore very short bursts
- ignore audio that matches the speaker output if echo cancellation reports playback leakage
- allow a sensitivity setting later: low, normal, high
- keep button interrupt as the reliable fallback

## Android Pieces

Future Android work:

- WebSocket client for streaming voice sessions
- microphone chunk encoder, likely PCM16 at 16 kHz to start
- local VAD for barge-in detection
- playback pipeline that can play streamed audio chunks
- echo cancellation/noise suppression using Android audio effects where available
- streaming UI state separate from current request/response state

Keep the current push-to-talk flow as fallback.

## Gateway Pieces

Future gateway work:

- WebSocket route under `/voice/stream`
- streaming session manager
- profile-scoped Hermes context using the selected profile `HERMES_HOME`
- streaming STT adapter if Hermes/local provider supports it
- token/text streaming from Hermes if available
- streaming or sentence-chunked TTS
- cancellation propagation for STT, Hermes, and TTS

If Hermes does not expose native streaming for a step, the gateway can still improve perceived latency by chunking at boundaries:

- streaming microphone upload to gateway
- final STT result
- Hermes text stream or sentence chunks
- TTS per sentence

## Phased Plan

### Phase 1: Keep Current API, Reduce Latency

- persistent STT worker
- profile prewarm
- avoid unnecessary session recreation
- optional shorter response mode for voice

### Phase 2: Server-Side Text Streaming

- add an experimental gateway stream endpoint
- stream Hermes text deltas when supported
- keep TTS as final full-response audio

### Phase 3: Sentence TTS

- split assistant text into sentence chunks
- synthesize and send each sentence as soon as it is ready
- Android queues streamed audio chunks

### Phase 4: Full Duplex Voice

- WebSocket microphone chunks
- VAD-driven speech start/end
- barge-in interrupt while assistant is speaking
- same-session follow-up without waiting for full playback

## Open Questions

- Which Hermes interface should be the source of truth for native text streaming?
- Can the selected Hermes TTS provider stream audio, or should gateway synthesize sentence chunks?
- Should streaming mode require PCM16, Opus, or Android-native encoded audio?
- How much barge-in sensitivity should be configurable in Settings?
- Should the gateway keep the persistent STT worker per process or per active profile?

## Non-Goals

- No Wear OS.
- No WebRTC unless a future requirement makes low-latency peer media unavoidable.
- No wake word.
- No replacement of the current gateway/app architecture.
- No removal of the stable HTTP request/response mode.
