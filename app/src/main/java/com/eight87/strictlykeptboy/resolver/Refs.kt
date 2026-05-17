package com.eight87.strictlykeptboy.resolver

/**
 * Phase E (resolver) — narrow identifier wrappers.
 *
 * Kept as plain inline `value class` aliases over `String` rather than
 * UUID instances — every entity in the store is a UUIDv7-stringed file
 * name (DM-D) and the resolver is comparison-only.
 */

@JvmInline value class RepoRef(val id: String)
@JvmInline value class CalendarRef(val id: String)
@JvmInline value class TodolistRef(val id: String)
@JvmInline value class RuleRef(val id: String)
@JvmInline value class EventRef(val id: String)
@JvmInline value class PersonRef(val id: String)
