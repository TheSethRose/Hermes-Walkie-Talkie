import { readFile } from "node:fs/promises";
import type { AppConfig } from "../config.js";
import type { HermesProfile } from "./profileStore.js";
import type { PersistentSttWorker } from "./sttWorker.js";
import { runCommand } from "../utils/command.js";
import { logger } from "../utils/logger.js";

export async function transcribeAudio(
  filePath: string,
  fileName: string,
  config: AppConfig,
  profile?: HermesProfile,
  worker?: PersistentSttWorker
) {
  if (config.mockMode) return "Mock transcript from uploaded audio.";
  if (config.sttProvider === "hermes" && worker) return transcribeWithHermesWorker(filePath, profile, worker);
  if (config.sttProvider === "hermes") return transcribeWithHermes(filePath, config, profile);
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

async function transcribeWithHermesWorker(filePath: string, profile: HermesProfile | undefined, worker: PersistentSttWorker) {
  if (worker.debugEnabled) {
    logger.info({ profileId: profile?.id, profileName: profile?.name, hermesHome: profile?.hermesHome }, "Starting Hermes STT worker request");
  }
  const body = await worker.transcribe(filePath, profile);
  if (body.success !== true || typeof body.transcript !== "string") {
    logger.error({ error: body.error }, "Hermes STT worker returned failure");
    throw new Error("Hermes STT returned failure");
  }
  if (worker.debugEnabled) logger.info({ providerOutput: body.success, transcriptChars: body.transcript.length }, "Hermes STT worker succeeded");
  return body.transcript;
}

async function transcribeWithHermes(filePath: string, config: AppConfig, profile?: HermesProfile) {
  const script = [
    "import json, os, sys",
    "from hermes_cli.env_loader import load_hermes_dotenv",
    "load_hermes_dotenv()",
    "from hermes_cli.config import get_config_path, get_env_path",
    "from tools import transcription_tools as stt",
    "try:",
    "    stt_config = stt._load_stt_config()",
    "    diagnostic = {",
    "        'hermes_home': os.environ.get('HERMES_HOME'),",
    "        'config_path': str(get_config_path()),",
    "        'config_exists': get_config_path().exists(),",
    "        'env_path': str(get_env_path()),",
    "        'env_exists': get_env_path().exists(),",
    "        'provider_config': stt_config.get('provider'),",
    "        'provider_resolved': stt._get_provider(stt_config),",
    "        'has_faster_whisper': bool(getattr(stt, '_HAS_FASTER_WHISPER', False)),",
    "        'has_local_command': stt._has_local_command(),",
    "        'has_whisper_binary': bool(stt._find_whisper_binary()),",
    "    }",
    "    print(json.dumps({'diagnostic': diagnostic}), file=sys.stderr)",
    "except Exception as exc:",
    "    print(json.dumps({'diagnostic_error': str(exc)}), file=sys.stderr)",
    "result = stt.transcribe_audio(sys.argv[1])",
    "print(json.dumps(result))"
  ].join("\n");

  if (config.gatewayDebug) {
    logger.info(
      {
        profileId: profile?.id,
        profileName: profile?.name,
        hermesHome: profile?.hermesHome,
        hermesPython: config.hermesSttPython,
        hermesPythonPath: config.hermesPythonPath
      },
      "Starting Hermes STT"
    );
  }

  const result = await runCommand(
    config.hermesSttPython,
    ["-c", script, filePath],
    config.hermesTimeoutMs,
    { env: hermesVoiceEnv(config, profile) }
  );

  if (result.timedOut || result.exitCode !== 0) {
    logger.error(
      { exitCode: result.exitCode, timedOut: result.timedOut, stderr: result.stderr.slice(0, 4000) },
      "Hermes STT failed"
    );
    throw new Error("Hermes STT failed");
  }

  if (config.gatewayDebug && result.stderr.trim()) {
    logger.info({ stderr: result.stderr.slice(0, 4000) }, "Hermes STT diagnostics");
  }

  const body = JSON.parse(result.stdout) as { success?: unknown; transcript?: unknown; error?: unknown };
  if (body.success !== true || typeof body.transcript !== "string") {
    logger.error({ error: body.error, stderr: result.stderr.slice(0, 4000) }, "Hermes STT returned failure");
    throw new Error("Hermes STT returned failure");
  }

  if (config.gatewayDebug) logger.info({ providerOutput: body.success, transcriptChars: body.transcript.length }, "Hermes STT succeeded");
  return body.transcript;
}

function hermesVoiceEnv(config: AppConfig, profile?: HermesProfile) {
  return {
    PYTHONPATH: config.hermesPythonPath,
    ...(profile?.hermesHome ? { HERMES_HOME: profile.hermesHome } : {})
  };
}
