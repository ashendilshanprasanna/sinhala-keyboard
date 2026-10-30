package com.example.sinhalakeyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.sinhalakeyboard.engine.SinglishTransliterator
import com.example.sinhalakeyboard.engine.WijesekaraLayoutEngine

/**
 * Android InputMethodService managing Sinhala Singlish, Sinhala Wijesekara,
 * and English typing modes.
 */
class SinhalaInputMethodService : InputMethodService() {

    enum class KeyboardMode {
        SINGLISH,
        WIJESEKARA,
        ENGLISH
    }

    private var currentMode = KeyboardMode.SINGLISH
    private var isShiftActive = false

    private val singlishEngine = SinglishTransliterator()
    private val wijesekaraEngine = WijesekaraLayoutEngine()
    private val composingBuffer = StringBuilder()

    private var keyboardContainer: LinearLayout? = null
    private var candidateBar: LinearLayout? = null
    private var candidateTextView: TextView? = null
    private var modeSwitchButton: Button? = null

    override fun onCreateInputView(): View {
        // Inflate custom keyboard view layout
        val rootView = layoutInflater.inflate(R.layout.keyboard_view, null) as LinearLayout
        keyboardContainer = rootView
        candidateBar = rootView.findViewById(R.id.candidate_bar)
        candidateTextView = rootView.findViewById(R.id.candidate_text)
        modeSwitchButton = rootView.findViewById(R.id.btn_mode_switch)

        setupKeyListeners(rootView)
        updateKeyboardUI()
        return rootView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        clearComposingBuffer()
        wijesekaraEngine.resetState()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        clearComposingBuffer()
        wijesekaraEngine.resetState()
    }

    /**
     * Handles key click events from the software keyboard UI.
     */
    fun onKeyInput(primaryCode: Int) {
        val ic: InputConnection = currentInputConnection ?: return

        when (primaryCode) {
            KEYCODE_DELETE -> handleBackspace(ic)
            KEYCODE_SPACE -> handleSpace(ic)
            KEYCODE_ENTER -> handleEnter(ic)
            KEYCODE_SHIFT -> handleShift()
            KEYCODE_MODE_SWITCH -> toggleKeyboardMode()
            else -> handleCharacterInput(primaryCode.toChar(), ic)
        }
    }

    private fun handleCharacterInput(char: Char, ic: InputConnection) {
        when (currentMode) {
            KeyboardMode.SINGLISH -> {
                // Buffer roman characters and show real-time Sinhala composing text
                val c = if (isShiftActive) char.uppercaseChar() else char
                composingBuffer.append(c)
                val transliterated = singlishEngine.transliterate(composingBuffer.toString())
                ic.setComposingText(transliterated, 1)
                updateCandidateBar(transliterated)
            }
            KeyboardMode.WIJESEKARA -> {
                // Direct Sinhala input via SLS 1134 Wijesekara state engine
                val action = wijesekaraEngine.processKey(char, isShiftActive)
                when (action) {
                    is WijesekaraLayoutEngine.EngineAction.Commit -> {
                        ic.commitText(action.text, 1)
                    }
                    is WijesekaraLayoutEngine.EngineAction.ReplacePrevious -> {
                        ic.deleteSurroundingText(action.charsToDelete, 0)
                        ic.commitText(action.text, 1)
                    }
                    is WijesekaraLayoutEngine.EngineAction.BufferPendingKombuva -> {
                        // Kombuva held in state until consonant is pressed
                    }
                }
            }
            KeyboardMode.ENGLISH -> {
                val out = if (isShiftActive) char.uppercaseChar() else char
                ic.commitText(out.toString(), 1)
            }
        }

        if (isShiftActive) {
            isShiftActive = false
            updateKeyboardUI()
        }
    }

    private fun handleBackspace(ic: InputConnection) {
        if (currentMode == KeyboardMode.SINGLISH && composingBuffer.isNotEmpty()) {
            composingBuffer.deleteCharAt(composingBuffer.length - 1)
            if (composingBuffer.isNotEmpty()) {
                val transliterated = singlishEngine.transliterate(composingBuffer.toString())
                ic.setComposingText(transliterated, 1)
                updateCandidateBar(transliterated)
            } else {
                ic.commitText("", 1)
                updateCandidateBar("")
            }
        } else {
            wijesekaraEngine.resetState()
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun handleSpace(ic: InputConnection) {
        if (currentMode == KeyboardMode.SINGLISH && composingBuffer.isNotEmpty()) {
            val transliterated = singlishEngine.transliterate(composingBuffer.toString())
            ic.commitText(transliterated, 1)
            clearComposingBuffer()
        }
        wijesekaraEngine.resetState()
        ic.commitText(" ", 1)
    }

    private fun handleEnter(ic: InputConnection) {
        if (currentMode == KeyboardMode.SINGLISH && composingBuffer.isNotEmpty()) {
            val transliterated = singlishEngine.transliterate(composingBuffer.toString())
            ic.commitText(transliterated, 1)
            clearComposingBuffer()
        }
        wijesekaraEngine.resetState()
        val editorInfo = currentInputEditorInfo
        val imeAction = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        if (imeAction != EditorInfo.IME_ACTION_NONE && imeAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(imeAction)
        } else {
            sendKeyChar('\n')
        }
    }

    private fun handleShift() {
        isShiftActive = !isShiftActive
        updateKeyboardUI()
    }

    private fun toggleKeyboardMode() {
        clearComposingBuffer()
        wijesekaraEngine.resetState()
        currentMode = when (currentMode) {
            KeyboardMode.SINGLISH -> KeyboardMode.WIJESEKARA
            KeyboardMode.WIJESEKARA -> KeyboardMode.ENGLISH
            KeyboardMode.ENGLISH -> KeyboardMode.SINGLISH
        }
        updateKeyboardUI()
    }

    private fun clearComposingBuffer() {
        composingBuffer.clear()
        updateCandidateBar("")
    }

    private fun updateCandidateBar(text: String) {
        if (candidateTextView != null) {
            candidateTextView?.text = text
            candidateBar?.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun updateKeyboardUI() {
        modeSwitchButton?.text = when (currentMode) {
            KeyboardMode.SINGLISH -> "සිං (Singlish)"
            KeyboardMode.WIJESEKARA -> "සිං (Wijesekara)"
            KeyboardMode.ENGLISH -> "English"
        }
        // Switch key label textures / layout rows based on currentMode and isShiftActive
    }

    private fun setupKeyListeners(rootView: View) {
        // Wire up soft keys, spacebar, backspace, and mode toggle buttons
    }

    companion object {
        const val KEYCODE_DELETE = -5
        const val KEYCODE_SHIFT = -1
        const val KEYCODE_SPACE = 32
        const val KEYCODE_ENTER = 10
        const val KEYCODE_MODE_SWITCH = -100
    }
}
