package com.gdelataillade.alarm.features

import android.content.Context
import android.media.MediaPlayer
import android.os.PowerManager
import io.flutter.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap

class AudioHandler(private val context: Context) {
    private val mediaPlayers = ConcurrentHashMap<Int, MediaPlayer>()
    private val timers = ConcurrentHashMap<Int, Timer>()

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
        scope: CoroutineScope,
        id: Int,
        filePath: String,
        loopAudio: Boolean,
        fadeDuration: Int?,
    ) {
        scope.launch(Dispatchers.IO) {
            // Stop and release any existing MediaPlayer and Timer for this ID
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
                    prepareAsync()

                    setOnPreparedListener {
                        isLooping = loopAudio
                        start()
                    }

                    setOnCompletionListener {
                        if (!loopAudio) {
                            // Switch back to the main thread to update UI or state
                            scope.launch(Dispatchers.Main) {
                                onAudioComplete?.invoke()
                            }
                        }
                    }

                    mediaPlayers[id] = this

                    if (fadeDuration != null && fadeDuration > 0) {
                        // Timer operations should also be offloaded
                        scope.launch(Dispatchers.Default) {
                            val timer = Timer(true)
                            timers[id] = timer
                            startFadeIn(this@apply, fadeDuration, timer)
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
        timers[id]?.cancel()
        timers.remove(id)

        mediaPlayers[id]?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayers.remove(id)
    }

    private fun startFadeIn(
        mediaPlayer: MediaPlayer,
        duration: Int,
        timer: Timer,
    ) {
        val maxVolume = 1.0f
        val fadeDuration = (duration * 1000).toLong()
        val fadeInterval = 100L
        val numberOfSteps = fadeDuration / fadeInterval
        val deltaVolume = maxVolume / numberOfSteps
        var volume = 0.0f

        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    if (!mediaPlayer.isPlaying) {
                        cancel()
                        return
                    }

                    mediaPlayer.setVolume(volume, volume)
                    volume += deltaVolume

                    if (volume >= maxVolume) {
                        mediaPlayer.setVolume(maxVolume, maxVolume)
                        cancel()
                    }
                }
            },
            0,
            fadeInterval,
        )
    }

    fun cleanUp() {
        timers.values.forEach(Timer::cancel)
        timers.clear()

        mediaPlayers.values.forEach { mediaPlayer ->
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.release()
        }
        mediaPlayers.clear()
    }
}
