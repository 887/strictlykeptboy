package com.eight87.strictlykeptboy.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Write-side serialiser per DM-E.
 *
 * `write(root, entity)` computes the canonical path via [EntityPath]
 * and writes atomically by emitting to a sibling `.tmp` file then
 * `move(..., REPLACE_EXISTING, ATOMIC_MOVE)`. ATOMIC_MOVE is best-effort
 * — on filesystems that don't support it we fall back to REPLACE_EXISTING
 * (documented limitation).
 *
 * `writeBatch` does the same for many entities but stages all tmp files
 * first, then renames in a second pass. This is NOT a true transaction
 * (the JVM filesystem API doesn't offer one) but minimises the window
 * during which a partial write could be observed.
 */
object EntityWriter {

    /** Computes the canonical path for [entity] without writing. */
    fun pathFor(entity: TypedEntity): Path = when (entity) {
        is Event -> EntityPath.event(entity.calendarId, entity.id,
            EntityPath.BucketDate.forIsoDateTime(entity.start))
        is RecurrenceRule -> EntityPath.recurrenceRule(entity.calendarId, entity.id)
        is Exception -> EntityPath.exception(entity.calendarId, entity.ruleId, entity.instanceDate)
        is Task -> {
            val due = entity.due ?: error("dated task missing due — use StandingTask instead")
            EntityPath.datedTask(entity.todolistId, entity.id, EntityPath.BucketDate.forIsoDateTime(due))
        }
        is StandingTask -> EntityPath.standingTask(entity.todolistId, entity.id)
        is Deviation -> EntityPath.deviation(
            calendarId = entity.targetId.substringBefore(":", "unknown"),
            targetId = entity.targetId.substringAfter(":", entity.targetId),
            instanceDate = entity.instanceDate,
        )
        is Override -> EntityPath.override(entity.supersededCalendarId, entity.eventId, entity.instanceDate)
        is JournalEntry -> EntityPath.journal(entity.day, entity.sequence)
        is Identity -> EntityPath.identity(entity.id)
        is RawEntity -> error("RawEntity has no canonical path — caller must place explicitly")
    }

    suspend fun write(rootDir: File, entity: TypedEntity): Path = withContext(Dispatchers.IO) {
        val rel = pathFor(entity)
        val target = rootDir.toPath().resolve(rel)
        writeFrontmatter(target, encode(entity))
        rel
    }

    /** Write multiple entities. See class kdoc for transaction caveats. */
    suspend fun writeBatch(rootDir: File, entities: List<TypedEntity>): List<Path> =
        withContext(Dispatchers.IO) {
            val stagings = entities.map { e ->
                val rel = pathFor(e)
                val target = rootDir.toPath().resolve(rel)
                val tmp = stageTmp(target, FrontmatterWriter.serialize(encode(e)))
                Triple(rel, target, tmp)
            }
            for ((_, target, tmp) in stagings) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            }
            stagings.map { it.first }
        }

    suspend fun delete(rootDir: File, entity: TypedEntity): Boolean = withContext(Dispatchers.IO) {
        val target = rootDir.toPath().resolve(pathFor(entity))
        Files.deleteIfExists(target)
    }

    private fun encode(entity: TypedEntity): FrontmatterDoc = when (entity) {
        is Event -> entity.toDoc()
        is RecurrenceRule -> entity.toDoc()
        is Exception -> entity.toDoc()
        is Task -> entity.toDoc()
        is StandingTask -> entity.toDoc()
        is Deviation -> entity.toDoc()
        is Override -> entity.toDoc()
        is JournalEntry -> entity.toDoc()
        is Identity -> entity.toDoc()
        is RawEntity -> FrontmatterDoc(entity.rawTable, entity.body)
    }

    private fun writeFrontmatter(target: Path, doc: FrontmatterDoc) {
        val text = FrontmatterWriter.serialize(doc)
        val tmp = stageTmp(target, text)
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: UnsupportedOperationException) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun stageTmp(target: Path, text: String): Path {
        Files.createDirectories(target.parent)
        val tmp = target.resolveSibling(target.fileName.toString() + ".tmp-${System.nanoTime()}")
        Files.write(tmp, text.toByteArray(StandardCharsets.UTF_8))
        return tmp
    }
}
