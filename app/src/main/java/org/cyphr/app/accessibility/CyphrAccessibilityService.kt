package org.cyphr.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cyphr.app.R
import org.cyphr.app.crypto.ContactKeyStore
import org.cyphr.app.crypto.CryptoFeatureFlag
import org.cyphr.app.crypto.ExchangeBlob
import org.cyphr.app.crypto.MessageLogStore
import org.cyphr.app.crypto.PAYLOAD_BEGIN_DELIMITER
import org.cyphr.app.crypto.PAYLOAD_END_DELIMITER
import org.cyphr.app.crypto.PayloadDecoder
import org.cyphr.app.crypto.ProfileKeyManager
import org.cyphr.app.crypto.ReplayProtectionStore
import org.cyphr.app.crypto.SenderResolution
import org.cyphr.app.crypto.StoredMessage

class CyphrAccessibilityService : AccessibilityService() {

    private companion object {
        private const val TAG = "CyphrAccessibility"
        private const val SCAN_COOLDOWN_MS = 500L
        private val ISO_8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastScanTimestamp = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val shownPayloads = Collections.newSetFromMap(
        object : LinkedHashMap<String, Boolean>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > 100
            }
        }
    )
    private var overlayView: View? = null
    private var lastPayload: String? = null
    private var pendingDismissRunnable: Runnable? = null
    private var pendingScanRunnable: Runnable? = null
    private var clipboardClearJob: Job? = null

    private val PAYLOAD_REGEX = Regex(
        "${PAYLOAD_BEGIN_DELIMITER}[A-Za-z0-9_-]+${PAYLOAD_END_DELIMITER}"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!CryptoFeatureFlag.isEnabled) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                scanForPayloads()
            }
            else -> {}
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        pendingScanRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingScanRunnable = null
        dismissOverlay()
        super.onDestroy()
    }

    private fun scanForPayloads() {
        val now = System.currentTimeMillis()
        if (now - lastScanTimestamp < SCAN_COOLDOWN_MS) return
        lastScanTimestamp = now
        pendingScanRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingScanRunnable = null

        val root = rootInActiveWindow ?: return

        val found = extractPayloadRecursive(root)
        root.recycle()

        if (found != null && found.payloadText != lastPayload) {
            lastPayload = found.payloadText
            tryDecrypt(found)
        } else if (found == null && overlayView == null) {
            val runnable = Runnable { scanForPayloads() }
            pendingScanRunnable = runnable
            mainHandler.postDelayed(runnable, 1_000L)
        }
    }

    private fun extractPayloadRecursive(node: AccessibilityNodeInfo): ScanResult? {
        if (node.isPassword) return null

        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        if (text.isNotEmpty()) {
            val match = PAYLOAD_REGEX.find(text)
            if (match != null) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                return ScanResult(payloadText = match.value, bounds = bounds)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = extractPayloadRecursive(child)
            child.recycle()
            if (result != null) return result
        }

        return null
    }

    private fun tryDecrypt(scanResult: ScanResult) {
        if (!shownPayloads.add(scanResult.payloadText)) return

        scope.launch {
            val ctx = this@CyphrAccessibilityService
            val result = withContext(Dispatchers.IO) {
                val allUuids = ProfileKeyManager.listAllProfileUuids(ctx)
                val activeUuid = ProfileKeyManager.loadActiveProfileUuid(ctx)
                val orderedUuids = if (activeUuid != null) {
                    listOf(activeUuid) + allUuids.filter { it != activeUuid }
                } else {
                    allUuids
                }

                for (uuid in orderedUuids) {
                    val keysets = mutableListOf<ByteArray>()
                    try {
                        val currentKeys = ProfileKeyManager.loadProfileKeys(ctx, uuid)
                        if (currentKeys != null) keysets.add(currentKeys)
                        val prevKeys = ProfileKeyManager.loadPreviousProfileKeys(ctx, uuid)
                        if (prevKeys != null) keysets.add(prevKeys)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load keys for profile $uuid: ${e::class.simpleName}")
                        continue
                    }

                    keysetLoop@ for (keysetBytes in keysets) {
                        val decoded = try {
                            PayloadDecoder.decodePayload(
                                encodedPayload = scanResult.payloadText,
                                recipientKeysetBytes = keysetBytes
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Decode failed for profile $uuid: ${e::class.simpleName}")
                            continue@keysetLoop
                        }
                        if (decoded != null) {
                            val meta = ProfileKeyManager.loadProfileMetadata(ctx, uuid)
                            val profileName = meta?.optString("displayName", uuid) ?: uuid

                            val resolution = ContactKeyStore.resolveSender(
                                ctx, uuid, decoded.senderPublicKeyBytes
                            )
                            val senderName: String? = when (resolution) {
                                is SenderResolution.KnownContact -> {
                                    val store = ReplayProtectionStore(ctx)
                                    val accepted = store.acceptReceivedCounter(
                                        uuid, resolution.contactUuid, decoded.replayCounter
                                    )
                                    if (!accepted) continue@keysetLoop
                                    resolution.displayName
                                }
                                else -> null
                            }

                            val fingerprint = if (decoded.senderPublicKeyBytes != null)
                                ExchangeBlob.shortFingerprint(decoded.senderPublicKeyBytes) else null
                            val timestamp = ISO_8601.format(Date())
                            val storedMsg = StoredMessage(
                                messageId = UUID.randomUUID().toString(),
                                profileUuid = uuid,
                                senderContactUuid = null,
                                senderDisplayName = senderName,
                                senderFingerprint = fingerprint,
                                replayCounter = decoded.replayCounter,
                                messageText = decoded.messageText.decodeToString(),
                                rawPayload = scanResult.payloadText,
                                decryptedAt = timestamp,
                                keyEpoch = decoded.keyEpoch
                            )
                            try {
                                MessageLogStore.saveMessage(ctx, storedMsg)
                            } catch (e: Exception) {
                                Log.w(TAG, "MessageLog save failed: ${e::class.simpleName}")
                            }

                            return@withContext DecryptResult(
                                messageText = decoded.messageText.decodeToString(),
                                senderName = senderName,
                                profileName = profileName,
                                payloadText = scanResult.payloadText,
                                bounds = scanResult.bounds
                            )
                        }
                    }
                }
                null
            }

            if (result != null) {
                Log.d(TAG, "Decrypt succeeded, showing bottom bar")
                showBottomBar(result)
            } else {
                Log.w(TAG, "Decrypt returned null, no bar shown")
            }
        }
    }

    private fun dpf(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun showBottomBar(result: DecryptResult) {
        dismissOverlay()

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val backdropColor = if (isDark) 0x99000000.toInt() else 0x88000000.toInt()
        val barBg = if (isDark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
        val senderColor = if (isDark) 0xFF90CAF9.toInt() else 0xFF1976D2.toInt()
        val textColor = if (isDark) 0xFFE0E0E0.toInt() else 0xFF212121.toInt()
        val captionColor = if (isDark) 0xFF9E9E9E.toInt() else 0xFF757575.toInt()
        val buttonColor = if (isDark) 0xFF90CAF9.toInt() else 0xFF1976D2.toInt()
        val dividerColor = if (isDark) 0xFF333333.toInt() else 0xFFE0E0E0.toInt()

        val barDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(barBg)
            val r = dpf(16f)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpf(16f).toInt(), dpf(12f).toInt(), dpf(16f).toInt(), dpf(8f).toInt())
            background = barDrawable
            clipToOutline = true
            elevation = dpf(8f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setOutlineAmbientShadowColor(0x40000000.toInt())
                setOutlineSpotShadowColor(0x40000000.toInt())
            }
            setOnClickListener {}
        }

        val senderText = if (result.senderName != null) {
            getString(R.string.overlay_from, result.senderName)
        } else {
            getString(R.string.overlay_from_unknown)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        headerRow.addView(TextView(this).apply {
            text = getString(R.string.overlay_sender_header, senderText)
            setTextColor(senderColor)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        headerRow.addView(Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.overlay_copy)
            setTextColor(buttonColor)
            textSize = 12f
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return@setOnClickListener
                val clip = android.content.ClipData.newPlainText("Cyphr decrypted", result.messageText)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    clip.description.extras = android.os.PersistableBundle().apply {
                        putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                }
                cm.setPrimaryClip(clip)
                clipboardClearJob?.cancel()
                clipboardClearJob = scope.launch {
                    delay(30_000L)
                    cm.clearPrimaryClip()
                }
            }
        })
        headerRow.addView(Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = "\u2716"
            setTextColor(captionColor)
            textSize = 14f
            setOnClickListener { dismissOverlay(userInitiated = true) }
        })
        bar.addView(headerRow)

        val dividerLine = View(this).apply {
            setBackgroundColor(dividerColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpf(0.5f).toInt()
            ).also { it.topMargin = dpf(6f).toInt(); it.bottomMargin = dpf(6f).toInt() }
        }
        bar.addView(dividerLine)

        bar.addView(TextView(this).apply {
            text = result.messageText
            setTextColor(textColor)
            textSize = 15f
            setLineSpacing(dpf(3f), 1f)
            maxLines = 5
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        val displayPayload = if (result.payloadText.length > 55) {
            result.payloadText.take(52) + "\u2026"
        } else {
            result.payloadText
        }
        bar.addView(TextView(this).apply {
            text = getString(R.string.overlay_payload, displayPayload)
            setTextColor(captionColor)
            textSize = 11f
            setPadding(0, dpf(4f).toInt(), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backdropColor)
            setOnClickListener { dismissOverlay(userInitiated = true) }
        }
        outer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        })
        outer.addView(bar)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        try {
            pendingDismissRunnable?.let { mainHandler.removeCallbacks(it) }
            wm.addView(outer, params)
            overlayView = outer

            val dismissRunnable = Runnable { dismissOverlay() }
            pendingDismissRunnable = dismissRunnable
            mainHandler.postDelayed(dismissRunnable, 30_000L)
        } catch (e: Exception) {
            Log.w(TAG, "Bottom bar show failed: ${e::class.simpleName}")
        }
    }

    private fun dismissOverlay(userInitiated: Boolean = false) {
        pendingDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingDismissRunnable = null
        overlayView?.let { view ->
            try {
                val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return@let
                wm.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "Overlay dismiss failed: ${e::class.simpleName}")
            }
        }
        overlayView = null
        if (userInitiated) {
            lastPayload = null
            shownPayloads.clear()
        }
    }

    private data class ScanResult(
        val payloadText: String,
        val bounds: Rect
    )

    private data class DecryptResult(
        val messageText: String,
        val senderName: String?,
        val profileName: String,
        val payloadText: String,
        val bounds: Rect
    )
}
