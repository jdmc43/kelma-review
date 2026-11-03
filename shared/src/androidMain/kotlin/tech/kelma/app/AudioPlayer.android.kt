package tech.kelma.app

import android.media.MediaPlayer
import java.io.File

actual fun createAudioPlayer(): AudioPlayer = AndroidAudioPlayer()

private class AndroidAudioPlayer : AudioPlayer {
    private var player: MediaPlayer? = null
    private var temporaryFile: File? = null

    override fun play(media: CardMedia) {
        closePlayback()
        // MediaPlayer throws for media it cannot decode (unsupported codecs,
        // truncated or non-audio bytes). A clip that cannot play must never
        // crash review, so failures release the player and stay silent.
        try {
            val extension = media.filename.substringAfterLast('.', "media")
            val file = File.createTempFile("kelma-audio-", ".$extension").apply {
                writeBytes(media.bytes)
            }
            temporaryFile = file
            player = MediaPlayer().apply {
                setOnCompletionListener { closePlayback() }
                setOnErrorListener { _, _, _ -> closePlayback(); true }
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
        } catch (error: Exception) {
            closePlayback()
        }
    }

    override fun pause(): Boolean {
        val current = player ?: return false
        if (current.isPlaying) current.pause()
        return true
    }

    override fun seekBy(offsetMillis: Long): Boolean {
        val current = player ?: return false
        val target = (current.currentPosition.toLong() + offsetMillis)
            .coerceIn(0L, current.duration.toLong().coerceAtLeast(0L))
        current.seekTo(target.toInt())
        return true
    }

    override fun stop() {
        closePlayback()
    }

    override fun close() {
        closePlayback()
    }

    private fun closePlayback() {
        player?.release()
        player = null
        temporaryFile?.delete()
        temporaryFile = null
    }
}
