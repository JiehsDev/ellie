package com.example.jikan.audio

import android.content.Context
import android.media.MediaPlayer

/**
 * Plays the short pronunciation clip bundled per card (res/raw/hira_*.mp3).
 * One player at a time — starting a new clip stops whatever's still playing.
 */
class KanaAudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    fun play(audioResName: String) {
        val resId = context.resources.getIdentifier(
            audioResName.substringBeforeLast('.'),
            "raw",
            context.packageName,
        )
        if (resId == 0) return
        release()
        mediaPlayer = MediaPlayer.create(context, resId)?.apply {
            setOnCompletionListener { release() }
            start()
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
