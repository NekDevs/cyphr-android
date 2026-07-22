package org.cyphr.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Collections
import java.util.LinkedHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cyphr.app.R
import org.cyphr.app.crypto.CryptoFeatureFlag
import org.cyphr.app.crypto.ExchangeBlob
import org.cyphr.app.crypto.PAYLOAD_BEGIN_DELIMITER
import org.cyphr.app.crypto.PAYLOAD_END_DELIMITER
import org.cyphr.app.crypto.PayloadDecoder
import org.cyphr.app.crypto.ProfileKeyManager
import org.cyphr.app.crypto.ReplayProtectionStore
import org.cyphr.app.crypto.SenderResolution
import org.cyphr.app.crypto.ContactKeyStore

class CyphrAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
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
        dismissOverlay()
        super.onDestroy()
    }

    private fun scanForPayloads() {
        val root = rootInActiveWindow ?: return

        val found = extractPayloadRecursive(root)
        root.recycle()

        if (found != null && found != lastPayload) {
            lastPayload = found
            tryDecrypt(found)
        }
    }

    private fun extractPayloadRecursive(node: AccessibilityNodeInfo): String? {
        if (node.isPassword || !node.isVisibleToUser) return null

        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        if (text.isNotEmpty()) {
            val match = PAYLOAD_REGEX.find(text)
            if (match != null) return match.value
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = extractPayloadRecursive(child)
            child.recycle()
            if (result != null) return result
        }

        return null
    }

    private fun tryDecrypt(payloadText: String) {
        if (!shownPayloads.add(payloadText)) return

        scope.launch {
            val ctx = this@CyphrAccessibilityService
            val result = withContext(Dispatchers.IO) {
                try {
                    val allUuids = ProfileKeyManager.listAllProfileUuids(ctx)
                    val activeUuid = ProfileKeyManager.loadActiveProfileUuid(ctx)
                    val orderedUuids = if (activeUuid != null) {
                        listOf(activeUuid) + allUuids.filter { it != activeUuid }
                    } else {
                        allUuids
                    }

                    for (uuid in orderedUuids) {
                        val keysets = mutableListOf<ByteArray>()
                        val currentKeys = ProfileKeyManager.loadProfileKeys(ctx, uuid)
                        if (currentKeys != null) keysets.add(currentKeys)
                        val prevKeys = ProfileKeyManager.loadPreviousProfileKeys(ctx, uuid)
                        if (prevKeys != null) keysets.add(prevKeys)

                        for (keysetBytes in keysets) {
                            val decoded = PayloadDecoder.decodePayload(
                                encodedPayload = payloadText,
                                recipientKeysetBytes = keysetBytes
                            )
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
                                        if (!accepted) return@withContext null
                                        resolution.displayName
                                    }
                                    else -> null
                                }

                                return@withContext DecryptResult(
                                    messageText = decoded.messageText.decodeToString(),
                                    senderName = senderName,
                                    profileName = profileName,
                                    payloadText = payloadText
                                )
                            }
                        }
                    }
                    null
                } catch (_: Exception) {
                    null
                }
            }

            if (result != null) {
                showOverlay(result)
            }
        }
    }

    private fun dpf(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun showOverlay(result: DecryptResult) {
        dismissOverlay()

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as? LayoutInflater ?: return

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val cardBg = if (isDark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
        val senderColor = if (isDark) 0xFF90CAF9.toInt() else 0xFF1976D2.toInt()
        val textColor = if (isDark) 0xFFE0E0E0.toInt() else 0xFF212121.toInt()
        val buttonColor = if (isDark) 0xFF90CAF9.toInt() else 0xFF1976D2.toInt()
        val dividerColor = if (isDark) 0xFF333333.toInt() else 0xFFE0E0E0.toInt()

        val cardDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(cardBg)
            val r = dpf(16f)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpf(20f).toInt(), dpf(12f).toInt(), dpf(20f).toInt(), dpf(12f).toInt())
            setOnClickListener { dismissOverlay() }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setOnApplyWindowInsetsListener { v, insets ->
                v.setPadding(
                    dpf(20f).toInt(),
                    dpf(12f).toInt(),
                    dpf(20f).toInt(),
                    dpf(12f).toInt() + insets.systemWindowInsetBottom
                )
                insets
            }
        }

        container.background = cardDrawable
        container.clipToOutline = true
        container.elevation = dpf(8f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            container.setOutlineAmbientShadowColor(0x40000000.toInt())
            container.setOutlineSpotShadowColor(0x40000000.toInt())
        }

        val senderText = if (result.senderName != null) {
            getString(R.string.overlay_from, result.senderName)
        } else {
            getString(R.string.overlay_from_unknown)
        }
        container.addView(TextView(this).apply {
            text = senderText
            setTextColor(senderColor)
            textSize = 14f
            setPadding(0, 0, 0, dpf(8f).toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })

        val serviceCtx = this@CyphrAccessibilityService
        container.addView(ScrollView(serviceCtx).apply {
            addView(TextView(serviceCtx).apply {
                text = result.messageText
                setTextColor(textColor)
                textSize = 16f
                setLineSpacing(dpf(4f), 1f)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
        })

        val divider = View(this).apply {
            setBackgroundColor(dividerColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpf(0.5f).toInt()
            ).also { it.topMargin = dpf(12f).toInt(); it.bottomMargin = dpf(8f).toInt() }
        }
        container.addView(divider)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        buttonRow.addView(Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.overlay_copy)
            setTextColor(buttonColor)
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
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })

        buttonRow.addView(Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.overlay_dismiss)
            setTextColor(buttonColor)
            setOnClickListener { dismissOverlay() }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })

        container.addView(buttonRow)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_SECURE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 0
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            horizontalMargin = 0f
            verticalMargin = 0f
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        try {
            pendingDismissRunnable?.let { mainHandler.removeCallbacks(it) }
            wm.addView(container, params)
            overlayView = container

            val dismissRunnable = Runnable { dismissOverlay() }
            pendingDismissRunnable = dismissRunnable
            mainHandler.postDelayed(dismissRunnable, 10_000L)
        } catch (_: Exception) {
        }
    }

    private fun dismissOverlay() {
        pendingDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingDismissRunnable = null
        overlayView?.let { view ->
            try {
                val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return@let
                wm.removeView(view)
            } catch (_: Exception) {}
        }
        overlayView = null
    }

    private data class DecryptResult(
        val messageText: String,
        val senderName: String?,
        val profileName: String,
        val payloadText: String
    )
}
