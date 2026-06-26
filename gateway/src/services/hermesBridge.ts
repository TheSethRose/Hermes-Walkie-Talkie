import type { AppConfig } from "../config.js";
import { logger } from "../utils/logger.js";
import { parseArgsTemplate, runCommand } from "../utils/command.js";

export type HermesBridgeRequest = {
  sessionId: string;
  agent: string;
  transcript: string;
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
    const args = parseArgsTemplate(this.config.hermesCliArgsTemplate, {
      agent: input.agent,
      prompt: input.transcript,
      sessionId: input.sessionId
    });
    const result = await runCommand(this.config.hermesCliCommand, args, this.config.hermesTimeoutMs);

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
        agent: input.agent,
        message: input.transcript
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
