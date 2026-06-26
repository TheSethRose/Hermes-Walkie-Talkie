import { randomUUID } from "node:crypto";
import { createWriteStream } from "node:fs";
import { writeFile } from "node:fs/promises";
import path from "node:path";
import { pipeline } from "node:stream/promises";
import type { MultipartFile } from "@fastify/multipart";
import type { AppConfig } from "../config.js";
import { safeBaseName } from "../utils/files.js";

const audioTypes = new Set(["audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac", "audio/mpeg", "audio/wav"]);

export function isAllowedAudioUpload(file: MultipartFile) {
  const name = file.filename.toLowerCase();
  return audioTypes.has(file.mimetype) || /\.(m4a|mp3|wav|aac)$/.test(name);
}

export async function saveUpload(file: MultipartFile, config: AppConfig) {
  const ext = path.extname(safeBaseName(file.filename || "audio.m4a")) || ".m4a";
  const fileName = `${randomUUID()}${ext}`;
  const filePath = path.join(config.uploadDir, fileName);
  await pipeline(file.file, createWriteStream(filePath));
  return { fileName, filePath, mimetype: file.mimetype };
}

export async function saveMp3(bytes: ArrayBuffer, config: AppConfig) {
  const fileName = `${randomUUID()}.mp3`;
  const filePath = path.join(config.audioDir, fileName);
  await writeFile(filePath, Buffer.from(bytes));
  const baseUrl = config.audioPublicBaseUrl || config.publicBaseUrl;
  return {
    fileName,
    filePath,
    audioUrl: baseUrl ? `${baseUrl}/audio/${fileName}` : `/audio/${fileName}`
  };
}
