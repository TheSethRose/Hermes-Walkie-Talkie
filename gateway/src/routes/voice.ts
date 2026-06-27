import { z } from "zod";
import type { FastifyInstance } from "fastify";
import type { AppConfig } from "../config.js";
import { isAuthorizedBearer, requireAuth } from "../auth.js";
import { createHermesBridge } from "../services/hermesBridge.js";
import { isAllowedAudioUpload, savePcm16Wav, saveUpload } from "../services/audioStore.js";
import { SessionStore } from "../services/sessionStore.js";
import type { ProfileStore } from "../services/profileStore.js";
import type { PersistentSttWorker } from "../services/sttWorker.js";
import { transcribeAudio } from "../services/stt.js";
import { listTtsVoices, synthesizeSpeech } from "../services/tts.js";

const sessionSchema = z.object({
  profileId: z.string().min(1).optional(),
  agent: z.string().min(1).optional(),
  responseMode: z.enum(["text", "text_audio"]),
  ttsVoiceId: z.string().min(1).optional()
});

const streamQuerySchema = z.object({
  sessionId: z.string().min(1).optional(),
  profileId: z.string().min(1).optional(),
  agent: z.string().min(1).optional(),
  responseMode: z.enum(["text", "text_audio"]).default("text_audio"),
  ttsVoiceId: z.string().min(1).optional()
});

const ttsVoicesQuerySchema = z.object({
  profileId: z.string().min(1).optional(),
  agent: z.string().min(1).optional()
});

const streamAudioChunkSchema = z.object({
  type: z.literal("audio_chunk"),
  seq: z.number().int().nonnegative(),
  format: z.literal("pcm16"),
  sampleRate: z.literal(16000),
  data: z.string().min(1)
});

const streamControlSchema = z.object({
  type: z.enum(["speech_end", "interrupt", "end_session"])
});

const streamVadDebugSchema = z.object({
  type: z.literal("vad_debug"),
  event: z.string().min(1),
  speaking: z.boolean(),
  rms: z.number().int().nonnegative(),
  bufferedBytes: z.number().int().nonnegative()
});

const streamClientEventSchema = z.union([streamAudioChunkSchema, streamControlSchema, streamVadDebugSchema]);

