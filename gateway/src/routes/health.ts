import type { FastifyInstance } from "fastify";
import type { AppConfig } from "../config.js";
import { requireAuth } from "../auth.js";

export async function healthRoutes(app: FastifyInstance, config: AppConfig) {
  app.get(
    "/health",
    { preHandler: config.healthRequiresAuth ? requireAuth(config) : undefined },
    async () => ({
      ok: true,
      service: "hermes-voice-gateway",
      mode: config.mockMode ? "mock" : config.hermesMode,
      time: new Date().toISOString()
    })
  );
}
