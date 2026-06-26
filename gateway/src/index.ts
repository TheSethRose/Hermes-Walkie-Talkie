import Fastify, { type FastifyInstance } from "fastify";
import multipart from "@fastify/multipart";
import { randomUUID } from "node:crypto";
import { config } from "./config.js";
import { audioRoutes } from "./routes/audio.js";
import { healthRoutes } from "./routes/health.js";
import { voiceRoutes } from "./routes/voice.js";
import { SessionStore } from "./services/sessionStore.js";
import { ensureStorageDirs } from "./utils/files.js";
import { loggerOptions } from "./utils/logger.js";

await ensureStorageDirs([config.uploadDir, config.audioDir]);

const app: FastifyInstance = Fastify({
  logger: loggerOptions,
  genReqId: (request) => request.headers["x-request-id"]?.toString() || randomUUID(),
  requestIdHeader: "x-request-id"
});

app.setErrorHandler((error, request, reply) => {
  request.log.error({ err: error }, "Request failed");
  const statusCode = hasStatusCode(error) ? error.statusCode : undefined;
  const status = typeof statusCode === "number" && statusCode >= 400 ? statusCode : 500;
  const message = status === 413 ? "Upload too large" : "Internal server error";
  reply.code(status).send({ error: message });
});

app.addContentTypeParser("application/json", { parseAs: "string" }, (req, body, done) => {
  if (!body || body === "") {
    done(null, {});
    return;
  }
  try {
    done(null, JSON.parse(body.toString()));
  } catch (err: any) {
    err.statusCode = 400;
    done(err);
  }
});

await app.register(multipart, {
  limits: {
    fileSize: config.maxUploadBytes,
    files: 1,
    fields: 8
  }
});

const sessions = new SessionStore();
await healthRoutes(app, config);
await voiceRoutes(app, config, sessions);
await audioRoutes(app, config);

await app.listen({ host: config.host, port: config.port });

function hasStatusCode(error: unknown): error is { statusCode: number } {
  return typeof error === "object" && error !== null && "statusCode" in error;
}
