package app.streamsave.download

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64

/** Small private prefix mask. Never loads a complete video into memory. */
class PrefixMaskStore(private val directory: File) {
    fun put(encoded: String): String {
        if (encoded.isEmpty()) return ""
        if (encoded.length > 700000) throw IOException("文件头数据过大")
        val bytes = try { Base64.getDecoder().decode(encoded) } catch (_: IllegalArgumentException) { throw IOException("文件头数据无效") }
        if (bytes.size > 512 * 1024) throw IOException("文件头数据过大")
        val id = hash(bytes)
        directory.mkdirs()
        val temp = File(directory, id + ".tmp")
        temp.writeBytes(bytes)
        Files.move(temp.toPath(), file(id).toPath(), StandardCopyOption.REPLACE_EXISTING)
        return id
    }
    fun exists(id: String) = id.isEmpty() || (valid(id) && file(id).isFile)
    fun read(id: String): ByteArray {
        if (id.isEmpty()) return ByteArray(0)
        if (!valid(id) || !file(id).isFile || file(id).length() > 512 * 1024) throw IOException("文件头缓存缺失，请重新解析")
        return file(id).readBytes().also { if (hash(it) != id) throw IOException("文件头缓存损坏，请重新解析") }
    }
    private fun valid(id: String) = id.matches(Regex("[a-f0-9]{64}"))
    private fun file(id: String) = File(directory, id + ".bin")
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
