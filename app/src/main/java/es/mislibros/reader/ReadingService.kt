package es.mislibros.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.io.File
import java.util.UUID

/**
 * Foreground playback service. It keeps the TTS engine alive while the activity
 * is not visible. Android's selected TTS engine supplies the actual voice.
 */
class ReadingService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_PLAY = "es.mislibros.reader.action.PLAY"
        const val ACTION_PAUSE = "es.mislibros.reader.action.PAUSE"
        const val ACTION_RESUME = "es.mislibros.reader.action.RESUME"
        const val ACTION_STOP = "es.mislibros.reader.action.STOP"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_RATE = "extra_rate"
        private const val CHANNEL_ID = "mis_libros_playback"
        private const val NOTIFICATION_ID = 4101
    }

    private var tts: TextToSpeech? = null
    private var initialized = false
    private var paused = false
    private var rate = 1.0f
    private var chunks: List<String> = emptyList()
    private var nextChunk = 0
    private var currentTitle = "Mis libros"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(applicationContext, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (!paused) {
                    nextChunk += 1
                    speakNextChunk()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (!paused) {
                    nextChunk += 1
                    speakNextChunk()
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (!paused) {
                    nextChunk += 1
                    speakNextChunk()
                }
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val fullText = intent.getStringExtra(EXTRA_FILE_PATH)?.let { path ->
                    runCatching { File(path).readText(Charsets.UTF_8) }.getOrNull()
                } ?: intent.getStringExtra(EXTRA_TEXT).orEmpty()
                if (fullText.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                rate = intent.getFloatExtra(EXTRA_RATE, 1.0f).coerceIn(0.5f, 2.0f)
                chunks = splitIntoChunks(fullText)
                nextChunk = 0
                paused = false
                startForeground(NOTIFICATION_ID, buildNotification("Reproduciendo"))
                if (initialized) speakNextChunk()
            }
            ACTION_PAUSE -> {
                paused = true
                tts?.stop()
                updateNotification("En pausa")
            }
            ACTION_RESUME -> {
                if (chunks.isNotEmpty()) {
                    paused = false
                    if (initialized) speakNextChunk()
                    updateNotification("Reproduciendo")
                }
            }
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        initialized = status == TextToSpeech.SUCCESS
        if (initialized) {
            // Prefer Spanish (Spain); the selected TTS engine decides which
            // installed voice is actually available.
            tts?.language = Locale("es", "ES")
            if (!paused && chunks.isNotEmpty()) speakNextChunk()
        } else {
            stopPlayback()
        }
    }

    private fun speakNextChunk() {
        if (!initialized || paused) return
        if (nextChunk >= chunks.size) {
            stopPlayback()
            return
        }
        val chunk = chunks[nextChunk]
        val params = Bundle()
        val utteranceId = "mislibros-${UUID.randomUUID()}"
        tts?.setSpeechRate(rate)
        tts?.speak(chunk, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        updateNotification("Reproduciendo · ${nextChunk + 1}/${chunks.size}")
    }

    private fun splitIntoChunks(text: String, limit: Int = 2500): List<String> {
        val result = mutableListOf<String>()
        val words = text.replace("\r\n", "\n").split(Regex("\\s+")).filter { it.isNotBlank() }
        val current = StringBuilder()
        for (word in words) {
            if (current.isNotEmpty() && current.length + word.length + 1 > limit) {
                result += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun stopPlayback() {
        paused = false
        chunks = emptyList()
        nextChunk = 0
        tts?.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Reproducción de Mis libros",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Controles de lectura en voz alta" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseOrResume = if (paused) ACTION_RESUME else ACTION_PAUSE
        val pauseLabel = if (paused) "Continuar" else "Pausar"
        val pauseIntent = PendingIntent.getService(
            this, 2, Intent(this, ReadingService::class.java).setAction(pauseOrResume),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 3, Intent(this, ReadingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle)
            .setContentText(status)
            .setContentIntent(openApp)
            .setOngoing(!paused)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, pauseLabel, pauseIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopIntent)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(status))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
