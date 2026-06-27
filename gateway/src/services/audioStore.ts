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

export async function savePcm16Wav(pcm: Buffer, sampleRate: number, config: AppConfig) {
  const fileName = `${randomUUID()}.wav`;
  const filePath = path.join(config.uploadDir, fileName);
  const header = Buffer.alloc(44);
  const byteRate = sampleRate * 2;

  header.write("RIFF", 0);
  header.writeUInt32LE(36 + pcm.length, 4);
  header.write("WAVE", 8);
  header.write("fmt ", 12);
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20);
  header.writeUInt16LE(1, 22);
  header.writeUInt32LE(sampleRate, 24);
  header.writeUInt32LE(byteRate, 28);
  header.writeUInt16LE(2, 32);
  header.writeUInt16LE(16, 34);
  header.write("data", 36);
  header.writeUInt32LE(pcm.length, 40);

  await writeFile(filePath, Buffer.concat([header, pcm]));
  return { fileName, filePath, mimetype: "audio/wav" };
}
