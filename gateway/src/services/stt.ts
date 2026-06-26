import { readFile } from "node:fs/promises";
import type { AppConfig } from "../config.js";
import { runCommand } from "../utils/command.js";
import { logger } from "../utils/logger.js";

export async function transcribeAudio(filePath: string, fileName: string, config: AppConfig) {
  if (config.mockMode) return "Mock transcript from uploaded audio.";
  if (config.sttProvider === "hermes") return transcribeWithHermes(filePath, config);
  if (!config.sttApiKey) throw new Error("STT_API_KEY is required");

  const form = new FormData();
  form.append("model", config.sttModel);
  form.append("file", new Blob([await readFile(filePath)]), fileName);

  const response = await fetch(`${config.sttBaseUrl}/audio/transcriptions`, {
    method: "POST",
    headers: { Authorization: `Bearer ${config.sttApiKey}` },
    body: form
  });

  if (!response.ok) throw new Error(`STT provider returned ${response.status}`);

  const body = (await response.json()) as { text?: unknown };
  if (typeof body.text !== "string") throw new Error("STT provider response missing text");
  return body.text;
}

async function transcribeWithHermes(filePath: string, config: AppConfig) {
  const script = [
    "import json, sys",
    "from tools.transcription_tools import transcribe_audio",
    "result = transcribe_audio(sys.argv[1])",
    "print(json.dumps(result))"
  ].join("\n");

  const result = await runCommand(
    config.hermesSttPython,
    ["-c", script, filePath],
    config.hermesTimeoutMs
  );

  if (result.timedOut || result.exitCode !== 0) {
    logger.error(
      { exitCode: result.exitCode, timedOut: result.timedOut, stderr: result.stderr.slice(0, 4000) },
      "Hermes STT failed"
    );
    throw new Error("Hermes STT failed");
  }

  const body = JSON.parse(result.stdout) as { success?: unknown; transcript?: unknown; error?: unknown };
  if (body.success !== true || typeof body.transcript !== "string") {
    logger.error({ error: body.error }, "Hermes STT returned failure");
    throw new Error("Hermes STT returned failure");
  }

  return body.transcript;
}
