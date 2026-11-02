package com.whisperflow.audio

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Password Field Detection Utility
 *
 * Phase 0 Critical Security Requirement:
 * - Zero voice activation in password fields
 * - No recognizer start
 * - No cloud calls
 * - No text logging
 *
 * Gate criteria: Zero password field violations in all tests
 */
object PasswordFieldDetector {

    /**
     * Check if the current editor is a password field
     *
     * Detects:
     * - TYPE_TEXT_VARIATION_PASSWORD
     * - TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
     * - TYPE_TEXT_VARIATION_WEB_PASSWORD
     * - TYPE_NUMBER_VARIATION_PASSWORD
     */
    fun isPasswordField(editorInfo: EditorInfo?): Boolean {
        if (editorInfo == null) return false

        val inputType = editorInfo.inputType
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val inputVariation = inputType and InputType.TYPE_MASK_VARIATION

        return when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> {
                inputVariation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                inputVariation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                inputVariation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            }
            InputType.TYPE_CLASS_NUMBER -> {
                inputVariation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
            else -> false
        }
    }

    /**
     * Get human-readable description of input type (for debugging only, never log actual text)
     */
    fun getInputTypeDescription(editorInfo: EditorInfo?): String {
        if (editorInfo == null) return "null"

        val inputType = editorInfo.inputType
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val inputVariation = inputType and InputType.TYPE_MASK_VARIATION

        val className = when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> "TEXT"
            InputType.TYPE_CLASS_NUMBER -> "NUMBER"
            InputType.TYPE_CLASS_PHONE -> "PHONE"
            InputType.TYPE_CLASS_DATETIME -> "DATETIME"
            else -> "UNKNOWN(0x${inputClass.toString(16)})"
        }

        val variationName = when {
            inputClass == InputType.TYPE_CLASS_TEXT -> when (inputVariation) {
                InputType.TYPE_TEXT_VARIATION_NORMAL -> "NORMAL"
                InputType.TYPE_TEXT_VARIATION_PASSWORD -> "PASSWORD"
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> "VISIBLE_PASSWORD"
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> "WEB_PASSWORD"
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> "EMAIL"
                InputType.TYPE_TEXT_VARIATION_URI -> "URI"
                else -> "VARIATION(0x${inputVariation.toString(16)})"
            }
            inputClass == InputType.TYPE_CLASS_NUMBER -> when (inputVariation) {
                InputType.TYPE_NUMBER_VARIATION_NORMAL -> "NORMAL"
                InputType.TYPE_NUMBER_VARIATION_PASSWORD -> "PASSWORD"
                else -> "VARIATION(0x${inputVariation.toString(16)})"
            }
            else -> ""
        }

        return "$className/$variationName"
    }

    /**
     * Check if voice input should be completely disabled for this field
     *
     * Returns true for:
     * - Password fields (all types)
     * - Any field where security is paramount
     */
    fun shouldDisableVoice(editorInfo: EditorInfo?): Boolean {
        // For Phase 0: only password fields
        // Future phases might add more cases (credit cards, SSN, etc.)
        return isPasswordField(editorInfo)
    }
}
