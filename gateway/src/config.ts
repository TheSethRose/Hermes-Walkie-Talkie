import "dotenv/config";
import { z } from "zod";

const boolFromString = z
  .string()
  .default("false")
  .transform((value) => value.toLowerCase() === "true");

const numberFromString = z
  .string()
  .regex(/^\d+$/)
  .transform(Number);

const envSchema = z.object({
  GATEWAY_HOST: z.string().default("0.0.0.0"),
  GATEWAY_PORT: numberFromString.default("8789"),
  GATEWAY_API_KEY: z.string().min(1).default("change-me"),
  HEALTH_REQUIRES_AUTH: boolFromString.default("true"),
  PUBLIC_BASE_URL: z.string().default(""),
  AUDIO_PUBLIC_BASE_URL: z.string().default(""),
  UPLOAD_DIR: z.string().default("storage/uploads"),
  AUDIO_DIR: z.string().default("storage/audio"),
  MAX_UPLOAD_MB: numberFromString.default("25"),
  STT_PROVIDER: z.enum(["hermes", "openai_compatible"]).default("hermes"),
  STT_BASE_URL: z.string().default(""),
  STT_API_KEY: z.string().default(""),
  STT_MODEL: z.string().default("whisper-1"),
  HERMES_STT_PYTHON: z.string().default("python"),
  TTS_PROVIDER: z.enum(["hermes", "openai_compatible"]).default("hermes"),
  TTS_BASE_URL: z.string().default(""),
  TTS_API_KEY: z.string().default(""),
  TTS_MODEL: z.string().default("tts-1"),
  TTS_VOICE: z.string().default("alloy"),
  HERMES_TTS_PYTHON: z.string().default("python"),
  HERMES_MODE: z.enum(["cli", "http"]).default("cli"),
  HERMES_CLI_COMMAND: z.string().default("hermes"),
  HERMES_CLI_ARGS_TEMPLATE: z.string().default("-z {prompt}"),
  HERMES_AGENT: z.string().default("default"),
  HERMES_TIMEOUT_MS: numberFromString.default("120000"),
  HERMES_HTTP_BASE_URL: z.string().default(""),
  HERMES_HTTP_API_KEY: z.string().default(""),
  HERMES_HTTP_TURN_PATH: z.string().default("/agent/turn"),
  MOCK_MODE: boolFromString.default("false")
});

const env = envSchema.parse(process.env);

export const config = {
  host: env.GATEWAY_HOST,
  port: env.GATEWAY_PORT,
  apiKey: env.GATEWAY_API_KEY,
  healthRequiresAuth: env.HEALTH_REQUIRES_AUTH,
  publicBaseUrl: env.PUBLIC_BASE_URL.replace(/\/$/, ""),
  audioPublicBaseUrl: env.AUDIO_PUBLIC_BASE_URL.replace(/\/$/, ""),
  uploadDir: env.UPLOAD_DIR,
  audioDir: env.AUDIO_DIR,
  maxUploadBytes: env.MAX_UPLOAD_MB * 1024 * 1024,
  sttProvider: env.STT_PROVIDER,
  sttBaseUrl: (env.STT_BASE_URL || "https://api.openai.com/v1").replace(/\/$/, ""),
  sttApiKey: env.STT_API_KEY,
  sttModel: env.STT_MODEL,
  hermesSttPython: env.HERMES_STT_PYTHON,
  ttsProvider: env.TTS_PROVIDER,
  ttsBaseUrl: (env.TTS_BASE_URL || "https://api.openai.com/v1").replace(/\/$/, ""),
  ttsApiKey: env.TTS_API_KEY,
  ttsModel: env.TTS_MODEL,
  ttsVoice: env.TTS_VOICE,
  hermesTtsPython: env.HERMES_TTS_PYTHON,
  hermesMode: env.HERMES_MODE,
  hermesCliCommand: env.HERMES_CLI_COMMAND,
  hermesCliArgsTemplate: env.HERMES_CLI_ARGS_TEMPLATE,
  hermesAgent: env.HERMES_AGENT,
  hermesTimeoutMs: env.HERMES_TIMEOUT_MS,
  hermesHttpBaseUrl: env.HERMES_HTTP_BASE_URL.replace(/\/$/, ""),
  hermesHttpApiKey: env.HERMES_HTTP_API_KEY,
  hermesHttpTurnPath: env.HERMES_HTTP_TURN_PATH.startsWith("/")
    ? env.HERMES_HTTP_TURN_PATH
    : `/${env.HERMES_HTTP_TURN_PATH}`,
  mockMode: env.MOCK_MODE
} as const;

export type AppConfig = typeof config;
