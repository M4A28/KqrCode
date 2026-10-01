package com.mohammed.mosa.qrscanner.data

import android.content.Context
import com.mohammed.mosa.qrscanner.util.ScanTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScanRepository private constructor(private val dao: ScanDao) {

    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeAll(): Flow<List<ScanEntity>> = dao.observeAll()

    /** Camera scan. Returns false when skipped as a duplicate from the last 3s. */
    suspend fun add(value: String, format: String): Boolean {
        val dedupeSince = System.currentTimeMillis() - 3_000
        if (dao.recentValue(dedupeSince) == value) return false
        dao.insert(scan(value, format, isGenerated = false))
        return true
    }

    /** Generated code saved from the Create screen — no dedupe, user-intended. */
    suspend fun addGenerated(value: String, format: String) {
        dao.insert(scan(value, format, isGenerated = true))
    }

    private fun scan(value: String, format: String, isGenerated: Boolean) = ScanEntity(
        value = value,
        format = format,
        type = ScanTypes.describe(value),
        isLink = ScanTypes.isLink(value),
        isGenerated = isGenerated,
    )

    fun saveAsync(value: String, format: String, onSaved: (Boolean) -> Unit = {}) {
        saveScope.launch {
            val saved = runCatching { add(value, format) }.getOrDefault(false)
            withContext(Dispatchers.Main) { onSaved(saved) }
        }
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)
    suspend fun all(): List<ScanEntity> = dao.getAllOnce()
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun clearAll() = dao.clearAll()

    companion object {
        @Volatile private var instance: ScanRepository? = null
        fun get(context: Context): ScanRepository =
            instance ?: synchronized(this) {
                instance ?: ScanRepository(ScannerDatabase.get(context).scanDao())
                    .also { instance = it }
            }
    }
}