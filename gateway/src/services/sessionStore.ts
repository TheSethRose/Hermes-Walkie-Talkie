import { randomUUID } from "node:crypto";

export type ResponseMode = "text" | "text_audio";

export type VoiceSession = {
  sessionId: string;
  profileId: string;
  profileName: string;
  agent: string;
  responseMode: ResponseMode;
  ttsVoiceId?: string;
  createdAt: string;
  lastActivityAt: string;
  canceled: boolean;
  turns: VoiceTurn[];
};

export type VoiceTurn = {
  userText: string;
  assistantText: string;
  createdAt: string;
};

export class SessionStore {
  private sessions = new Map<string, VoiceSession>();

  create(profileId: string, profileName: string, responseMode: ResponseMode, ttsVoiceId?: string) {
    const now = new Date().toISOString();
    const session: VoiceSession = {
      sessionId: `sess_${randomUUID()}`,
      profileId,
      profileName,
      agent: profileName,
      responseMode,
      ttsVoiceId,
      createdAt: now,
      lastActivityAt: now,
      canceled: false,
      turns: []
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
      session.lastActivityAt = new Date().toISOString();
    }
    return session ?? null;
  }

  appendTurn(sessionId: string, userText: string, assistantText: string) {
    const session = this.sessions.get(sessionId);
    if (!session) return null;
    const createdAt = new Date().toISOString();
    session.turns.push({ userText, assistantText, createdAt });
    session.lastActivityAt = createdAt;
    session.canceled = false;
    return session;
  }

  reset(sessionId: string) {
    const session = this.sessions.get(sessionId);
    if (!session) return null;
    session.turns = [];
    session.canceled = false;
    session.lastActivityAt = new Date().toISOString();
    return session;
  }

  end(sessionId: string) {
    const session = this.sessions.get(sessionId);
    this.sessions.delete(sessionId);
    return session ?? null;
  }

  getHistory(sessionId: string) {
    return this.sessions.get(sessionId)?.turns ?? [];
  }
}
