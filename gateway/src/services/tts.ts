import { randomUUID } from "node:crypto";
import path from "node:path";
import type { AppConfig } from "../config.js";
import { saveMp3 } from "./audioStore.js";
import { runCommand } from "../utils/command.js";
import { logger } from "../utils/logger.js";

export async function synthesizeSpeech(text: string, config: AppConfig) {
  if (config.mockMode) return null;
  if (config.ttsProvider === "hermes") return synthesizeWithHermes(text, config);
  if (!config.ttsApiKey) throw new Error("TTS_API_KEY is required");

  const response = await fetch(`${config.ttsBaseUrl}/audio/speech`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${config.ttsApiKey}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      model: config.ttsModel,
      voice: config.ttsVoice,
      input: text
    })
  });

  if (!response.ok) throw new Error(`TTS provider returned ${response.status}`);
  return saveMp3(await response.arrayBuffer(), config);
}

async function synthesizeWithHermes(text: string, config: AppConfig) {
  const fileName = `${randomUUID()}.mp3`;
  const filePath = path.join(config.audioDir, fileName);
  const script = [
    "import json, sys",
    "from tools.tts_tool import text_to_speech_tool",
    "print(text_to_speech_tool(text=sys.argv[1], output_path=sys.argv[2]))"
  ].join("\n");

  const result = await runCommand(
    config.hermesTtsPython,
    ["-c", script, text, filePath],
    config.hermesTimeoutMs
  );

  if (result.timedOut || result.exitCode !== 0) {
    logger.error(
      { exitCode: result.exitCode, timedOut: result.timedOut, stderr: result.stderr.slice(0, 4000) },
      "Hermes TTS failed"
    );
    throw new Error("Hermes TTS failed");
  }

  const body = JSON.parse(result.stdout.split("\n").at(-1) || "{}") as { success?: unknown; error?: unknown };
  if (body.success !== true) {
    logger.error({ error: body.error }, "Hermes TTS returned failure");
    throw new Error("Hermes TTS returned failure");
  }

  const baseUrl = config.audioPublicBaseUrl || config.publicBaseUrl;
  return {
    fileName,
    filePath,
    audioUrl: baseUrl ? `${baseUrl}/audio/${fileName}` : `/audio/${fileName}`
  };
}
