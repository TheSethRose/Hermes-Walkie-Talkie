import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { randomUUID } from "node:crypto";
import type { AppConfig } from "../config.js";
import type { HermesProfile } from "./profileStore.js";
import { logger } from "../utils/logger.js";

type WorkerResponse = {
  id: string;
  diagnostic?: unknown;
  result?: { success?: unknown; transcript?: unknown; error?: unknown };
  error?: string;
};

type PendingRequest = {
  resolve: (response: WorkerResponse) => void;
  reject: (error: Error) => void;
  timer: ReturnType<typeof setTimeout>;
};

export class PersistentSttWorker {
  private child: ChildProcessWithoutNullStreams | null = null;
  private pending = new Map<string, PendingRequest>();
  private stdoutBuffer = "";
  private stderrBuffer = "";

  constructor(private config: AppConfig) {}

  start() {
    if (this.child) return;
    this.child = spawn(
      this.config.hermesSttPython,
      ["-u", "-c", workerScript],
      {
        shell: false,
        stdio: ["pipe", "pipe", "pipe"],
        env: {
          ...process.env,
          PYTHONPATH: this.config.hermesPythonPath
        }
      }
    );
    logger.info(
      { hermesPython: this.config.hermesSttPython, hermesPythonPath: this.config.hermesPythonPath },
      "Started persistent Hermes STT worker"
    );

    this.child.stdout.on("data", (chunk) => this.handleStdout(Buffer.from(chunk).toString("utf8")));
    this.child.stderr.on("data", (chunk) => this.handleStderr(Buffer.from(chunk).toString("utf8")));
    this.child.on("error", (error) => this.failAll(error));
    this.child.on("close", (exitCode) => {
      logger.warn({ exitCode }, "Persistent Hermes STT worker exited");
      this.child = null;
      this.failAll(new Error(`Persistent Hermes STT worker exited with ${exitCode}`));
    });
  }

  async transcribe(filePath: string, profile?: HermesProfile) {
    this.start();
    if (!this.child) throw new Error("Persistent Hermes STT worker failed to start");

    const id = randomUUID();
    const response = await new Promise<WorkerResponse>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error("Persistent Hermes STT worker timed out"));
      }, this.config.hermesTimeoutMs);
      this.pending.set(id, { resolve, reject, timer });
      this.child?.stdin.write(`${JSON.stringify({ id, filePath, hermesHome: profile?.hermesHome })}\n`);
    });

    if (response.diagnostic) {
      logger.info({ diagnostic: response.diagnostic }, "Hermes STT worker diagnostics");
    }
    if (response.error) throw new Error(response.error);
    if (!response.result) throw new Error("Persistent Hermes STT worker returned no result");
    return response.result;
  }

  stop() {
    this.child?.kill("SIGTERM");
    this.child = null;
    this.failAll(new Error("Persistent Hermes STT worker stopped"));
  }

  private handleStdout(chunk: string) {
    this.stdoutBuffer += chunk;
    const lines = this.stdoutBuffer.split("\n");
    this.stdoutBuffer = lines.pop() ?? "";
    for (const line of lines) {
      if (!line.trim()) continue;
      const response = JSON.parse(line) as WorkerResponse;
      const pending = this.pending.get(response.id);
      if (!pending) continue;
      clearTimeout(pending.timer);
      this.pending.delete(response.id);
      pending.resolve(response);
    }
  }

  private handleStderr(chunk: string) {
    this.stderrBuffer += chunk;
    const lines = this.stderrBuffer.split("\n");
    this.stderrBuffer = lines.pop() ?? "";
    for (const line of lines) {
      if (line.trim()) logger.info({ stderr: line.slice(0, 4000) }, "Hermes STT worker stderr");
    }
  }

  private failAll(error: Error) {
    for (const pending of this.pending.values()) {
      clearTimeout(pending.timer);
      pending.reject(error);
    }
    this.pending.clear();
  }
}

const workerScript = `
import json, os, sys, traceback
from hermes_cli.env_loader import load_hermes_dotenv
from hermes_cli.config import get_config_path, get_env_path
from tools import transcription_tools as stt

for line in sys.stdin:
    try:
        request = json.loads(line)
        hermes_home = request.get("hermesHome")
        if hermes_home:
            os.environ["HERMES_HOME"] = hermes_home
        load_hermes_dotenv(hermes_home=hermes_home)
        stt_config = stt._load_stt_config()
        diagnostic = {
            "hermes_home": os.environ.get("HERMES_HOME"),
            "config_path": str(get_config_path()),
            "config_exists": get_config_path().exists(),
            "env_path": str(get_env_path()),
            "env_exists": get_env_path().exists(),
            "provider_config": stt_config.get("provider"),
            "provider_resolved": stt._get_provider(stt_config),
            "has_faster_whisper": bool(getattr(stt, "_HAS_FASTER_WHISPER", False)),
            "has_local_command": stt._has_local_command(),
            "has_whisper_binary": bool(stt._find_whisper_binary()),
        }
        result = stt.transcribe_audio(request["filePath"])
        print(json.dumps({"id": request["id"], "diagnostic": diagnostic, "result": result}), flush=True)
    except Exception as exc:
        print(json.dumps({"id": request.get("id", ""), "error": str(exc), "traceback": traceback.format_exc()}), flush=True)
`;
