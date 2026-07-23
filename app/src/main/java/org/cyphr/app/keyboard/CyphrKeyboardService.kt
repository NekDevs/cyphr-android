package org.cyphr.app.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cyphr.app.AppSettings
import org.cyphr.app.R
import org.cyphr.app.crypto.ContactKeyStore
import org.cyphr.app.crypto.CryptoFeatureFlag
import org.cyphr.app.crypto.MessageLogStore
import org.cyphr.app.crypto.PayloadDecoder
import org.cyphr.app.crypto.PayloadEncoder
import org.cyphr.app.crypto.ProfileKeyManager
import org.cyphr.app.crypto.ReplayProtectionStore
import org.cyphr.app.crypto.StoredMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class CyphrKeyboardService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    private companion object {
        private val ISO_8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val _buf = StringBuilder()
    private var _buffer by mutableStateOf("")
    private var _isShifted by mutableStateOf(false)
    private var _isCapsLock by mutableStateOf(false)
    private var _lastShiftTapTime = 0L
    private var _firstCharOfSession by mutableStateOf(true)
    private var _isEmojiMode by mutableStateOf(false)
    private var _layoutIndex by mutableIntStateOf(0)
    private var _alphaLayoutIndex by mutableIntStateOf(0)
    private var _contacts by mutableStateOf<List<ContactInfo>>(emptyList())
    private var _selectedContact by mutableStateOf<ContactInfo?>(null)
    private var _isEncrypting by mutableStateOf(false)
    private var _isEncryptMode by mutableStateOf(false)
    private var _showDecryptPopup by mutableStateOf(false)
    private var _decryptPayloadInput by mutableStateOf("")
    private var _decryptResult by mutableStateOf<String?>(null)
    private var _isDecrypting by mutableStateOf(false)

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        _isEncryptMode = AppSettings.isEncryptModeEnabled(this)
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        val composeView = ComposeView(this)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        getWindow()?.window?.decorView?.apply {
            setViewTreeLifecycleOwner(this@CyphrKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@CyphrKeyboardService)
        }
        composeView.setContent {
            CompositionLocalProvider(LocalBufferState provides _buffer) {
            KeyboardScreen(
                selectedContact = _selectedContact,
                availableContacts = _contacts,
                currentLayout = KEYBOARD_LAYOUTS[_layoutIndex],
                isShifted = _isShifted || _isCapsLock || (_firstCharOfSession && _buffer.isEmpty()),
                isEmojiMode = _isEmojiMode,
                layoutIndex = _layoutIndex,
                isEncryptMode = _isEncryptMode,
                isCapsLock = _isCapsLock,
                currentLanguageCode = KEYBOARD_LAYOUTS[_layoutIndex].takeIf { !it.isSymbols }?.code?.uppercase()
                    ?: KEYBOARD_LAYOUTS[_alphaLayoutIndex].code.uppercase(),
                onKeyPress = { char ->
                    var committed = false
                    if (_firstCharOfSession && _buffer.isEmpty() && char.length == 1) {
                        _firstCharOfSession = false
                        val textBefore = currentInputConnection?.getTextBeforeCursor(1, 0)
                        if (textBefore == null || textBefore.isEmpty()) {
                            val upper = char.uppercase()
                            if (upper != char) {
                                _buf.append(upper)
                                _buffer = _buf.toString()
                                currentInputConnection?.commitText(upper, 1)
                                if (_isShifted && !_isCapsLock) _isShifted = false
                                committed = true
                            }
                        }
                    } else {
                        _firstCharOfSession = false
                    }
                    if (!committed) {
                        val before = currentInputConnection
                            ?.getTextBeforeCursor(_buffer.length + 1, 0)?.toString() ?: ""
                        if (_buffer.isNotEmpty() && before != _buffer) {
                            _buf.clear()
                            _buffer = ""
                        }
                        _buf.append(char)
                        _buffer = _buf.toString()
                        currentInputConnection?.commitText(char, 1)
                        if (_isShifted && !_isCapsLock) _isShifted = false
                    }
                },
                onBackspace = {
                    val selected = currentInputConnection?.getSelectedText(0)
                    if (selected != null && selected.isNotEmpty()) {
                        currentInputConnection?.commitText("", 1)
                        _buf.clear()
                        _buffer = ""
                    } else {
                        currentInputConnection?.deleteSurroundingText(1, 0)
                        if (_buf.isNotEmpty()) {
                            _buf.deleteCharAt(_buf.length - 1)
                            _buffer = _buf.toString()
                        }
                    }
                },
                onEncrypt = {
                    if (_isEncryptMode && _selectedContact != null) {
                        encryptCurrentBuffer()
                    } else {
                        triggerEditorAction()
                        _buf.clear()
                        _buffer = ""
                    }
                },
                onToggleEmoji = { _isEmojiMode = !_isEmojiMode },
                onToggleShift = {
                    _firstCharOfSession = false
                    val now = System.currentTimeMillis()
                    if (_isCapsLock) {
                        _isCapsLock = false
                        _isShifted = false
                    } else if (_isShifted && (now - _lastShiftTapTime) < 300) {
                        _isCapsLock = true
                        _isShifted = true
                    } else {
                        _isShifted = !_isShifted
                    }
                    _lastShiftTapTime = now
                },
                onSelectContact = { uuid -> selectContact(uuid) },
                onDeselectContact = { deselectContact() },
                onToggleSymbols = {
                    _layoutIndex = if (KEYBOARD_LAYOUTS[_layoutIndex].isSymbols) {
                        _alphaLayoutIndex
                    } else {
                        SYMBOLS_LAYOUT_INDEX
                    }
                },
                onPaste = {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = try { clipboard?.primaryClip } catch (_: SecurityException) { null }
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).text?.toString()
                        if (text != null) {
                            if (_isEncryptMode && _selectedContact != null) {
                                _buf.append(text)
                                _buffer = _buf.toString()
                                encryptCurrentBuffer()
                            } else {
                                _buf.append(text)
                                _buffer = _buf.toString()
                                currentInputConnection?.commitText(text, text.length)
                            }
                        }
                    }
                },
                onToggleEncryptMode = {
                    _isEncryptMode = !_isEncryptMode
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            AppSettings.setEncryptModeEnabled(
                                this@CyphrKeyboardService, _isEncryptMode
                            )
                        }
                    }
                },
                onCycleLanguage = { cycleLanguage(true) },
                onCycleLanguageBackward = { cycleLanguage(false) },
                showDecryptPopup = _showDecryptPopup,
                decryptPayloadInput = _decryptPayloadInput,
                decryptResult = _decryptResult,
                isDecrypting = _isDecrypting,
                onToggleDecryptPopup = {
                    _showDecryptPopup = !_showDecryptPopup
                    if (!_showDecryptPopup) {
                        _decryptPayloadInput = ""
                        _decryptResult = null
                    }
                },
                onDecryptPayloadInputChange = { _decryptPayloadInput = it },
                onDecryptPayload = { decryptKeyboardPayload() },
                modifier = Modifier.fillMaxWidth()
            )
            }
        }
        getWindow()?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        return composeView
    }

    override fun onStartInputView(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        if (!restarting) {
            _firstCharOfSession = true
        }
        syncLayoutFromSubtype()
        applySavedKeyboardLanguage()
        _isEncryptMode = AppSettings.isEncryptModeEnabled(this)
        loadContacts()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        _buf.clear()
        _buffer = ""
        _isEmojiMode = false
    }

    override fun onDestroy() {
        _buf.clear()
        _buffer = ""
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        scope.cancel()
        super.onDestroy()
    }

    private fun loadContacts() {
        scope.launch {
            val ctx = this@CyphrKeyboardService
            val profileUuid = withContext(Dispatchers.IO) {
                ProfileKeyManager.loadActiveProfileUuid(ctx)
            }
            if (profileUuid == null) return@launch

            val contacts = withContext(Dispatchers.IO) {
                ContactKeyStore.listContactKeys(ctx, profileUuid)
            }
            _contacts = contacts.map { it.toContactInfo() }

            val selectedUuid = withContext(Dispatchers.IO) {
                AppSettings.getSelectedContactUuid(ctx)
            }
            _selectedContact = _contacts.find { it.uuid == selectedUuid }
        }
    }

    private fun selectContact(uuid: String) {
        val contact = _contacts.find { it.uuid == uuid }
        if (contact != null) {
            _selectedContact = contact
            scope.launch {
                withContext(Dispatchers.IO) {
                    AppSettings.setSelectedContactUuid(
                        this@CyphrKeyboardService, contact.uuid, contact.displayName
                    )
                }
            }
        }
    }

    private fun deselectContact() {
        _selectedContact = null
    }

    private fun cycleLanguage(forward: Boolean = true) {
        val locales = resources.configuration.locales
        val systemCodes = (0 until locales.size())
            .map { locales.get(it).language.lowercase() }
            .toSet()

        val availableLayouts = KEYBOARD_LAYOUTS.filter { !it.isSymbols && systemCodes.contains(it.code) }
        val layoutsToUse = if (availableLayouts.isEmpty()) {
            KEYBOARD_LAYOUTS.filter { !it.isSymbols }
        } else {
            availableLayouts
        }

        if (layoutsToUse.size <= 1) return

        val currentCode = KEYBOARD_LAYOUTS[_layoutIndex].takeIf { !it.isSymbols }?.code
            ?: KEYBOARD_LAYOUTS[_alphaLayoutIndex].code
        val currentIndex = layoutsToUse.indexOfFirst { it.code == currentCode }
        val nextIndex = if (currentIndex < 0) {
            0
        } else if (forward) {
            (currentIndex + 1) % layoutsToUse.size
        } else {
            (currentIndex - 1 + layoutsToUse.size) % layoutsToUse.size
        }
        val nextLayout = layoutsToUse[nextIndex]

        val nextKeyIndex = KEYBOARD_LAYOUTS.indexOfFirst { it.code == nextLayout.code && !it.isSymbols }
        if (nextKeyIndex >= 0) {
            _alphaLayoutIndex = nextKeyIndex
            if (!KEYBOARD_LAYOUTS[_layoutIndex].isSymbols) {
                _layoutIndex = nextKeyIndex
            }
            _firstCharOfSession = true
            scope.launch {
                withContext(Dispatchers.IO) {
                    AppSettings.setKeyboardLanguageCode(
                        this@CyphrKeyboardService, nextLayout.code
                    )
                }
            }
        }
    }

    private fun applySavedKeyboardLanguage() {
        val savedCode = AppSettings.getKeyboardLanguageCode(this) ?: return
        val index = KEYBOARD_LAYOUTS.indexOfFirst { it.code == savedCode && !it.isSymbols }
        if (index >= 0) {
            _alphaLayoutIndex = index
            if (!KEYBOARD_LAYOUTS[_layoutIndex].isSymbols) {
                _layoutIndex = index
            }
        }
    }

    private fun triggerEditorAction() {
        currentInputConnection?.let { ic ->
            ic.finishComposingText()
            val options = currentInputEditorInfo?.imeOptions ?: 0
            val action = options and EditorInfo.IME_MASK_ACTION
            if (action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action)
            } else {
                ic.commitText("\n", 1)
            }
        }
    }

    override fun onCurrentInputMethodSubtypeChanged(subtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(subtype)
        syncLayoutFromSubtype(subtype)
    }

    private fun syncLayoutFromSubtype(subtype: InputMethodSubtype? = null) {
        val resolved = subtype
            ?: (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.currentInputMethodSubtype
            ?: return

        val bcp47 = resolved.languageTag.take(2).lowercase()
        if (bcp47.isEmpty()) return
        val index = KEYBOARD_LAYOUTS.indexOfFirst { it.code == bcp47 && !it.isSymbols }
        if (index >= 0) {
            _alphaLayoutIndex = index
            if (!KEYBOARD_LAYOUTS[_layoutIndex].isSymbols) _layoutIndex = index
        }
    }

    private fun encryptCurrentBuffer() {
        val selectedContact = _selectedContact
        if (_buffer.isBlank() || selectedContact == null || _isEncrypting) return
        if (!CryptoFeatureFlag.isEnabled) return

        _isEncrypting = true
        val plaintext = _buffer
        val plaintextLen = plaintext.length
        val contactUuid = selectedContact.uuid

        scope.launch {
            val ctx = this@CyphrKeyboardService
            val result = withContext(Dispatchers.IO) {
                try {
                    val profileUuid = ProfileKeyManager.loadActiveProfileUuid(ctx)
                        ?: return@withContext null as String?
                    val keysetBytes = ProfileKeyManager.loadProfileKeys(ctx, profileUuid)
                        ?: return@withContext null
                    val senderPublicKey = ProfileKeyManager.getPublicKey(keysetBytes)
                        ?: return@withContext null
                    val meta = ProfileKeyManager.loadProfileMetadata(ctx, profileUuid)
                        ?: return@withContext null
                    val senderEpoch = meta.optInt("keyEpoch", 1)

                    val recipientKey = ContactKeyStore.loadContactPublicKey(ctx, profileUuid, contactUuid)
                        ?: return@withContext null

                    val replayStore = ReplayProtectionStore(ctx)
                    val replayCounter = replayStore.nextSendCounter(profileUuid, contactUuid)

                    val encoded = PayloadEncoder.encodePayload(
                        plaintextMessage = plaintext.toByteArray(),
                        senderKeyEpoch = senderEpoch,
                        recipientPublicKeyBytes = recipientKey,
                        senderPublicKeyBytes = senderPublicKey,
                        replayCounter = replayCounter
                    ) ?: return@withContext null

                    val contactMeta = ContactKeyStore.getContact(ctx, profileUuid, contactUuid)
                    val sentMsg = StoredMessage(
                        messageId = UUID.randomUUID().toString(),
                        profileUuid = profileUuid,
                        senderContactUuid = contactUuid,
                        senderDisplayName = contactMeta?.displayName,
                        senderFingerprint = contactMeta?.shortFingerprint,
                        replayCounter = replayCounter,
                        messageText = plaintext,
                        rawPayload = encoded,
                        decryptedAt = ISO_8601.format(Date()),
                        keyEpoch = senderEpoch,
                        isOutgoing = true
                    )
                    MessageLogStore.saveMessage(ctx, sentMsg)

                    encoded
                } catch (_: Exception) {
                    null
                }
            }

            _isEncrypting = false
            if (result != null) {
                currentInputConnection?.deleteSurroundingText(plaintextLen, 0)
                currentInputConnection?.commitText(result, 1)
                _buf.clear()
                _buffer = ""
                triggerEditorAction()
            }
        }
    }

    private fun decryptKeyboardPayload() {
        if (_isDecrypting) return
        if (!CryptoFeatureFlag.isEnabled) return
        val input = _decryptPayloadInput.trim()
        if (input.isEmpty()) return

        _isDecrypting = true
        _decryptResult = null

        scope.launch {
            val ctx = this@CyphrKeyboardService
            val result = withContext(Dispatchers.IO) {
                try {
                    val profileUuid = ProfileKeyManager.loadActiveProfileUuid(ctx)
                        ?: return@withContext null as String?
                    val keysetBytes = ProfileKeyManager.loadProfileKeys(ctx, profileUuid)
                        ?: return@withContext null
                    val decoded = PayloadDecoder.decodePayload(input, keysetBytes)
                        ?: return@withContext null
                    "Decrypted: " + decoded.messageText.decodeToString()
                } catch (e: Exception) {
                    Log.w("CyphrKeyboard", "decryptKeyboardPayload failed", e)
                    null
                }
            }
            _isDecrypting = false
            _decryptResult = result ?: ctx.getString(R.string.keyboard_decrypt_failed)
        }
    }
}
