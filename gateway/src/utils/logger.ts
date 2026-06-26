import pino, { type LoggerOptions } from "pino";

export const loggerOptions: LoggerOptions = {
  level: process.env.LOG_LEVEL ?? "info",
  redact: {
    paths: [
      "req.headers.authorization",
      "request.headers.authorization",
      "headers.authorization",
      "*.apiKey",
      "*.token"
    ],
    censor: "[redacted]"
  }
};

export const logger = pino(loggerOptions);
