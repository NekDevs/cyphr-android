package org.cyphr.app.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Popup
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.cyphr.app.crypto.ContactKeyMeta
import org.cyphr.app.R

data class ContactInfo(
    val uuid: String,
    val displayName: String,
    val status: String,
    val keyChanged: Boolean
)

fun ContactKeyMeta.toContactInfo() = ContactInfo(
    uuid = contactUuid,
    displayName = displayName,
    status = status,
    keyChanged = keyChanged
)

private val COMMON_EMOJIS = listOf(
    "\uD83D\uDE00", "\uD83D\uDE03", "\uD83D\uDE04", "\uD83D\uDE01", "\uD83D\uDE06", "\uD83D\uDE02",
    "\uD83D\uDE0A", "\uD83D\uDE07", "\uD83D\uDE0D", "\uD83D\uDE18", "\uD83D\uDE17", "\uD83D\uDE19",
    "\uD83D\uDE12", "\uD83E\uDD14", "\uD83D\uDE0F", "\uD83D\uDE11", "\uD83D\uDE10", "\uD83D\uDE0E",
    "\u2764\uFE0F", "\uD83E\uDDE1", "\uD83D\uDC9B", "\uD83D\uDC9A", "\uD83D\uDC99", "\uD83D\uDC9C",
    "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4F", "\uD83D\uDC4A", "\uD83E\uDD1D", "\uD83D\uDCAA",
    "\uD83D\uDD25", "\u2B50", "\u2728", "\uD83C\uDF89", "\uD83C\uDF8A", "\uD83C\uDF82",
    "\u2615", "\uD83C\uDF55", "\uD83C\uDF54", "\uD83C\uDF2E", "\uD83C\uDF2D", "\uD83C\uDF6D",
    "\uD83D\uDCA1", "\uD83D\uDD11", "\u26A1", "\u23F0", "\uD83D\uDCCC", "\u2702\uFE0F",
    "\uD83D\uDE97", "\u2708\uFE0F", "\uD83D\uDEA2", "\uD83C\uDFE0", "\uD83C\uDF0D", "\u2600\uFE0F",
    "\uD83D\uDC4B", "\uD83E\uDD1F", "\uD83E\uDD1E", "\uD83D\uDE4C", "\uD83E\uDD32", "\uD83D\uDE4F",
    "\uD83D\uDE36", "\uD83D\uDE48", "\uD83D\uDC76", "\uD83D\uDC66", "\uD83D\uDC67", "\uD83D\uDC69",
    "\uD83D\uDC68", "\uD83E\uDDD3", "\uD83E\uDDD4", "\uD83E\uDDD1", "\uD83D\uDC36", "\uD83D\uDC31",
    "\uD83D\uDCA6", "\uD83E\uDDF8", "\uD83C\uDF1F", "\uD83C\uDF19", "\uD83C\uDF0A", "\uD83D\uDE80"
)

data class KeyboardColors(
    val bg: Color, val keyChar: Color, val keySpecial: Color, val keyText: Color,
    val lavender: Color, val lavenderText: Color, val encryptOn: Color,
    val previewBg: Color, val statusText: Color, val toolbarPill: Color,
)

val LocalKeyboardColors = staticCompositionLocalOf { darkKeyboardColors }
val LocalLayoutVariants = staticCompositionLocalOf<Map<Char, List<Char>>> { emptyMap() }
val LocalBufferState = staticCompositionLocalOf { "" }

val darkKeyboardColors = KeyboardColors(
    bg = Color(0xFF1B1C1E),
    keyChar = Color(0xFF303135),
    keySpecial = Color(0xFF212125),
    keyText = Color(0xFFF1F0F3),
    lavender = Color(0xFFD7D4E5),
    lavenderText = Color(0xFF1B1C1E),
    encryptOn = Color(0xFF4CAF50),
    previewBg = Color(0xFF252527),
    statusText = Color(0xFF8A8A8E),
    toolbarPill = Color(0xFF2C2C30),
)

val lightKeyboardColors = KeyboardColors(
    bg = Color(0xFFF5F5F5),
    keyChar = Color(0xFFE0E0E0),
    keySpecial = Color(0xFFD0D0D0),
    keyText = Color(0xFF1C1B1F),
    lavender = Color(0xFF6750A4),
    lavenderText = Color(0xFFFFFFFF),
    encryptOn = Color(0xFF2E7D32),
    previewBg = Color(0xFFE8E8E8),
    statusText = Color(0xFF6B6B6F),
    toolbarPill = Color(0xFFDADADA),
)

