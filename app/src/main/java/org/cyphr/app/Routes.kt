package org.cyphr.app

import android.net.Uri

object Routes {
    const val ONBOARDING = "onboarding"
    const val LANDING = "landing"
    const val TRANSFORM = "transform"
    const val INSPECT = "inspect"
    const val MY_IDENTITY = "my_identity"
    const val CONTACTS = "contacts"
    const val PROFILES = "profiles"
    const val MESSAGE_LOG = "message_log"
    const val SETTINGS = "settings"
    const val SCAN_QR = "scan_qr"

    const val CONTACT_DETAIL = "contact/{contactUuid}"
    const val QR_CODE = "qr/{blob}"

    fun contactDetail(uuid: String) = "contact/$uuid"
    fun qrCode(blob: String) = "qr/${Uri.encode(blob)}"
}
