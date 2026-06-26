import { existsSync } from "node:fs";
import { readFile } from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import type { AppConfig } from "../config.js";
import { parseArgsTemplate, runCommand } from "../utils/command.js";
import { logger } from "../utils/logger.js";

export type HermesProfile = {
  id: string;
  name: string;
  description?: string;
  isDefault: boolean;
  source: "hermes" | "config" | "mock";
  sttLabel?: string;
  ttsLabel?: string;
  hermesHome?: string;
};

type ProfilesResult = {
  profiles: HermesProfile[];
  defaultProfileId: string;
};

export class ProfileStore {
  private cached: { expiresAt: number; value: ProfilesResult } | null = null;

  constructor(private config: AppConfig) {}

  async list(refresh = false): Promise<ProfilesResult> {
    if (!refresh && this.cached && this.cached.expiresAt > Date.now()) {
      if (this.config.gatewayDebug) {
        logger.info(
          { count: this.cached.value.profiles.length, defaultProfileId: this.cached.value.defaultProfileId },
          "Returning cached Hermes profiles"
        );
      }
      return this.cached.value;
    }

    const value = await this.discover().catch((error) => {
      logger.warn({ err: error }, "Profile discovery failed; falling back to default profile");
      return this.fallback();
    });
    logger.info({ count: value.profiles.length, defaultProfileId: value.defaultProfileId }, "Loaded Hermes profiles");
    if (this.config.gatewayDebug) {
      logger.info(
        {
          profiles: value.profiles.map((profile) => ({
            id: profile.id,
            name: profile.name,
            source: profile.source,
            hermesHome: profile.hermesHome,
            isDefault: profile.isDefault
          }))
        },
        "Loaded Hermes profile details"
      );
    }
    this.cached = { expiresAt: Date.now() + 30_000, value };
    return value;
  }

  async resolve(profileId?: string, agent?: string): Promise<HermesProfile> {
    const result = await this.list();
    const requested = (profileId || agent || result.defaultProfileId).trim();
    const profile = (
      result.profiles.find((profile) => profile.id === requested || profile.name === requested) ??
      withHermesHome(profileFromId(requested || result.defaultProfileId, result.defaultProfileId, "config"))
    );
    if (this.config.gatewayDebug) {
      logger.info(
        {
          requestedProfileId: profileId,
          requestedAgent: agent,
          requested,
          resolvedProfileId: profile.id,
          resolvedProfileName: profile.name,
          hermesHome: profile.hermesHome,
          source: profile.source
        },
        "Resolved Hermes profile"
      );
    }
    return profile;
  }

  private async discover(): Promise<ProfilesResult> {
    if (this.config.mockMode) return mockProfiles(this.config.hermesDefaultProfile);

    const source = this.config.hermesProfileSource;
    const attempts: Array<() => Promise<HermesProfile[]>> = [];

    if ((source === "auto" || source === "command") && this.config.hermesProfilesCommand) {
      attempts.push(() => this.fromCommand(this.config.hermesProfilesCommand, this.config.hermesProfilesArgsTemplate));
    }
    if ((source === "auto" || source === "path") && this.config.hermesProfilesPath) {
      attempts.push(() => this.fromPath(this.config.hermesProfilesPath));
    }
    if (source === "auto") {
      attempts.push(() => this.fromCommand(this.config.hermesCliCommand, "profile list"));
      attempts.push(() => this.fromCommonPaths());
    }

    if (source !== "fallback") {
      for (const attempt of attempts) {
        const profiles = await attempt().catch((error) => {
          logger.debug({ err: error }, "Profile discovery attempt failed");
          return [];
        });
        if (profiles.length > 0) return normalizeProfiles(profiles, this.config.hermesDefaultProfile);
      }
    }

    return this.fallback();
  }

  private async fromCommand(command: string, argsTemplate: string) {
    if (this.config.gatewayDebug) logger.info({ command, argsTemplate }, "Discovering Hermes profiles from command");
    const result = await runCommand(command, parseArgsTemplate(argsTemplate, {}), this.config.hermesTimeoutMs);
    if (this.config.gatewayDebug) {
      logger.info(
        { command, exitCode: result.exitCode, timedOut: result.timedOut, stdoutChars: result.stdout.length, stderrChars: result.stderr.length },
        "Hermes profile command finished"
      );
    }
    if (result.timedOut || result.exitCode !== 0) return [];
    return parseProfiles(result.stdout, "hermes", this.config.hermesDefaultProfile);
  }

  private async fromCommonPaths() {
    const candidates = [
      path.join(os.homedir(), ".hermes", "profiles.json"),
      path.join(os.homedir(), ".hermes", "profiles", "profiles.json"),
      path.join(os.homedir(), ".hermes", "desktop", "profiles.json")
    ];
    for (const candidate of candidates) {
      if (!existsSync(candidate)) continue;
      const profiles = await this.fromPath(candidate);
      if (profiles.length > 0) return profiles;
    }
    return [];
  }

