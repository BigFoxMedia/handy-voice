package com.bigfoxmedia.handyvoice

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class HandyAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: HandyAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Used only on demand by the floating overlay.
    }

    override fun onInterrupt() {
    }

    fun insertText(text: String): Boolean {
        if (text.isBlank()) return false

        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val node = focused ?: findEditable(root) ?: return false

        val existing = node.text?.toString().orEmpty()
        val combined = when {
            existing.isEmpty() -> text
            existing.endsWith(" ") -> existing + text
            else -> "$existing $text"
        }

        val setTextArgs = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                combined,
            )
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setTextArgs)) {
            return true
        }

        // Some apps accept paste but not SET_TEXT. Put the transcript on the
        // clipboard and ask the focused editable node to paste it.
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Handy Voice", text))
        if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            return true
        }

        return false
    }

    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val editable = findEditable(child)
            if (editable != null) return editable
        }
        return null
    }
}
