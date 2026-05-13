package com.eight87.skb.cli.core

/**
 * Exit code taxonomy per D.24 / CLI-D.1 (Phase X.7).
 *
 * NEVER reorder these. The numeric values are part of the CLI's
 * machine-readable contract — `error.code` in `--json` mode is the
 * lowercase enum name; the process exit code is the integer.
 */
enum class ExitCode(val code: Int, val wireName: String) {
  OK(0, "ok"),
  USAGE(1, "usage"),
  NOT_FOUND(2, "not_found"),
  CONFLICT(3, "conflict"),
  AUTH(4, "auth"),
  CORRUPT(5, "corrupt"),
  SCHEMA_MISMATCH(6, "schema_mismatch"),
  NETWORK(7, "network"),
  INTERNAL(8, "internal");

  companion object {
    fun fromCode(c: Int): ExitCode = values().firstOrNull { it.code == c } ?: INTERNAL
  }
}

/**
 * Exception type thrown anywhere in the CLI to abort with a specific
 * exit code. The Main loop catches this, prints either a human error
 * line or a JSON error envelope (per `--json` flag), then exits with
 * [code].code.
 */
class CliError(
  val code: ExitCode,
  message: String,
  val hint: String? = null,
  val details: Map<String, Any?> = emptyMap(),
  cause: Throwable? = null,
) : RuntimeException(message, cause)