  private async fromPath(filePath: string) {
    if (!existsSync(filePath)) return [];
    return parseProfiles(await readFile(filePath, "utf8"), "config", this.config.hermesDefaultProfile);
  }

  private fallback(): ProfilesResult {
    return normalizeProfiles(
      [profileFromId(this.config.hermesDefaultProfile, this.config.hermesDefaultProfile, "config")],
      this.config.hermesDefaultProfile
    );
  }
}

function mockProfiles(defaultProfileId: string): ProfilesResult {
  return normalizeProfiles(
    [
      profileFromId("main", defaultProfileId, "mock", "Main", "Default Hermes profile"),
      profileFromId("dev", defaultProfileId, "mock", "Dev", "Development profile"),
      profileFromId("research", defaultProfileId, "mock", "Research", "Research profile")
    ],
    defaultProfileId
  );
}

function parseProfiles(input: string, source: HermesProfile["source"], defaultProfileId: string) {
  const jsonProfiles = parseJsonProfiles(input, source, defaultProfileId);
  if (jsonProfiles.length > 0) return jsonProfiles;
  return parseHermesProfileTable(input, defaultProfileId);
}

function parseJsonProfiles(input: string, source: HermesProfile["source"], defaultProfileId: string) {
  try {
    const body = JSON.parse(input) as unknown;
    const rows = Array.isArray(body)
      ? body
      : typeof body === "object" && body !== null && Array.isArray((body as { profiles?: unknown }).profiles)
        ? (body as { profiles: unknown[] }).profiles
        : [];
    return rows.flatMap((row) => {
      if (typeof row === "string") return [profileFromId(row, defaultProfileId, source)];
      if (typeof row !== "object" || row === null) return [];
      const record = row as Record<string, unknown>;
      const id = stringValue(record.id) || stringValue(record.name);
      if (!id) return [];
      return [
        {
          id,
          name: stringValue(record.name) || titleFromId(id),
          description: stringValue(record.description),
          isDefault: Boolean(record.isDefault) || id === defaultProfileId,
          source,
          sttLabel: stringValue(record.sttLabel) || "Hermes default",
          ttsLabel: stringValue(record.ttsLabel) || "Hermes default",
          hermesHome: stringValue(record.hermesHome) || stringValue(record.path)
        }
      ];
    });
  } catch {
    return [];
  }
}

function parseHermesProfileTable(input: string, defaultProfileId: string) {
  return input
    .replace(/\x1b\[[0-9;]*m/g, "")
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("Profile ") && !line.startsWith("─"))
    .flatMap((line) => {
      const isDefault = line.startsWith("◆");
      const normalized = line.replace(/^◆\s*/, "");
      const id = normalized.split(/\s+/)[0];
      if (!id || id === "Profile") return [];
      return [{ ...profileFromId(id, defaultProfileId, "hermes"), isDefault }];
    });
}

function normalizeProfiles(profiles: HermesProfile[], configuredDefault: string): ProfilesResult {
  const deduped = new Map<string, HermesProfile>();
  for (const profile of profiles) {
    if (!profile.id) continue;
    deduped.set(profile.id, {
      ...profile,
      name: profile.name || titleFromId(profile.id),
      isDefault: profile.isDefault || profile.id === configuredDefault,
      sttLabel: profile.sttLabel || "Hermes default",
      ttsLabel: profile.ttsLabel || "Hermes default",
      hermesHome: profile.hermesHome || hermesHomeForProfile(profile.id)
    });
  }
  const values = [...deduped.values()];
  const defaultProfile =
    values.find((profile) => profile.isDefault) ??
    values.find((profile) => profile.id === configuredDefault) ??
    values[0];
  return {
    profiles: values.map((profile) => ({ ...profile, isDefault: profile.id === defaultProfile.id })),
    defaultProfileId: defaultProfile.id
  };
}

export function publicProfile(profile: HermesProfile) {
  return {
    id: profile.id,
    name: profile.name,
    description: profile.description,
    isDefault: profile.isDefault,
    source: profile.source,
    sttLabel: profile.sttLabel,
    ttsLabel: profile.ttsLabel
  };
}

function withHermesHome(profile: HermesProfile): HermesProfile {
  return { ...profile, hermesHome: profile.hermesHome || hermesHomeForProfile(profile.id) };
}

function hermesHomeForProfile(id: string) {
  if (id === "default" || id === "main") return path.join(os.homedir(), ".hermes");
  return path.join(os.homedir(), ".hermes", "profiles", id);
}

function profileFromId(
  id: string,
  defaultProfileId: string,
  source: HermesProfile["source"],
  name = titleFromId(id),
  description?: string
): HermesProfile {
  return {
    id,
    name,
    description,
    isDefault: id === defaultProfileId,
    source,
    sttLabel: "Hermes default",
    ttsLabel: "Hermes default"
  };
}

function titleFromId(id: string) {
  if (id === "default") return "Main";
  return id
    .split(/[_-]/)
    .filter(Boolean)
    .map((part) => `${part.slice(0, 1).toUpperCase()}${part.slice(1)}`)
    .join(" ");
}

function stringValue(value: unknown) {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}
