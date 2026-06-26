import { spawn } from "node:child_process";

export type CommandResult = {
  exitCode: number | null;
  stdout: string;
  stderr: string;
  timedOut: boolean;
};

export function runCommand(command: string, args: string[], timeoutMs: number): Promise<CommandResult> {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { shell: false, stdio: ["ignore", "pipe", "pipe"] });
    const stdout: Buffer[] = [];
    const stderr: Buffer[] = [];
    let timedOut = false;

    const timer = setTimeout(() => {
      timedOut = true;
      child.kill("SIGTERM");
      setTimeout(() => child.kill("SIGKILL"), 1000).unref();
    }, timeoutMs);

    child.stdout.on("data", (chunk) => stdout.push(Buffer.from(chunk)));
    child.stderr.on("data", (chunk) => stderr.push(Buffer.from(chunk)));
    child.on("error", reject);
    child.on("close", (exitCode) => {
      clearTimeout(timer);
      resolve({
        exitCode,
        stdout: Buffer.concat(stdout).toString("utf8").trim(),
        stderr: Buffer.concat(stderr).toString("utf8").trim(),
        timedOut
      });
    });
  });
}

export function parseArgsTemplate(template: string, values: Record<string, string>) {
  const args: string[] = [];
  let current = "";
  let quote: "'" | '"' | null = null;

  for (let index = 0; index < template.length; index += 1) {
    const char = template[index];
    if ((char === "'" || char === '"') && !quote) {
      quote = char;
      continue;
    }
    if (char === quote) {
      quote = null;
      continue;
    }
    if (/\s/.test(char) && !quote) {
      if (current) args.push(replaceTemplateValues(current, values));
      current = "";
      continue;
    }
    current += char;
  }

  if (current) args.push(replaceTemplateValues(current, values));
  return args;
}

function replaceTemplateValues(input: string, values: Record<string, string>) {
  return input.replace(/\{(agent|prompt|sessionId)\}/g, (_, key: string) => values[key] ?? "");
}
