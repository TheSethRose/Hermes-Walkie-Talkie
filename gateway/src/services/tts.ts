import { randomUUID } from "node:crypto";
import path from "node:path";
import type { AppConfig } from "../config.js";
import type { HermesProfile } from "./profileStore.js";
import { saveMp3 } from "./audioStore.js";
import { runCommand } from "../utils/command.js";
import { logger } from "../utils/logger.js";

export async function synthesizeSpeech(text: string, config: AppConfig, profile?: HermesProfile) {
  if (config.mockMode) return null;
  if (config.ttsProvider === "hermes") return synthesizeWithHermes(text, config, profile);
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

async function synthesizeWithHermes(text: string, config: AppConfig, profile?: HermesProfile) {
  const fileName = `${randomUUID()}.mp3`;
  const filePath = path.join(config.audioDir, fileName);
  const script = [
    "import json, os, sys",
    "from hermes_cli.env_loader import load_hermes_dotenv",
    "load_hermes_dotenv()",
    "from hermes_cli.config import get_config_path, get_env_path",
    "from tools import tts_tool as tts",
    "try:",
    "    tts_config = tts._load_tts_config()",
    "    diagnostic = {",
    "        'hermes_home': os.environ.get('HERMES_HOME'),",
    "        'config_path': str(get_config_path()),",
    "        'config_exists': get_config_path().exists(),",
    "        'env_path': str(get_env_path()),",
    "        'env_exists': get_env_path().exists(),",
    "        'provider_config': tts_config.get('provider'),",
    "        'provider_resolved': tts._get_provider(tts_config),",
    "    }",
    "    print(json.dumps({'diagnostic': diagnostic}), file=sys.stderr)",
    "except Exception as exc:",
    "    print(json.dumps({'diagnostic_error': str(exc)}), file=sys.stderr)",
    "result = tts.text_to_speech_tool(text=sys.argv[1], output_path=sys.argv[2])",
    "print(result if isinstance(result, str) else json.dumps(result))"
  ].join("\n");

  logger.info(
    {
      profileId: profile?.id,
      profileName: profile?.name,
      hermesHome: profile?.hermesHome,
      textChars: text.length,
      outputFile: fileName,
      hermesPython: config.hermesTtsPython,
      hermesPythonPath: config.hermesPythonPath
    },
    "Starting Hermes TTS"
  );

  const result = await runCommand(
    config.hermesTtsPython,
    ["-c", script, text, filePath],
    config.hermesTimeoutMs,
    { env: hermesVoiceEnv(config, profile) }
  );

  if (result.timedOut || result.exitCode !== 0) {
    logger.error(
      { exitCode: result.exitCode, timedOut: result.timedOut, stderr: result.stderr.slice(0, 4000) },
      "Hermes TTS failed"
    );
    throw new Error("Hermes TTS failed");
  }

  if (result.stderr.trim()) {
    logger.info({ stderr: result.stderr.slice(0, 4000) }, "Hermes TTS diagnostics");
  }

  const body = JSON.parse(result.stdout.split("\n").at(-1) || "{}") as { success?: unknown; error?: unknown };
  if (body.success !== true) {
    logger.error({ error: body.error, stderr: result.stderr.slice(0, 4000) }, "Hermes TTS returned failure");
    throw new Error("Hermes TTS returned failure");
  }

  const baseUrl = config.audioPublicBaseUrl || config.publicBaseUrl;
  const audio = {
    fileName,
    filePath,
    audioUrl: baseUrl ? `${baseUrl}/audio/${fileName}` : `/audio/${fileName}`
  };
  logger.info({ fileName, audioUrl: audio.audioUrl }, "Hermes TTS succeeded");
  return audio;
}

function hermesVoiceEnv(config: AppConfig, profile?: HermesProfile) {
  return {
    PYTHONPATH: config.hermesPythonPath,
    ...(profile?.hermesHome ? { HERMES_HOME: profile.hermesHome } : {})
  };
}
