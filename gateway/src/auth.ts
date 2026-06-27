import { timingSafeEqual } from "node:crypto";
import type { FastifyReply, FastifyRequest } from "fastify";
import type { AppConfig } from "./config.js";

export function requireAuth(config: AppConfig) {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    if (!isAuthorizedBearer(request.headers.authorization, config)) {
      return reply.code(401).send({ error: "Unauthorized" });
    }
  };
}

export function isAuthorizedBearer(header: string | undefined, config: AppConfig) {
  if (!header?.startsWith("Bearer ")) return false;
  return secureEqual(header.slice("Bearer ".length), config.apiKey);
}

function secureEqual(a: string, b: string) {
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  return left.length === right.length && timingSafeEqual(left, right);
}
