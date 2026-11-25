package com.bigfoxmedia.handyvoice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class OverlayService : Service() {
    companion object {
        private const val CHANNEL_ID = "handy_voice_overlay"
        private const val NOTIFICATION_ID = 1
        private const val TAP_SLOP_PX = 14f
        private const val RESET_DELAY_MS = 3_000L
    }

    private lateinit var settings: SettingsStore
    private val client = RemoteSttClient()
    private val recorder = AudioRecorder()
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var overlayParams: WindowManager.LayoutParams
    private lateinit var micView: ImageView
    private lateinit var cancelView: TextView
    private lateinit var statusView: TextView

    private var recording = false
    private var pendingRequest = false
    private var localFallbackActive = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var overlayAdded = false
    private var hasEditableFocus = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        try {
            settings = SettingsStore(this)
        } catch (error: Exception) {
            Toast.makeText(this, "Encrypted settings unavailable: ${error.message}", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        HandyAccessibilityService.setEditableFocusListener { focused ->
            handler.post {
                hasEditableFocus = focused
                if (focused) {
                    showOverlay()
                } else if (!recording && !pendingRequest && !localFallbackActive) {
                    hideOverlay()
                }
            }
        }
        HandyAccessibilityService.instance?.refreshEditableFocus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        HandyAccessibilityService.setEditableFocusListener(null)
        handler.removeCallbacksAndMessages(null)
        recorder.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        executor.shutdownNow()
        hideOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (!::windowManager.isInitialized || overlayAdded) return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        micView = ImageView(this).apply {
            setImageResource(R.drawable.ic_mic)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Start or stop Handy Voice recording"
            background = circleDrawable(Color.parseColor("#E8488C"))
            setPadding(dp(9), dp(9), dp(9), dp(9))
        }
        val micRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        micRow.addView(
            micView,
            LinearLayout.LayoutParams(dp(48), dp(48)),
        )

        cancelView = TextView(this).apply {
            text = "X"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            background = circleDrawable(Color.parseColor("#B52C55"))
            contentDescription = "Cancel recording"
            visibility = View.GONE
            setOnClickListener { cancelRecording() }
        }
        micRow.addView(
            cancelView,
            LinearLayout.LayoutParams(dp(32), dp(32)).apply { leftMargin = dp(5) },
        )
        root.addView(micRow)

        statusView = TextView(this).apply {
            text = "Tap mic"
            setTextColor(Color.WHITE)
            textSize = 9f
            background = pillDrawable(Color.argb(175, 0, 0, 0))
            setPadding(dp(6), dp(3), dp(6), dp(3))
        }
        root.addView(
            statusView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(3) },
        )

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(80)
        }

        micView.setOnTouchListener { _, event ->
            handleOverlayTouch(event)
            true
        }

        try {
            windowManager.addView(root, overlayParams)
            overlayView = root
            overlayAdded = true
        } catch (error: Exception) {
            Toast.makeText(this, "Overlay permission missing: ${error.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun hideOverlay() {
        if (!overlayAdded || !::windowManager.isInitialized) return
        try {
            windowManager.removeView(overlayView)
        } catch (_: Exception) {
        }
        overlayAdded = false
    }

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchStartX = 0
    private var touchStartY = 0
    private var moved = false

    private fun handleOverlayTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.rawX
                touchDownY = event.rawY
                touchStartX = overlayParams.x
                touchStartY = overlayParams.y
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - touchDownX
                val dy = event.rawY - touchDownY
                if (Math.abs(dx) > TAP_SLOP_PX || Math.abs(dy) > TAP_SLOP_PX) {
                    moved = true
                }
                overlayParams.x = touchStartX + dx.toInt()
                overlayParams.y = touchStartY + dy.toInt()
                try {
                    windowManager.updateViewLayout(overlayView, overlayParams)
                } catch (_: Exception) {
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    toggleRecording()
                }
            }
        }
    }

    private fun toggleRecording() {
        if (localFallbackActive) {
            cancelLocalFallback()
            resetStatusSoon()
            return
        }
        if (pendingRequest) {
            updateStatus("Transcribing…")
            return
        }
        if (recording) {
            stopRecordingAndTranscribe()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateStatus("Mic permission")
            Toast.makeText(this, "Grant microphone permission in the app", Toast.LENGTH_LONG).show()
            return
        }
        if (!recorder.start()) {
            updateStatus("Mic error")
            return
        }
        recording = true
        setRecordingUi(true)
        updateStatus("Recording…")
    }

    private fun cancelRecording() {
        recorder.cancel()
        recording = false
        pendingRequest = false
        setRecordingUi(false)
        updateStatus("Cancelled")
        resetStatusSoon()
    }

    private fun stopRecordingAndTranscribe() {
        val pcm = recorder.stop()
        recording = false
        setRecordingUi(false)

        if (pcm.isEmpty()) {
            updateStatus("No audio")
            resetStatusSoon()
            return
        }

        pendingRequest = true
        updateStatus("Transcribing…")

        val baseUrl = settings.baseUrl
        val model = settings.model
        val apiKey = settings.apiKey
        val language = settings.language

        executor.execute {
            val outcome = client.transcribe(baseUrl, model, apiKey, language, pcm)
            handler.post {
                pendingRequest = false
                handleOutcome(outcome)
            }
        }
    }

    private fun handleOutcome(outcome: SttOutcome) {
        when (outcome) {
            is SttOutcome.Success -> {
                if (insertText(outcome.text)) {
                    updateStatus("Inserted")
                } else {
                    copyToClipboard(outcome.text)
                    updateStatus("Copied")
                }
                resetStatusSoon()
            }
            is SttOutcome.Failure -> {
                if (settings.fallbackEnabled) {
                    startLocalFallback()
                } else {
                    updateStatus("Remote failed")
                    Toast.makeText(this, outcome.message, Toast.LENGTH_LONG).show()
                    resetStatusSoon()
                }
            }
        }
    }

    private fun insertText(text: String): Boolean {
        val accessibility = HandyAccessibilityService.instance
        if (accessibility == null) {
            return false
        }
        return accessibility.insertText(text)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Handy Voice", text))
    }

    private fun startLocalFallback() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateStatus("Fallback unavailable")
            resetStatusSoon()
            return
        }

        cancelLocalFallback()
        localFallbackActive = true
        updateStatus("Speak for fallback")

        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                updateStatus("Transcribing…")
            }

            override fun onError(error: Int) {
                localFallbackActive = false
                updateStatus("Fallback failed")
                Toast.makeText(this@OverlayService, speechErrorMessage(error), Toast.LENGTH_SHORT).show()
                resetStatusSoon()
            }

            override fun onResults(results: Bundle?) {
                localFallbackActive = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isBlank()) {
                    updateStatus("Fallback empty")
                } else if (insertText(text)) {
                    updateStatus("Inserted")
                } else {
                    copyToClipboard(text)
                    updateStatus("Copied")
                }
                resetStatusSoon()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            if (settings.language.isNotBlank() && settings.language != "auto") {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, settings.language)
            }
        }
        recognizer.startListening(intent)
    }

    private fun cancelLocalFallback() {
        localFallbackActive = false
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun speechErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "audio error"
        SpeechRecognizer.ERROR_CLIENT -> "client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "microphone permission"
        SpeechRecognizer.ERROR_NETWORK -> "network"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "no speech recognized"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no speech"
        else -> "error $error"
    }

    private fun setRecordingUi(isRecording: Boolean) {
        micView.background = circleDrawable(
            if (isRecording) Color.parseColor("#B52C55") else Color.parseColor("#E8488C"),
        )
        if (::cancelView.isInitialized) {
            cancelView.visibility = if (isRecording) View.VISIBLE else View.GONE
        }
    }

    private fun updateStatus(text: String) {
        handler.post {
            if (::statusView.isInitialized) {
                statusView.text = text
            }
        }
    }

    private fun resetStatusSoon() {
        handler.postDelayed({
            if (!recording && !pendingRequest && !localFallbackActive) {
                updateStatus("Tap mic")
                setRecordingUi(false)
                if (!hasEditableFocus) {
                    hideOverlay()
                }
            }
        }, RESET_DELAY_MS)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Handy Voice overlay",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Floating microphone for Handy Voice dictation"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Handy Voice")
            .setContentText("Floating microphone active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun circleDrawable(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2), Color.WHITE)
        }

    private fun pillDrawable(color: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(color)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
