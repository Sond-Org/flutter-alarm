package com.gdelataillade.alarm.features

import android.content.Context
import android.media.MediaPlayer
import android.os.PowerManager
import com.gdelataillade.alarm.alarm.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log

class AudioHandler(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mediaPlayers = ConcurrentHashMap<Int, MediaPlayer>()

    private var onAudioComplete: (() -> Unit)? = null

    fun setOnAudioCompleteListener(listener: () -> Unit) {
        onAudioComplete = listener
    }

    fun isMediaPlayerEmpty(): Boolean {
        return mediaPlayers.isEmpty()
    }

    fun getPlayingMediaPlayersIds(): List<Int> {
        return mediaPlayers.filter { (_, mediaPlayer) -> mediaPlayer.isPlaying }.keys.toList()
    }

    fun playAudio(
        id: Int,
        filePath: String,
        loopAudio: Boolean,
        fadeDurationSec: Int?,
    ) {
        scope.launch(Dispatchers.IO) {
            // Stop and release any existing MediaPlayer for this ID
            stopAudio(id)

            val adjustedFilePath =
                if (filePath.startsWith("assets/")) {
                    "flutter_assets/$filePath"
                } else {
                    filePath
                }

            try {
                MediaPlayer().apply {
                    if (adjustedFilePath.startsWith("flutter_assets/")) {
                        // It's an asset file
                        val assetManager = context.assets
                        val descriptor = assetManager.openFd(adjustedFilePath)
                        setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                    } else {
                        // Handle local files
                        setDataSource(adjustedFilePath)
                    }
                    setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                    prepare()

                    isLooping = loopAudio
                    start()

                    setOnCompletionListener {
                        if (!loopAudio) {
                            onAudioComplete?.invoke()
                        }
                    }

                    mediaPlayers[id] = this
                    if (fadeDurationSec != null && fadeDurationSec > 0) {
                        scope.launch(Dispatchers.Default) {
                            startFadeIn(this@apply, fadeDurationSec)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("flutter/AlarmPlugin", "Error playing audio")
                e.printStackTrace()
            }
        }
    }

    fun stopAudio(id: Int) {
        mediaPlayers[id]?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayers.remove(id)
    }

    private suspend fun startFadeIn(
        mediaPlayer: MediaPlayer,
        duration: Int,
    ) {
        val maxVolume = 1.0f
        val fadeDuration = duration * 1000L
        val fadeInterval = 100L
        val numberOfSteps = fadeDuration / fadeInterval
        val deltaVolume = maxVolume / numberOfSteps
        var volume = 0.0f

        while (volume <= maxVolume) {
            if (!mediaPlayers.containsValue(mediaPlayer) || !mediaPlayer.isPlaying) {
                return
            }

            val scaledVolume = 1 - log(1 + (1 - volume) * 9, 10.0f)
            Log.d("flutter/AlarmPlugin", "Fade in volume: $scaledVolume")
            mediaPlayer.setVolume(scaledVolume, scaledVolume)
            volume += deltaVolume
            delay(fadeInterval)
        }
    }

    fun cleanUp() {
        val players = mediaPlayers.values
        mediaPlayers.clear()
        players.forEach { mediaPlayer ->
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.release()
        }
    }
}
