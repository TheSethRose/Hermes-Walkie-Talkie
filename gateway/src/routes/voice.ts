import { z } from "zod";
import type { FastifyInstance } from "fastify";
import type { AppConfig } from "../config.js";
import { requireAuth } from "../auth.js";
import { createHermesBridge } from "../services/hermesBridge.js";
import { isAllowedAudioUpload, saveUpload } from "../services/audioStore.js";
import { SessionStore } from "../services/sessionStore.js";
import type { ProfileStore } from "../services/profileStore.js";
import type { PersistentSttWorker } from "../services/sttWorker.js";
import { transcribeAudio } from "../services/stt.js";
import { synthesizeSpeech } from "../services/tts.js";

const sessionSchema = z.object({
  profileId: z.string().min(1).optional(),
  agent: z.string().min(1).optional(),
  responseMode: z.enum(["text", "text_audio"])
});

export async function voiceRoutes(
  app: FastifyInstance,
  config: AppConfig,
  sessions: SessionStore,
  profiles: ProfileStore,
  sttWorker?: PersistentSttWorker
) {
  const hermes = createHermesBridge(config);

  app.post(
    "/voice/session",
    { preHandler: requireAuth(config) },
    async (request, reply) => {
      const parsed = sessionSchema.safeParse(request.body);
      if (!parsed.success) return reply.code(400).send({ error: "Invalid session request" });

      const profile = await profiles.resolve(parsed.data.profileId, parsed.data.agent || config.hermesAgent);
      const session = sessions.create(profile.id, profile.name, parsed.data.responseMode);
      if (config.gatewayDebug) {
        request.log.info(
          {
            requestedProfileId: parsed.data.profileId,
            requestedAgent: parsed.data.agent,
            resolvedProfileId: profile.id,
            resolvedProfileName: profile.name,
            hermesHome: profile.hermesHome,
            responseMode: parsed.data.responseMode,
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
        responseMode: fields.responseMode || session.responseMode
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
        const audio = await synthesizeSpeech(hermesResponse.responseText, config, profile);
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
