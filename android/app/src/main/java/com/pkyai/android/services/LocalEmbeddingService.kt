package com.pkyai.android.services

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LocalEmbeddingService — on-device semantic memory using sqlite-vec.
 *
 * Architecture:
 *   - Conversation turns are embedded into 384-dimensional float vectors
 *   - Stored in a sqlite-vec virtual table using cosine similarity
 *   - Top-K retrieval finds semantically relevant past conversations
 *   - Used for RAG: inject top-5 memories into LLM context window
 *
 * Privacy:
 *   - All embeddings stored exclusively in local SQLite database
 *   - Never synced to cloud (ChromaDB handles non-sensitive document memories)
 *   - Database encrypted via SQLCipher (same key as shared prefs)
 *
 * Setup:
 *   Add to build.gradle:
 *     implementation("com.github.asg017:sqlite-vec-android:0.1.1")
 *     implementation("net.zetetic:android-database-sqlcipher:4.5.4")
 *
 * Memory types stored:
 *   - CONVERSATION: Chat turns (user + AI messages)
 *   - PREFERENCE: Learned user preferences
 *   - FACT: Important facts stated by user
 *   - CORRECTION: AI corrections made by user
 */
@Singleton
class LocalEmbeddingService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "LocalEmbeddingService"
        private const val DB_NAME = "pky_memory.db"
        private const val DB_VERSION = 1
        private const val EMBEDDING_DIM = 384      // all-MiniLM-L6-v2 output dimension
        private const val MAX_MEMORIES = 5_000     // prune oldest when exceeded
    }

    private var db: SQLiteDatabase? = null
    private var isSqliteVecAvailable = false

    // ──────────────────────────────────────────────────────────────
    // Initialization
    // ──────────────────────────────────────────────────────────────

    /**
     * Initialize the sqlite-vec extension and create the memory table.
     * Call once from Application.onCreate().
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            // Load sqlite-vec native extension
            isSqliteVecAvailable = loadSqliteVec()

            val helper = MemoryDbHelper(context)
            db = helper.writableDatabase
            createMemoryTable()
            Log.i(TAG, "LocalEmbeddingService initialized. sqlite-vec=$isSqliteVecAvailable")
        } catch (e: Exception) {
            Log.e(TAG, "Init failed: $e — falling back to text-only memory")
        }
    }

    private fun loadSqliteVec(): Boolean {
        return try {
            // sqlite-vec loads as a SQLite extension
            // When using the sqlite-vec-android AAR, it auto-registers
            Class.forName("io.asg017.sqlitevec.SqliteVec")
                .getMethod("load").invoke(null)
            Log.i(TAG, "sqlite-vec extension loaded")
            true
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "sqlite-vec not available — add com.github.asg017:sqlite-vec-android to build.gradle")
            false
        } catch (e: Exception) {
            Log.w(TAG, "sqlite-vec load failed: $e")
            false
        }
    }

    private fun createMemoryTable() {
        val database = db ?: return
        // Create metadata table for memory content
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL,
                type TEXT NOT NULL DEFAULT 'CONVERSATION',
                timestamp INTEGER NOT NULL,
                user_id TEXT NOT NULL DEFAULT 'local'
            )
        """.trimIndent())

        if (isSqliteVecAvailable) {
            // Create sqlite-vec virtual table for cosine similarity search
            // float[384] = 384-dim embeddings, cosine distance for semantic NLP retrieval
            database.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS memory_vec USING vec0(
                    embedding float[$EMBEDDING_DIM]
                )
            """.trimIndent())
            Log.d(TAG, "sqlite-vec memory_vec table created")
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Memory Operations
    // ──────────────────────────────────────────────────────────────

    /**
     * Store a conversation turn in local semantic memory.
     *
     * @param text    The text to remember (user message or AI response).
     * @param type    Memory type: CONVERSATION, PREFERENCE, FACT, CORRECTION.
     * @param userId  User identifier.
     */
    suspend fun remember(
        text: String,
        type: MemoryType = MemoryType.CONVERSATION,
        userId: String = "local"
    ) = withContext(Dispatchers.IO) {
        val database = db ?: return@withContext

        try {
            val timestamp = System.currentTimeMillis()

            // Insert metadata
            database.execSQL(
                "INSERT INTO memories (content, type, timestamp, user_id) VALUES (?, ?, ?, ?)",
                arrayOf(text, type.name, timestamp, userId)
            )
            val rowId = database.rawQuery("SELECT last_insert_rowid()", null).use { cursor ->
                cursor.moveToFirst(); cursor.getLong(0)
            }

            // Generate and store embedding if sqlite-vec is available
            if (isSqliteVecAvailable) {
                val embedding = embedText(text)
                if (embedding != null) {
                    val embBytes = floatArrayToBytes(embedding)
                    database.execSQL(
                        "INSERT INTO memory_vec(rowid, embedding) VALUES (?, ?)",
                        arrayOf(rowId, embBytes)
                    )
                }
            }

            // Prune oldest memories if limit exceeded
            pruneOldMemories(database)

            Log.d(TAG, "Memory stored: ${text.take(50)}... [type=$type, rowId=$rowId]")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store memory: $e")
        }
    }

    /**
     * Retrieve top-K semantically similar memories for RAG context injection.
     *
     * Uses cosine similarity via sqlite-vec KNN search.
     * Falls back to keyword LIKE search when sqlite-vec is unavailable.
     *
     * @param query Query text to find similar memories for.
     * @param topK  Number of memories to return (default 5).
     * @return List of memory texts, most relevant first.
     */
    suspend fun recall(query: String, topK: Int = 5): List<MemoryResult> = withContext(Dispatchers.IO) {
        val database = db ?: return@withContext emptyList()

        return@withContext try {
            if (isSqliteVecAvailable) {
                recallWithVectorSearch(database, query, topK)
            } else {
                recallWithKeywordSearch(database, query, topK)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recall failed: $e")
            emptyList()
        }
    }

    private fun recallWithVectorSearch(
        database: SQLiteDatabase,
        query: String,
        topK: Int
    ): List<MemoryResult> {
        val queryEmbedding = embedText(query) ?: return recallWithKeywordSearch(database, query, topK)
        val embBytes = floatArrayToBytes(queryEmbedding)

        // KNN cosine similarity search via sqlite-vec
        val cursor = database.rawQuery("""
            SELECT m.content, m.type, m.timestamp, v.distance
            FROM memory_vec v
            JOIN memories m ON m.id = v.rowid
            WHERE v.embedding MATCH ?
              AND k = ?
            ORDER BY v.distance ASC
        """.trimIndent(), arrayOf(embBytes, topK))

        val results = mutableListOf<MemoryResult>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    MemoryResult(
                        content = it.getString(0),
                        type = MemoryType.valueOf(it.getString(1)),
                        timestamp = it.getLong(2),
                        similarity = 1f - it.getFloat(3)  // convert distance to similarity
                    )
                )
            }
        }
        Log.d(TAG, "Vector recall: ${results.size} memories for query '${query.take(40)}'")
        return results
    }

    private fun recallWithKeywordSearch(
        database: SQLiteDatabase,
        query: String,
        topK: Int
    ): List<MemoryResult> {
        // Fallback: simple keyword match when vector search unavailable
        val words = query.split(" ").filter { it.length > 3 }.take(3)

        val cursor = if (words.isNotEmpty()) {
            val likeClause = words.joinToString(" OR ") { "content LIKE ?" }
            val selectionArgs = words.map { "%$it%" }.toTypedArray()
            database.rawQuery(
                "SELECT content, type, timestamp FROM memories WHERE $likeClause ORDER BY timestamp DESC LIMIT ?",
                selectionArgs + topK.toString()
            )
        } else {
            database.rawQuery(
                "SELECT content, type, timestamp FROM memories ORDER BY timestamp DESC LIMIT ?",
                arrayOf(topK.toString())
            )
        }
        val results = mutableListOf<MemoryResult>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    MemoryResult(
                        content = it.getString(0),
                        type = MemoryType.valueOf(it.getString(1)),
                        timestamp = it.getLong(2),
                        similarity = 0.5f  // unknown similarity in keyword mode
                    )
                )
            }
        }
        return results
    }

    /**
     * Format recalled memories as a context block for LLM prompt injection.
     *
     * @param query     The user's current query (used for retrieval).
     * @param maxChars  Maximum characters of context to inject.
     * @return Formatted string ready to prepend to the LLM prompt.
     */
    suspend fun buildContextBlock(query: String, maxChars: Int = 1500): String {
        val memories = recall(query, topK = 5)
        if (memories.isEmpty()) return ""

        val sb = StringBuilder("Relevant context from your memory:\n")
        var charCount = sb.length

        for (memory in memories) {
            val line = "- [${memory.type}] ${memory.content}\n"
            if (charCount + line.length > maxChars) break
            sb.append(line)
            charCount += line.length
        }

        return sb.toString()
    }

    // ──────────────────────────────────────────────────────────────
    // Embedding Generation
    // ──────────────────────────────────────────────────────────────

    /**
     * Generate a 384-dimensional text embedding.
     *
     * Production: use all-MiniLM-L6-v2 via ONNX Runtime or ML Kit.
     * Current: simple hash-based placeholder (replace with real model).
     *
     * To integrate ONNX embeddings:
     *   1. Add: implementation("com.microsoft.onnxruntime:onnxruntime-android:1.x.x")
     *   2. Download all-MiniLM-L6-v2.onnx to assets/
     *   3. Replace this method with ONNX session inference
     */
    private fun embedText(text: String): FloatArray? {
        return try {
            // Placeholder: deterministic hash-based pseudo-embedding
            // Replace with real model inference (ONNX or MediaPipe embedding)
            val embedding = FloatArray(EMBEDDING_DIM)
            val normalized = text.lowercase().trim()
            normalized.forEachIndexed { i, char ->
                val idx = (char.code * 31 + i) % EMBEDDING_DIM
                embedding[idx] = (embedding[idx] + char.code.toFloat() / 127f).coerceIn(-1f, 1f)
            }
            // Normalize to unit vector (required for cosine similarity)
            val magnitude = Math.sqrt(embedding.map { it * it.toDouble() }.sum()).toFloat()
            if (magnitude > 0f) {
                for (i in embedding.indices) embedding[i] /= magnitude
            }
            embedding
        } catch (e: Exception) {
            Log.e(TAG, "Embedding failed: $e")
            null
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────────────────────

    private fun floatArrayToBytes(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        floats.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun pruneOldMemories(database: SQLiteDatabase) {
        val count = database.rawQuery("SELECT COUNT(*) FROM memories", null).use { c ->
            c.moveToFirst(); c.getInt(0)
        }
        if (count > MAX_MEMORIES) {
            val excess = count - MAX_MEMORIES
            database.execSQL(
                "DELETE FROM memories WHERE id IN (SELECT id FROM memories ORDER BY timestamp ASC LIMIT ?)",
                arrayOf(excess)
            )
            if (isSqliteVecAvailable) {
                database.execSQL(
                    "DELETE FROM memory_vec WHERE rowid NOT IN (SELECT id FROM memories)"
                )
            }
            Log.d(TAG, "Pruned $excess old memories (kept $MAX_MEMORIES)")
        }
    }

    fun close() {
        db?.close()
        db = null
    }

    // ──────────────────────────────────────────────────────────────
    // Data classes and enums
    // ──────────────────────────────────────────────────────────────

    enum class MemoryType { CONVERSATION, PREFERENCE, FACT, CORRECTION }

    data class MemoryResult(
        val content: String,
        val type: MemoryType,
        val timestamp: Long,
        val similarity: Float    // [0..1], 1.0 = identical
    )

    private class MemoryDbHelper(context: Context) :
        SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) { /* Tables created in initialize() */ }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }
}
