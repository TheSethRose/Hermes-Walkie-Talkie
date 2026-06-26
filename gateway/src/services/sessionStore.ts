import { randomUUID } from "node:crypto";

export type ResponseMode = "text" | "text_audio";

export type VoiceSession = {
  sessionId: string;
  agent: string;
  responseMode: ResponseMode;
  createdAt: string;
  lastActivityAt: string;
  canceled: boolean;
};

export class SessionStore {
  private sessions = new Map<string, VoiceSession>();

  create(agent: string, responseMode: ResponseMode) {
    const now = new Date().toISOString();
    const session: VoiceSession = {
      sessionId: `sess_${randomUUID()}`,
      agent,
      responseMode,
      createdAt: now,
      lastActivityAt: now,
      canceled: false
    };
    this.sessions.set(session.sessionId, session);
    return session;
  }

  get(sessionId: string) {
    return this.sessions.get(sessionId) ?? null;
  }

  touch(sessionId: string) {
    const session = this.sessions.get(sessionId);
    if (session) session.lastActivityAt = new Date().toISOString();
    return session ?? null;
  }

  cancel(sessionId: string) {
    const session = this.sessions.get(sessionId);
    if (session) {
      session.canceled = true;
      session.lastActivityAt = new Date().toISOString();
    }
    return session ?? null;
  }
}
