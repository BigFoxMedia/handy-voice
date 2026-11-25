package com.bigfoxmedia.handyvoice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var settings: SettingsStore
    private val client = RemoteSttClient()
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var baseUrlField: EditText
    private lateinit var modelField: EditText
    private lateinit var apiKeyField: EditText
    private lateinit var languageField: EditText
    private lateinit var fallbackCheck: CheckBox
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = try {
            SettingsStore(this)
        } catch (error: Exception) {
            showFatal("Encrypted settings unavailable: ${error.message}")
            return
        }

        setContentView(buildUi())
        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusView.isInitialized) {
            updatePermissionStatus()
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }

        layout.addView(TextView(this).apply {
            text = "Handy Voice"
            textSize = 26f
            setTextColor(Color.parseColor("#222222"))
            setPadding(0, 0, 0, dp(8))
        })

        layout.addView(TextView(this).apply {
            text = "Remote dictation through LiteLLM. The mic appears when you focus a text field and the keyboard is open; speak, then stop to insert the transcript."
            textSize = 14f
            setTextColor(Color.parseColor("#555555"))
            setPadding(0, 0, 0, dp(16))
        })

        baseUrlField = EditText(this).apply {
            hint = "LiteLLM base URL"
            setText(settings.baseUrl)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        layout.addView(fieldLabel("LiteLLM base URL"))
        layout.addView(baseUrlField)

        modelField = EditText(this).apply {
            hint = "Model"
            setText(settings.model)
        }
        layout.addView(fieldLabel("Model"))
        layout.addView(modelField)

        apiKeyField = EditText(this).apply {
            hint = "LiteLLM API key"
            setText(settings.apiKey)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(fieldLabel("API key"))
        layout.addView(apiKeyField)

        languageField = EditText(this).apply {
            hint = "Language code (auto for auto)"
            setText(settings.language)
        }
        layout.addView(fieldLabel("Language"))
        layout.addView(languageField)

        fallbackCheck = CheckBox(this).apply {
            text = "Fallback to Android speech recognition if remote fails"
            isChecked = settings.fallbackEnabled
        }
        layout.addView(fallbackCheck)

        val saveButton = button("Save settings") {
            saveSettings()
        }
        layout.addView(saveButton)

        val testButton = button("Test LiteLLM connection") {
            testConnection()
        }
        layout.addView(testButton)

        val permissionButton = button("Grant microphone / notification permissions") {
            requestRuntimePermissions()
        }
        layout.addView(permissionButton)

        val overlayButton = button("Enable overlay permission") {
            openOverlaySettings()
        }
        layout.addView(overlayButton)

        val accessibilityButton = button("Enable accessibility service") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        layout.addView(accessibilityButton)

        val startButton = button("Start floating mic") {
            startOverlay()
        }
        layout.addView(startButton)

        val stopButton = button("Stop floating mic") {
            stopService(Intent(this, OverlayService::class.java))
            updatePermissionStatus("Overlay stopped")
        }
        layout.addView(stopButton)

        statusView = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, dp(16), 0, dp(8))
        }
        layout.addView(statusView)

        scroll.addView(layout)
        return scroll
    }

    private fun fieldLabel(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, dp(10), 0, dp(2))
        }

    private fun button(text: String, action: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }

    private fun saveSettings() {
        settings.save(
            baseUrlField.text.toString().trim(),
            modelField.text.toString().trim(),
            apiKeyField.text.toString().trim(),
            languageField.text.toString().trim().ifEmpty { "auto" },
            fallbackCheck.isChecked,
        )
        updatePermissionStatus("Settings saved")
    }

    private fun testConnection() {
        val baseUrl = baseUrlField.text.toString().trim()
        val apiKey = apiKeyField.text.toString().trim()
        updatePermissionStatus("Testing…")
        executor.execute {
            val outcome = client.testConnection(baseUrl, apiKey)
            handler.post {
                updatePermissionStatus(
                    when (outcome) {
                        is SttOutcome.Success -> "LiteLLM: ${outcome.text}"
                        is SttOutcome.Failure -> "LiteLLM test failed: ${outcome.message}"
                    },
                )
            }
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions(permissions.toTypedArray(), PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            updatePermissionStatus()
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        startActivity(intent)
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            updatePermissionStatus("Overlay permission not granted")
            openOverlaySettings()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updatePermissionStatus("Microphone permission not granted")
            requestRuntimePermissions()
            return
        }

        val accessibility = isAccessibilityServiceEnabled()
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updatePermissionStatus(
            if (accessibility) {
                "Overlay started; it appears when a text field is focused and the keyboard is open."
            } else {
                "Overlay started; accessibility is not enabled, so insertion falls back to clipboard. Enable it for the mic to appear on keyboard focus."
            },
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val serviceName = "$packageName/${HandyAccessibilityService::class.java.name}"
        return enabled.split(':').any { it.equals(serviceName, ignoreCase = true) }
    }

    private fun updatePermissionStatus(message: String? = null) {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val overlay = Settings.canDrawOverlays(this)
        val accessibility = isAccessibilityServiceEnabled()
        val base = "Permissions — mic: ${yesNo(mic)}, overlay: ${yesNo(overlay)}, accessibility: ${yesNo(accessibility)}"
        statusView.text = if (message.isNullOrBlank()) base else "$message\n$base"
    }

    private fun showFatal(message: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }
        layout.addView(TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(Color.RED)
        })
        setContentView(layout)
    }

    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PERMISSION_REQUEST = 42
    }
}
