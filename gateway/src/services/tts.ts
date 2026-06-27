import { randomUUID } from "node:crypto";
import path from "node:path";
import type { AppConfig } from "../config.js";
import type { HermesProfile } from "./profileStore.js";
import { saveMp3 } from "./audioStore.js";
import { runCommand } from "../utils/command.js";
import { logger } from "../utils/logger.js";

export type TtsVoice = {
  id: string;
  name: string;
  provider: string;
  locale?: string;
  gender?: string;
};

export async function listTtsVoices(config: AppConfig, profile?: HermesProfile) {
  if (config.mockMode) return { provider: "mock", defaultVoiceId: "", voices: [] as TtsVoice[] };
  if (config.ttsProvider === "openai_compatible") {
    return {
      provider: "openai_compatible",
      defaultVoiceId: config.ttsVoice,
      voices: [{ id: config.ttsVoice, name: config.ttsVoice, provider: "openai_compatible" }]
    };
  }
  return listHermesTtsVoices(config, profile);
}

export async function synthesizeSpeech(text: string, config: AppConfig, profile?: HermesProfile, ttsVoiceId?: string) {
  if (config.mockMode) return null;
  if (config.ttsProvider === "hermes") return synthesizeWithHermes(text, config, profile, ttsVoiceId);
  if (!config.ttsApiKey) throw new Error("TTS_API_KEY is required");

  const response = await fetch(`${config.ttsBaseUrl}/audio/speech`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${config.ttsApiKey}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      model: config.ttsModel,
      voice: ttsVoiceId || config.ttsVoice,
      input: text
    })
  });

  if (!response.ok) throw new Error(`TTS provider returned ${response.status}`);
  return saveMp3(await response.arrayBuffer(), config);
}

async function listHermesTtsVoices(config: AppConfig, profile?: HermesProfile) {
  const script = [
    "import asyncio, json",
    "from hermes_cli.env_loader import load_hermes_dotenv",
    "load_hermes_dotenv()",
    "from tools import tts_tool as tts",
    "cfg = tts._load_tts_config()",
    "provider = tts._get_provider(cfg)",
    "section = cfg.get(provider, {}) if isinstance(cfg, dict) else {}",
    "voice_key = 'voice_id' if provider in {'elevenlabs', 'minimax', 'mistral', 'xai'} else 'voice'",
    "default_voice = section.get(voice_key, '') if isinstance(section, dict) else ''",
    "voices = []",
    "if provider == 'edge':",
    "    edge_tts = tts._import_edge_tts()",
    "    rows = asyncio.run(edge_tts.list_voices())",
    "    def is_good_english_voice(row):",
    "        short_name = row.get('ShortName', '')",
    "        friendly_name = row.get('FriendlyName', '')",
    "        return (",
    "            row.get('Locale', '').startswith('en-')",
    "            and row.get('Status') == 'GA'",
    "            and short_name.endswith('Neural')",
    "            and 'Multilingual' not in short_name",
    "            and 'Preview' not in friendly_name",
    "        )",
    "    voices = [{",
    "        'id': row.get('ShortName', ''),",
    "        'name': row.get('FriendlyName') or row.get('ShortName', ''),",
    "        'provider': provider,",
    "        'locale': row.get('Locale'),",
    "        'gender': row.get('Gender'),",
    "    } for row in rows if row.get('ShortName') and is_good_english_voice(row)]",
    "elif default_voice:",
    "    voices = [{'id': default_voice, 'name': default_voice, 'provider': provider}]",
    "print(json.dumps({'provider': provider, 'defaultVoiceId': default_voice, 'voices': voices}))"
  ].join("\n");

  const result = await runCommand(
    config.hermesTtsPython,
    ["-c", script],
    config.hermesTimeoutMs,
    { env: hermesVoiceEnv(config, profile) }
  );

  if (result.timedOut || result.exitCode !== 0) {
    logger.error(
      { exitCode: result.exitCode, timedOut: result.timedOut, stderr: result.stderr.slice(0, 4000) },
      "Hermes TTS voice discovery failed"
    );
    throw new Error("Hermes TTS voice discovery failed");
  }

  const body = JSON.parse(result.stdout || "{}") as { provider?: unknown; defaultVoiceId?: unknown; voices?: unknown };
  const voices = Array.isArray(body.voices)
    ? body.voices.flatMap((voice) => {
      if (typeof voice !== "object" || voice === null) return [];
      const record = voice as Record<string, unknown>;
      const id = typeof record.id === "string" ? record.id : "";
      if (!id) return [];
      return [{
        id,
        name: typeof record.name === "string" && record.name ? record.name : id,
        provider: typeof record.provider === "string" && record.provider ? record.provider : "hermes",
        locale: typeof record.locale === "string" ? record.locale : undefined,
        gender: typeof record.gender === "string" ? record.gender : undefined
      }];
    })
    : [];

  return {
    provider: typeof body.provider === "string" ? body.provider : "hermes",
    defaultVoiceId: typeof body.defaultVoiceId === "string" ? body.defaultVoiceId : "",
    voices
  };
}

async function synthesizeWithHermes(text: string, config: AppConfig, profile?: HermesProfile, ttsVoiceId?: string) {
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
    "voice_id = sys.argv[3].strip() if len(sys.argv) > 3 else ''",
    "if voice_id:",
    "    original_load_tts_config = tts._load_tts_config",
    "    def load_tts_config_with_voice():",
    "        cfg = original_load_tts_config()",
    "        provider = tts._get_provider(cfg)",
    "        voice_key = 'voice_id' if provider in {'elevenlabs', 'minimax', 'mistral', 'xai'} else 'voice'",
    "        section = cfg.setdefault(provider, {})",
    "        if isinstance(section, dict):",
    "            section[voice_key] = voice_id",
    "        return cfg",
    "    tts._load_tts_config = load_tts_config_with_voice",
    "result = tts.text_to_speech_tool(text=sys.argv[1], output_path=sys.argv[2])",
    "print(result if isinstance(result, str) else json.dumps(result))"
  ].join("\n");

  if (config.gatewayDebug) {
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
  }

  const result = await runCommand(
    config.hermesTtsPython,
    ["-c", script, text, filePath, ttsVoiceId || ""],
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

  if (config.gatewayDebug && result.stderr.trim()) {
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
  if (config.gatewayDebug) logger.info({ fileName, audioUrl: audio.audioUrl }, "Hermes TTS succeeded");
  return audio;
}

function hermesVoiceEnv(config: AppConfig, profile?: HermesProfile) {
  return {
    PYTHONPATH: config.hermesPythonPath,
    ...(profile?.hermesHome ? { HERMES_HOME: profile.hermesHome } : {})
  };
}
