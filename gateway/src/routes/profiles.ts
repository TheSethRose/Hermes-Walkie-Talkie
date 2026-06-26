import type { FastifyInstance } from "fastify";
import type { AppConfig } from "../config.js";
import { requireAuth } from "../auth.js";
import { publicProfile, type ProfileStore } from "../services/profileStore.js";

export async function profileRoutes(app: FastifyInstance, config: AppConfig, profiles: ProfileStore) {
  app.get<{ Querystring: { refresh?: string } }>(
    "/profiles",
    { preHandler: requireAuth(config) },
    async (request) => {
      const result = await profiles.list(request.query.refresh === "true");
      return {
        profiles: result.profiles.map(publicProfile),
        defaultProfileId: result.defaultProfileId
      };
    }
  );
}