private val H_PAD = 6.dp
private val KEY_GAP = 6.dp
private val ROW_GAP = 6.dp
private val CORNER = 12.dp

@Composable
private fun currentScreenWidthDp(): Int {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    return with(density) { containerSize.width.toDp().value.roundToInt() }
}

private fun scaledKeyHeight(screenWidthDp: Int): Dp =
    (screenWidthDp / 6.5f).dp.coerceIn(44.dp, 60.dp)

private fun scaledToolbarHeight(screenWidthDp: Int): Dp =
    (screenWidthDp / 6f).dp.coerceIn(48.dp, 64.dp)

private fun scaledPreviewHeight(screenWidthDp: Int): Dp =
    (screenWidthDp / 8f).dp.coerceIn(36.dp, 48.dp)

private fun scaledEmojiPanelHeight(screenWidthDp: Int): Dp =
    (screenWidthDp * 0.55f).dp.coerceIn(160.dp, 250.dp)

@Composable
fun KeyboardScreen(
    selectedContact: ContactInfo?,
    availableContacts: List<ContactInfo>,
    currentLayout: KeyboardLayout,
    isShifted: Boolean,
    isEmojiMode: Boolean,
    layoutIndex: Int,
    isEncryptMode: Boolean,
    isCapsLock: Boolean,
    currentLanguageCode: String,
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onEncrypt: () -> Unit,
    onToggleEmoji: () -> Unit,
    onToggleShift: () -> Unit,
    onSelectContact: (String) -> Unit,
    onDeselectContact: () -> Unit,
    onToggleSymbols: () -> Unit,
    onPaste: () -> Unit,
    onToggleEncryptMode: () -> Unit,
    onCycleLanguage: () -> Unit,
    onCycleLanguageBackward: () -> Unit,
    modifier: Modifier = Modifier,
    showDecryptPopup: Boolean = false,
    decryptPayloadInput: String = "",
    decryptResult: String? = null,
    isDecrypting: Boolean = false,
    onToggleDecryptPopup: () -> Unit = {},
    onDecryptPayloadInputChange: (String) -> Unit = {},
    onDecryptPayload: () -> Unit = {}
) {
    val colors = if (isSystemInDarkTheme()) darkKeyboardColors else lightKeyboardColors

    CompositionLocalProvider(LocalKeyboardColors provides colors) {
    CompositionLocalProvider(LocalLayoutVariants provides currentLayout.variants) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(top = 4.dp, bottom = 48.dp)
    ) {
        Toolbar(
            selectedContact = selectedContact,
            availableContacts = availableContacts,
            onSelectContact = onSelectContact,
            onDeselectContact = onDeselectContact,
            onPaste = onPaste,
            isEncryptMode = isEncryptMode,
            onToggleEncryptMode = onToggleEncryptMode,
            showDecryptPopup = showDecryptPopup,
            onToggleDecryptPopup = onToggleDecryptPopup
        )

        if (showDecryptPopup) {
            DecryptPopupForm(
                input = decryptPayloadInput,
                onInputChange = onDecryptPayloadInputChange,
                onDecrypt = onDecryptPayload,
                isDecrypting = isDecrypting,
                result = decryptResult,
                onClose = onToggleDecryptPopup
            )
        }

        PreviewBar()

        StatusBar(
            selectedContact = selectedContact,
            layoutNameResId = currentLayout.nameResId
        )

        if (isEmojiMode) {
            EmojiPanel(
                onEmojiTap = onKeyPress,
                onBackToKeyboard = onToggleEmoji
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = H_PAD),
                verticalArrangement = Arrangement.spacedBy(ROW_GAP)
            ) {
                val shifted = isShifted && !currentLayout.isSymbols
                val r1 = if (shifted) currentLayout.row1.uppercase() else currentLayout.row1
                val r2 = if (shifted) currentLayout.row2.uppercase() else currentLayout.row2

                KeyRow(keys = r1, onKeyPress = onKeyPress, inset = false)
                KeyRow(keys = r2, onKeyPress = onKeyPress, inset = true)

                            Row3(
                    layout = currentLayout,
                    isShifted = isShifted,
                    isCapsLock = isCapsLock,
                    onToggleShift = onToggleShift,
                    onBackspace = onBackspace,
                    onKeyPress = onKeyPress
                )

                BottomRow(
                    layout = currentLayout,
                    hasContact = selectedContact != null,
                    isEncryptMode = isEncryptMode,
                    currentLanguageCode = currentLanguageCode,
                    onKeyPress = onKeyPress,
                    onToggleEmoji = onToggleEmoji,
                    onEncrypt = onEncrypt,
                    onToggleSymbols = onToggleSymbols,
                    onCycleLanguage = onCycleLanguage,
                    onCycleLanguageBackward = onCycleLanguageBackward
                )
            }
        }
    }
    }
    }
}

