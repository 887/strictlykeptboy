package com.eight87.skb.cli.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * CLI-C.1 envelope:
 *
 * ```
 * { "version": 1, "command": "<dotted>", "result": {...} }
 * ```
 *
 * Error envelope (CLI-C.1 / CLI-D.5):
 *
 * ```
 * { "version": 1, "command": "<dotted>", "error": { "code": ..., "message": ..., "details": {...} } }
 * ```
 */
object JsonEnvelope {
  private val json = Json { prettyPrint = false }

  fun success(command: String, result: JsonElement): String {
    val obj = buildJsonObject {
      put("version", JsonPrimitive(1))
      put("command", JsonPrimitive(command))
      put("result", result)
    }
    return json.encodeToString(JsonObject.serializer(), obj)
  }

  fun error(command: String, code: ExitCode, message: String, details: Map<String, Any?>): String {
    val obj = buildJsonObject {
      put("version", JsonPrimitive(1))
      put("command", JsonPrimitive(command))
      put(
        "error",
        buildJsonObject {
          put("code", JsonPrimitive(code.wireName))
          put("message", JsonPrimitive(message))
          put("details", toJsonElement(details))
        },
      )
    }
    return json.encodeToString(JsonObject.serializer(), obj)
  }

  fun toJsonElement(any: Any?): JsonElement = when (any) {
    null -> JsonNull
    is JsonElement -> any
    is String -> JsonPrimitive(any)
    is Number -> JsonPrimitive(any)
    is Boolean -> JsonPrimitive(any)
    is Map<*, *> -> JsonObject(any.entries.associate { (k, v) -> k.toString() to toJsonElement(v) })
    is List<*> -> JsonArray(any.map { toJsonElement(it) })
    else -> JsonPrimitive(any.toString())
  }
}
