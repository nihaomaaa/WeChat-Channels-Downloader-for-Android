package app.streamsave.parser

data class VideoInfo(
    val sourceUrl: String,
    val title: String,
    val author: String = "",
    val coverUrl: String = "",
    val videoUrl: String = "",
    val notice: String = "",
    val needsLogin: Boolean = false,
    val prefixId: String = "",
) { val downloadable: Boolean get() = videoUrl.isNotBlank() }

interface VideoResolver { suspend fun resolve(source: String): VideoInfo }

class ResolveException(message: String) : java.io.IOException(message)
