import { createReadStream } from "node:fs";
import type { FastifyInstance } from "fastify";
import type { AppConfig } from "../config.js";
import { requireAuth } from "../auth.js";
import { fileExists, resolveInside } from "../utils/files.js";

export async function audioRoutes(app: FastifyInstance, config: AppConfig) {
  app.get<{ Params: { fileName: string } }>(
    "/audio/:fileName",
    { preHandler: requireAuth(config) },
    async (request, reply) => {
      const { fileName } = request.params;
      if (!/^[a-zA-Z0-9._-]+\.mp3$/.test(fileName)) {
        return reply.code(400).send({ error: "Invalid audio file" });
      }

      const filePath = resolveInside(config.audioDir, fileName);
      if (!(await fileExists(filePath))) return reply.code(404).send({ error: "Audio not found" });

      return reply.type("audio/mpeg").send(createReadStream(filePath));
    }
  );
}