@Composable
private fun Toolbar(
    selectedContact: ContactInfo?,
    availableContacts: List<ContactInfo>,
    onSelectContact: (String) -> Unit,
    onDeselectContact: () -> Unit,
    onPaste: () -> Unit,
    isEncryptMode: Boolean,
    onToggleEncryptMode: () -> Unit,
    showDecryptPopup: Boolean = false,
    onToggleDecryptPopup: () -> Unit = {}
) {
    val c = LocalKeyboardColors.current
    val screenWidthDp = currentScreenWidthDp()
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaledToolbarHeight(screenWidthDp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.toolbarPill)
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.keyboard_to_label),
                        fontSize = 13.sp,
                        color = c.statusText
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = selectedContact?.displayName ?: "\u2014",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.keyText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "\u25BC",
                        fontSize = 10.sp,
                        color = c.statusText
                    )
                }
            }

            if (expanded) {
                Popup(
                    onDismissRequest = { expanded = false },
                    alignment = Alignment.BottomStart
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 180.dp, max = 260.dp)
                            .clip(RoundedCornerShape(CORNER))
                            .background(c.toolbarPill)
                            .padding(4.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(CORNER))
                                .clickable {
                                    onDeselectContact()
                                    expanded = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = stringResource(R.string.keyboard_no_contact),
                                fontSize = 14.sp,
                                color = c.statusText
                            )
                        }
                        availableContacts.forEach { contact ->
                            val statusIcon = when {
                                contact.keyChanged -> "\u26A0\uFE0F"
                                contact.status == "verified" -> "\u2714\uFE0F"
                                else -> "\u26AA"
                            }
                            val isSelected = contact.uuid == selectedContact?.uuid
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(CORNER))
                                    .background(if (isSelected) c.keyChar else Color.Transparent)
                                    .clickable {
                                        onSelectContact(contact.uuid)
                                        expanded = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "$statusIcon ${contact.displayName}",
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = c.keyText
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (isEncryptMode) c.encryptOn.copy(alpha = 0.15f) else c.toolbarPill)
                .clickable(onClick = onToggleEncryptMode)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isEncryptMode) stringResource(R.string.keyboard_encrypt_on) else stringResource(R.string.keyboard_encrypt_off),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isEncryptMode) c.encryptOn else c.statusText,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (showDecryptPopup) c.encryptOn.copy(alpha = 0.15f) else c.toolbarPill)
                .clickable(onClick = onToggleDecryptPopup)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.keyboard_decrypt_label),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (showDecryptPopup) c.encryptOn else c.statusText,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(CORNER))
                .background(c.keySpecial)
                .clickable(onClick = onPaste)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.keyboard_paste),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = c.keyText,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DecryptPopupForm(
    input: String,
    onInputChange: (String) -> Unit,
    onDecrypt: () -> Unit,
    isDecrypting: Boolean,
    result: String?,
    onClose: () -> Unit
) {
    val c = LocalKeyboardColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = H_PAD)
            .clip(RoundedCornerShape(CORNER))
            .background(c.previewBg)
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text(stringResource(R.string.keyboard_decrypt_hint), fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = c.keyText)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(CORNER))
                    .background(if (input.isBlank()) c.keySpecial else c.encryptOn)
                    .clickable(enabled = input.isNotBlank() && !isDecrypting) { onDecrypt() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isDecrypting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = c.keyText
                    )
                } else {
                    Text(
                        text = stringResource(R.string.keyboard_decrypt_label),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (input.isBlank()) c.statusText else Color.White
                    )
                }
            }
        }
        if (result != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result,
                    fontSize = 13.sp,
                    color = if (result.startsWith("Decrypted:")) c.encryptOn else Color(0xFFEF5350),
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.keySpecial)
                        .clickable(onClick = onClose)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\u2716",
                        fontSize = 12.sp,
                        color = c.statusText
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewBar() {
    val buffer = LocalBufferState.current
    val c = LocalKeyboardColors.current
    val screenWidthDp = currentScreenWidthDp()
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaledPreviewHeight(screenWidthDp))
            .padding(horizontal = H_PAD)
            .clip(RoundedCornerShape(8.dp))
            .background(c.previewBg)
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (buffer.isEmpty()) {
            Text(
                text = stringResource(R.string.keyboard_type_hint),
                color = c.statusText,
                fontSize = 14.sp
            )
        } else {
            Text(
                text = buffer,
                color = c.keyText,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusBar(
    selectedContact: ContactInfo?,
    layoutNameResId: Int
) {
    val c = LocalKeyboardColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedContact != null) {
            val icon = when {
                selectedContact.keyChanged -> "\u26A0\uFE0F"
                selectedContact.status == "verified" -> "\u2714\uFE0F"
                else -> "\u26AA"
            }
            Text(
                text = icon,
                fontSize = 11.sp,
                color = c.statusText
            )
            Spacer(Modifier.width(4.dp))
        } else {
            Text(
                text = "\u26D4",
                fontSize = 11.sp,
                color = c.statusText
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = stringResource(layoutNameResId),
            fontSize = 11.sp,
            color = c.statusText,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun KeyRow(
    keys: String,
    onKeyPress: (String) -> Unit,
    inset: Boolean
) {
    val screenWidthDp = currentScreenWidthDp()
    val kh = scaledKeyHeight(screenWidthDp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP)
    ) {
        if (inset) {
            Spacer(modifier = Modifier.weight(0.5f / (keys.length + 1f)))
        }
        val totalWeight = if (inset) keys.length + 1f else keys.length.toFloat()
        keys.forEach { c ->
            KeyButton(
                label = c.toString(),
                onSelectKey = onKeyPress,
                modifier = Modifier.weight(1f / totalWeight),
                keyHeight = kh
            )
        }
        if (inset) {
            Spacer(modifier = Modifier.weight(0.5f / (keys.length + 1f)))
        }
    }
}

@Composable
private fun Row3(
    layout: KeyboardLayout,
    isShifted: Boolean,
    isCapsLock: Boolean,
    onToggleShift: () -> Unit,
    onBackspace: () -> Unit,
    onKeyPress: (String) -> Unit
) {
    val c = LocalKeyboardColors.current
    val haptic = LocalHapticFeedback.current
    val screenWidthDp = currentScreenWidthDp()
    val shifted = isShifted && !layout.isSymbols
    val keys = if (shifted) layout.row3.uppercase() else layout.row3
    val kh = scaledKeyHeight(screenWidthDp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP)
    ) {
        val totalWeight = 10f

        if (!layout.isSymbols) {
            Box(
                modifier = Modifier
                    .weight(1.5f / totalWeight)
                    .height(kh)
                    .clip(RoundedCornerShape(CORNER))
                    .background(if (isCapsLock) c.lavender else if (isShifted) c.keyChar else c.keySpecial)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleShift()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u21E7",
                    fontSize = 21.sp,
                    fontWeight = if (isShifted || isCapsLock) FontWeight.Bold else FontWeight.Normal,
                    color = c.keyText,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1.5f / totalWeight))
        }

        keys.forEach { ch ->
            KeyButton(
                label = ch.toString(),
                onSelectKey = onKeyPress,
                modifier = Modifier.weight(1f / totalWeight)
            )
        }

        RepeatableSpecialKey(
            label = "\u232B",
            onTap = onBackspace,
            onRepeat = onBackspace,
            keyHeight = kh,
            modifier = Modifier.weight(1.5f / totalWeight)
        )
    }
}

@Composable
private fun BottomRow(
    layout: KeyboardLayout,
    hasContact: Boolean,
    isEncryptMode: Boolean,
    currentLanguageCode: String,
    onKeyPress: (String) -> Unit,
    onToggleEmoji: () -> Unit,
    onEncrypt: () -> Unit,
    onToggleSymbols: () -> Unit,
    onCycleLanguage: () -> Unit,
    onCycleLanguageBackward: () -> Unit
) {
    val c = LocalKeyboardColors.current
    val haptic = LocalHapticFeedback.current
    val screenWidthDp = currentScreenWidthDp()
    val bh = scaledKeyHeight(screenWidthDp)
    val totalWeight = 9f
    val currentOnCycleLanguage by rememberUpdatedState(onCycleLanguage)
    val currentOnCycleLanguageBackward by rememberUpdatedState(onCycleLanguageBackward)
    val canEncrypt = if (isEncryptMode) hasContact else true
    val langLabel = if (layout.isSymbols) stringResource(R.string.keyboard_show_alpha) else stringResource(R.string.keyboard_show_symbols)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP)
    ) {
        Box(
            modifier = Modifier
                .weight(1.5f / totalWeight)
                .height(bh)
                .clip(RoundedCornerShape(CORNER))
                .background(c.lavender)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSymbols()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = langLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.lavenderText,
                textAlign = TextAlign.Center
            )
        }

        Box(
            modifier = Modifier
                .weight(1f / totalWeight)
                .height(bh)
                .clip(RoundedCornerShape(CORNER))
                .background(c.keyChar)
                .combinedClickable(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onKeyPress(",")
                    },
                    onLongClick = onToggleEmoji
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "\u263A",
                    fontSize = 10.sp,
                    color = c.statusText
                )
                Text(
                    text = ",",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.keyText,
                    textAlign = TextAlign.Center
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(4f / totalWeight)
                .height(bh)
                .clip(RoundedCornerShape(CORNER))
                .background(c.keyChar)
                .pointerInput(Unit) {
                    var accumulatedDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { accumulatedDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDrag += dragAmount
                        },
                        onDragEnd = {
                            if (accumulatedDrag > 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnCycleLanguage()
                            } else if (accumulatedDrag < 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnCycleLanguageBackward()
                            }
                        }
                    )
                }
                .combinedClickable(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onKeyPress(" ")
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCycleLanguage()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentLanguageCode,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = c.keyText.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }

        KeyButton(
            label = ".",
            onSelectKey = onKeyPress,
            modifier = Modifier.weight(1f / totalWeight)
        )

        Box(
            modifier = Modifier
                .weight(1.5f / totalWeight)
                .height(bh)
                .clip(CircleShape)
                .background(
                    if (canEncrypt) {
                        if (isEncryptMode) c.encryptOn else c.lavender
                    } else c.lavender
                )
                .clickable(enabled = canEncrypt) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEncrypt()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isEncryptMode) "\uD83D\uDD12" else "\u2709\uFE0F",
                fontSize = 16.sp,
                color = if (canEncrypt) {
                    if (isEncryptMode) Color.White else c.lavenderText
                } else c.lavenderText,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    onSelectKey: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 56.dp
) {
    val c = LocalKeyboardColors.current
    val layoutVariants = LocalLayoutVariants.current
    val haptic = LocalHapticFeedback.current
    var showPopup by remember { mutableStateOf(false) }

    val variantChars = remember(label, layoutVariants) {
        if (label.length == 1) {
            layoutVariants[label[0]] ?: emptyList()
        } else emptyList()
    }

    Box(
        modifier = modifier
            .height(keyHeight)
            .clip(RoundedCornerShape(CORNER))
            .background(c.keyChar)
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelectKey(label)
                },
                onLongClick = {
                    if (variantChars.isNotEmpty()) {
                        showPopup = true
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            color = c.keyText,
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        if (showPopup) {
            Popup(
                alignment = Alignment.TopCenter,
                onDismissRequest = { showPopup = false }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.keySpecial)
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        variantChars.forEach { variant ->
                            Box(
                                modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(c.keyChar)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelectKey(variant.toString())
                                    showPopup = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(variant.toString(), fontSize = 19.sp, color = c.keyText)
                        }
                    }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun RepeatableSpecialKey(
    label: String,
    onTap: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 56.dp
) {
    val c = LocalKeyboardColors.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .height(keyHeight)
            .clip(RoundedCornerShape(CORNER))
            .background(c.keySpecial)
            .indication(interactionSource, ripple())
            .pointerInput(onTap, onRepeat) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = PressInteraction.Press(offset)
                        interactionSource.emit(press)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTap()
                        val repeatJob = scope.launch {
                            delay(400L)
                            while (true) {
                                onRepeat()
                                delay(60L)
                            }
                        }
                        try {
                            awaitRelease()
                            interactionSource.emit(PressInteraction.Release(press))
                        } catch (e: CancellationException) {
                            interactionSource.emit(PressInteraction.Cancel(press))
                        } finally {
                            repeatJob.cancel()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 21.sp,
            color = c.keyText,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmojiPanel(
    onEmojiTap: (String) -> Unit,
    onBackToKeyboard: () -> Unit
) {
    val c = LocalKeyboardColors.current
    val haptic = LocalHapticFeedback.current
    val screenWidthDp = currentScreenWidthDp()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = H_PAD)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier
                .fillMaxWidth()
                .height(scaledEmojiPanelHeight(screenWidthDp)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(COMMON_EMOJIS, key = { it }) { emoji ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.keyChar)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEmojiTap(emoji)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = 22.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(CORNER))
                    .background(c.keySpecial)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBackToKeyboard()
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                                            text = stringResource(R.string.keyboard_back_to_keyboard),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.lavender,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
