import { mkdir, stat } from "node:fs/promises";
import path from "node:path";

export async function ensureDir(dir: string) {
  await mkdir(dir, { recursive: true });
}

export async function ensureStorageDirs(dirs: string[]) {
  await Promise.all(dirs.map(ensureDir));
}

export function safeBaseName(fileName: string) {
  return path.basename(fileName).replace(/[^a-zA-Z0-9._-]/g, "_");
}

export function resolveInside(root: string, fileName: string) {
  const rootPath = path.resolve(root);
  const target = path.resolve(rootPath, safeBaseName(fileName));
  if (!target.startsWith(`${rootPath}${path.sep}`)) {
    throw new Error("Invalid file path");
  }
  return target;
}

export async function fileExists(filePath: string) {
  try {
    return (await stat(filePath)).isFile();
  } catch {
    return false;
  }
}
