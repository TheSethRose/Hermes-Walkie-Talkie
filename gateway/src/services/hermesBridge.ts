import type { AppConfig } from "../config.js";
import { logger } from "../utils/logger.js";
import { parseArgsTemplate, runCommand } from "../utils/command.js";

export type HermesBridgeRequest = {
  sessionId: string;
  profileId: string;
  hermesHome?: string;
  agent: string;
  transcript: string;
  history: Array<{ userText: string; assistantText: string; createdAt: string }>;
};

export type HermesBridgeResponse = {
  responseText: string;
};

export interface HermesBridge {
  sendTurn(input: HermesBridgeRequest): Promise<HermesBridgeResponse>;
  cancel(sessionId: string): Promise<void>;
}

export function createHermesBridge(config: AppConfig): HermesBridge {
  if (config.mockMode) return new MockHermesBridge();
  if (config.hermesMode === "http") return new HttpHermesBridge(config);
  return new CliHermesBridge(config);
}

class MockHermesBridge implements HermesBridge {
  async sendTurn(input: HermesBridgeRequest) {
    return { responseText: `Hermes mock response received: ${input.transcript}` };
  }

  async cancel() {}
}

class CliHermesBridge implements HermesBridge {
  constructor(private config: AppConfig) {}

  async sendTurn(input: HermesBridgeRequest) {
    const conversation = buildConversationPrompt(input, this.config.hermesMaxHistoryTurns);
    const prompt = this.config.hermesCliArgsTemplate.includes("{conversation}") ? conversation : (
      this.config.hermesContextMode === "gateway_history" ? conversation : input.transcript
    );
    const args = parseArgsTemplate(this.config.hermesCliArgsTemplate, {
      agent: input.agent,
      profileId: input.profileId,
      prompt,
      conversation,
      sessionId: input.sessionId
    });
    logger.info(
      {
        sessionId: input.sessionId,
        profileId: input.profileId,
        agent: input.agent,
        hermesHome: input.hermesHome,
        argsTemplate: this.config.hermesCliArgsTemplate
      },
      "Starting Hermes CLI turn"
    );
    const result = await runCommand(this.config.hermesCliCommand, args, this.config.hermesTimeoutMs, {
      env: {
        ...(input.hermesHome ? { HERMES_HOME: input.hermesHome } : {})
      }
    });

    if (result.timedOut || result.exitCode !== 0) {
      logger.error(
        {
          exitCode: result.exitCode,
          timedOut: result.timedOut,
          stderr: result.stderr.slice(0, 4000)
        },
        "Hermes CLI failed"
      );
      throw new Error("Hermes CLI failed");
    }

    return { responseText: result.stdout };
  }

  async cancel() {
    // ponytail: CLI turns are per-request child processes; no persistent Hermes session to cancel yet.
  }
}

class HttpHermesBridge implements HermesBridge {
  constructor(private config: AppConfig) {}

  async sendTurn(input: HermesBridgeRequest) {
    if (!this.config.hermesHttpBaseUrl) throw new Error("HERMES_HTTP_BASE_URL is required");

    const headers: Record<string, string> = { "Content-Type": "application/json" };
    if (this.config.hermesHttpApiKey) headers.Authorization = `Bearer ${this.config.hermesHttpApiKey}`;

    const response = await fetch(`${this.config.hermesHttpBaseUrl}${this.config.hermesHttpTurnPath}`, {
      method: "POST",
      headers,
      body: JSON.stringify({
        sessionId: input.sessionId,
        profileId: input.profileId,
        hermesHome: input.hermesHome,
        agent: input.agent,
        message: input.transcript,
        history: input.history
      })
    });

    if (!response.ok) {
      logger.error({ status: response.status }, "Hermes HTTP failed");
      throw new Error("Hermes HTTP failed");
    }

    const body = (await response.json()) as { responseText?: unknown };
    if (typeof body.responseText !== "string") throw new Error("Hermes HTTP response missing responseText");
    return { responseText: body.responseText };
  }

  async cancel() {}
}

function buildConversationPrompt(input: HermesBridgeRequest, maxTurns: number) {
  const history = input.history.slice(-maxTurns);
  if (history.length === 0) return input.transcript;

  const previous = history
    .map((turn) => `User: ${turn.userText}\nAssistant: ${turn.assistantText}`)
    .join("\n\n");

  return [
    "You are continuing an existing Hermes agent conversation.",
    "",
    "Previous conversation:",
    previous,
    "",
    "New user message:",
    input.transcript,
    "",
    "Respond as the selected Hermes profile.",
    "",
    "Do not duplicate old responses."
  ].join("\n");
}
