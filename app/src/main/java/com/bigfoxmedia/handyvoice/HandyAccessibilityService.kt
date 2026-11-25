package com.bigfoxmedia.handyvoice

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class HandyAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: HandyAccessibilityService? = null
            private set

        @Volatile
        private var editableFocusListener: ((Boolean) -> Unit)? = null

        fun setEditableFocusListener(listener: ((Boolean) -> Unit)?) {
            editableFocusListener = listener
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        refreshEditableFocus()
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> refreshEditableFocus()
        }
    }

    fun refreshEditableFocus() {
        val root = rootInActiveWindow
        val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val editable = focused?.isEditable == true
        val keyboardVisible = windows.any {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
        editableFocusListener?.invoke(editable && keyboardVisible)
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