export async function voiceRoutes(
  app: FastifyInstance,
  config: AppConfig,
  sessions: SessionStore,
  profiles: ProfileStore,
  sttWorker?: PersistentSttWorker
) {
  const hermes = createHermesBridge(config);

  app.get<{ Querystring: { profileId?: string; agent?: string } }>(
    "/tts/voices",
    { preHandler: requireAuth(config) },
    async (request, reply) => {
      const parsed = ttsVoicesQuerySchema.safeParse(request.query);
      if (!parsed.success) return reply.code(400).send({ error: "Invalid TTS voices request" });
      const profile = await profiles.resolve(parsed.data.profileId, parsed.data.agent || config.hermesAgent);
      return listTtsVoices(config, profile);
    }
  );

  app.get(
    "/voice/stream",
    { websocket: true },
    async (socket, request) => {
      if (!isAuthorizedBearer(request.headers.authorization, config)) {
        socket.close(1008, "Unauthorized");
        return;
      }

      const parsedQuery = streamQuerySchema.safeParse(request.query);
      if (!parsedQuery.success) {
        sendStreamEvent(socket, { type: "error", message: "Invalid stream request" });
        socket.close(1008, "Invalid stream request");
        return;
      }
      const streamOptions = parsedQuery.data;

      const profile = await profiles.resolve(streamOptions.profileId, streamOptions.agent || config.hermesAgent);
      const existing = streamOptions.sessionId ? sessions.get(streamOptions.sessionId) : null;
      const session = existing ?? sessions.create(profile.id, profile.name, streamOptions.responseMode, streamOptions.ttsVoiceId);
      const ttsVoiceId = streamOptions.ttsVoiceId || session.ttsVoiceId;
      let generation = 0;
      let chunks: Buffer[] = [];
      let bufferedBytes = 0;
      let processing = false;

      request.log.info(
        { sessionId: session.sessionId, profileId: profile.id, responseMode: streamOptions.responseMode },
        "Streaming voice session opened"
      );

      sendStreamEvent(socket, {
        type: "session",
        sessionId: session.sessionId,
        profileId: session.profileId,
        profileName: session.profileName
      });
      sendStreamEvent(socket, { type: "state", status: "listening" });

      socket.on("message", (raw: Buffer | ArrayBuffer | Buffer[]) => {
        void handleStreamMessage(raw);
      });

      socket.on("close", () => {
        generation += 1;
        chunks = [];
        bufferedBytes = 0;
      });

      async function handleStreamMessage(raw: Buffer | ArrayBuffer | Buffer[]) {
        let body: unknown;
        try {
          body = JSON.parse(Buffer.isBuffer(raw) ? raw.toString("utf8") : raw.toString());
        } catch {
          sendStreamEvent(socket, { type: "error", message: "Invalid JSON event" });
          return;
        }

        const event = streamClientEventSchema.safeParse(body);
        if (!event.success) {
          sendStreamEvent(socket, { type: "error", message: "Invalid stream event" });
          return;
        }

        if (event.data.type === "audio_chunk") {
          if (processing) return;
          const chunk = Buffer.from(event.data.data, "base64");
          bufferedBytes += chunk.length;
          if (bufferedBytes > config.maxUploadBytes) {
            chunks = [];
            bufferedBytes = 0;
            sendStreamEvent(socket, { type: "error", message: "Audio stream too large" });
            return;
          }
          chunks.push(chunk);
          return;
        }

        if (event.data.type === "vad_debug") {
          request.log.info(
            {
              sessionId: session.sessionId,
              event: event.data.event,
              speaking: event.data.speaking,
              rms: event.data.rms,
              bufferedBytes: event.data.bufferedBytes,
              serverBufferedBytes: bufferedBytes,
              processing
            },
            "Always listening VAD debug"
          );
          return;
        }

        if (event.data.type === "speech_end") {
          if (processing || chunks.length === 0) {
            request.log.info(
              { sessionId: session.sessionId, processing, chunks: chunks.length, bufferedBytes },
              "Streaming speech_end ignored"
            );
            return;
          }
          const pcm = Buffer.concat(chunks);
          request.log.info({ sessionId: session.sessionId, bytes: pcm.length }, "Streaming voice utterance received");
          chunks = [];
          bufferedBytes = 0;
          await processStreamTurn(pcm, generation);
          return;
        }

        if (event.data.type === "interrupt") {
          generation += 1;
          chunks = [];
          bufferedBytes = 0;
          await hermes.cancel(session.sessionId);
          sendStreamEvent(socket, { type: "state", status: "interrupted" });
          sendStreamEvent(socket, { type: "state", status: "listening" });
          return;
        }

        generation += 1;
        sessions.end(session.sessionId);
        await hermes.cancel(session.sessionId);
        socket.close(1000, "Session ended");
      }

      async function processStreamTurn(pcm: Buffer, turnGeneration: number) {
        processing = true;
        sendStreamEvent(socket, { type: "state", status: "thinking" });

        try {
          const saved = await savePcm16Wav(pcm, 16000, config);
          const transcript = await transcribeAudio(saved.filePath, saved.fileName, config, profile, sttWorker);
          if (turnGeneration !== generation) return;

          request.log.info({ sessionId: session.sessionId, transcriptChars: transcript.length }, "Streaming voice turn transcribed");
          sendStreamEvent(socket, { type: "final_transcript", text: transcript });
          const hermesResponse = await hermes.sendTurn({
            sessionId: session.sessionId,
            profileId: profile.id,
            hermesHome: profile.hermesHome,
            agent: profile.name,
            transcript,
            history: sessions.getHistory(session.sessionId)
          });
          if (turnGeneration !== generation) return;

          sessions.appendTurn(session.sessionId, transcript, hermesResponse.responseText);
          request.log.info({ sessionId: session.sessionId, responseChars: hermesResponse.responseText.length }, "Streaming Hermes response received");
          sendStreamEvent(socket, { type: "assistant_text_final", text: hermesResponse.responseText });

          if (streamOptions.responseMode === "text_audio") {
            sendStreamEvent(socket, { type: "state", status: "speaking" });
            const audio = await synthesizeSpeech(hermesResponse.responseText, config, profile, ttsVoiceId);
            if (turnGeneration !== generation) return;
            if (audio?.audioUrl) sendStreamEvent(socket, { type: "audio_url", url: audio.audioUrl });
          }

          sendStreamEvent(socket, { type: "state", status: "listening" });
        } catch (error) {
          request.log.error({ err: error }, "Streaming voice turn failed");
          sendStreamEvent(socket, { type: "error", message: "Streaming voice turn failed" });
          sendStreamEvent(socket, { type: "state", status: "listening" });
        } finally {
          processing = false;
        }
      }
    }
  );

  app.post(
    "/voice/session",
    { preHandler: requireAuth(config) },
    async (request, reply) => {
      const parsed = sessionSchema.safeParse(request.body);
      if (!parsed.success) return reply.code(400).send({ error: "Invalid session request" });

      const profile = await profiles.resolve(parsed.data.profileId, parsed.data.agent || config.hermesAgent);
      const session = sessions.create(profile.id, profile.name, parsed.data.responseMode, parsed.data.ttsVoiceId);
      if (config.gatewayDebug) {
        request.log.info(
          {
            requestedProfileId: parsed.data.profileId,
            requestedAgent: parsed.data.agent,
            resolvedProfileId: profile.id,
            resolvedProfileName: profile.name,
            hermesHome: profile.hermesHome,
            responseMode: parsed.data.responseMode,
            ttsVoiceId: parsed.data.ttsVoiceId,
            sessionId: session.sessionId
          },
          "Created voice session"
        );
      }
      return {
        sessionId: session.sessionId,
        profileId: session.profileId,
        profileName: session.profileName,
        agent: session.agent,
        responseMode: session.responseMode
      };
    }
  );

  app.post<{ Params: { sessionId: string } }>(
    "/voice/session/:sessionId/turn",
    { preHandler: requireAuth(config) },
    async (request, reply) => {
      const session = sessions.get(request.params.sessionId);
      if (!session || session.canceled) return reply.code(404).send({ error: "Session not found" });

      const fields: Record<string, string> = {};
      let saved: Awaited<ReturnType<typeof saveUpload>> | null = null;
      let sawAudioFile = false;

      for await (const part of request.parts()) {
        if (part.type === "field" && typeof part.value === "string") {
          fields[part.fieldname] = part.value;
          continue;
        }
        if (part.type === "file" && part.fieldname === "audio" && !saved) {
          sawAudioFile = true;
          if (isAllowedAudioUpload(part)) {
            saved = await saveUpload(part, config);
            continue;
          }
        }
        if (part.type === "file") part.file.resume();
      }

      if (sawAudioFile && !saved) return reply.code(400).send({ error: "Invalid audio upload" });
      if (!saved) return reply.code(400).send({ error: "Audio file is required" });

      const parsed = sessionSchema.safeParse({
        profileId: fields.profileId || session.profileId,
        agent: fields.agent || session.agent,
        responseMode: fields.responseMode || session.responseMode,
        ttsVoiceId: fields.ttsVoiceId || session.ttsVoiceId
      });
      if (!parsed.success) return reply.code(400).send({ error: "Invalid turn request" });

      const profile = await profiles.resolve(parsed.data.profileId, parsed.data.agent);

      sessions.touch(session.sessionId);
      if (config.gatewayDebug) {
        request.log.info(
          {
            sessionId: session.sessionId,
            sessionProfileId: session.profileId,
            requestProfileId: parsed.data.profileId,
            requestAgent: parsed.data.agent,
            resolvedProfileId: profile.id,
            resolvedProfileName: profile.name,
            hermesHome: profile.hermesHome,
            responseMode: parsed.data.responseMode,
            ttsVoiceId: parsed.data.ttsVoiceId,
            uploadFileName: saved.fileName
          },
          "Processing voice turn"
        );
      }

      let transcript: string;
      try {
        transcript = await transcribeAudio(saved.filePath, saved.fileName, config, profile, sttWorker);
        if (config.gatewayDebug) request.log.info({ sessionId: session.sessionId, transcriptChars: transcript.length }, "Voice turn transcribed");
      } catch (error) {
        request.log.error({ err: error }, "Transcription failed");
        return reply.code(502).send({ error: "Transcription failed" });
      }

      let hermesResponse;
      try {
        hermesResponse = await hermes.sendTurn({
          sessionId: session.sessionId,
          profileId: profile.id,
          hermesHome: profile.hermesHome,
          agent: profile.name,
          transcript,
          history: sessions.getHistory(session.sessionId)
        });
        if (config.gatewayDebug) {
          request.log.info(
            { sessionId: session.sessionId, responseChars: hermesResponse.responseText.length },
            "Hermes agent response received"
          );
        }
      } catch (error) {
        request.log.error({ err: error }, "Hermes agent failed");
        return reply.code(502).send({ error: "Hermes agent failed" });
      }

      sessions.appendTurn(session.sessionId, transcript, hermesResponse.responseText);

      if (parsed.data.responseMode === "text") {
        return { transcript, responseText: hermesResponse.responseText };
      }

      try {
        const audio = await synthesizeSpeech(hermesResponse.responseText, config, profile, parsed.data.ttsVoiceId);
        if (config.gatewayDebug) request.log.info({ sessionId: session.sessionId, audioUrl: audio?.audioUrl ?? null }, "Voice turn TTS completed");
        return {
          transcript,
          responseText: hermesResponse.responseText,
          audioUrl: audio?.audioUrl ?? null,
          ...(audio ? {} : { warning: "TTS failed; returned text only" })
        };
      } catch (error) {
        request.log.error({ err: error }, "TTS failed");
        return {
          transcript,
          responseText: hermesResponse.responseText,
          audioUrl: null,
          warning: "TTS failed; returned text only"
        };
      }
    }
  );

  app.post<{ Params: { sessionId: string } }>(
    "/voice/session/:sessionId/cancel",
    { preHandler: requireAuth(config) },
    async (request, reply) => {
      sessions.cancel(request.params.sessionId);
      await hermes.cancel(request.params.sessionId);
      return reply.code(204).send();
    }
  );

  app.post<{ Params: { sessionId: string } }>(
    "/voice/session/:sessionId/reset",
    { preHandler: requireAuth(config) },
    async (request, reply) => {
      const session = sessions.reset(request.params.sessionId);
      if (!session) return reply.code(404).send({ error: "Session not found" });
      return reply.code(204).send();
    }
  );

  app.post<{ Params: { sessionId: string } }>(
    "/voice/session/:sessionId/end",
    { preHandler: requireAuth(config) },
    async (request, reply) => {
      const session = sessions.end(request.params.sessionId);
      if (!session) return reply.code(404).send({ error: "Session not found" });
      await hermes.cancel(request.params.sessionId);
      return reply.code(204).send();
    }
  );
}

function sendStreamEvent(socket: { readyState: number; send: (data: string) => void }, event: Record<string, unknown>) {
  if (socket.readyState === 1) socket.send(JSON.stringify(event));
}
