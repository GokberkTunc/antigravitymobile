package com.antigravity.mobile.core.mcp

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.SmsManager
import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.core.root.ShellEscaping
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction over Android platform APIs invoked by the MCP server's system tools.
 * Separated so JVM unit tests can mock or fake platform calls cleanly while production
 * executes real Android APIs strictly off the Main Thread.
 */
interface AndroidSystemToolGateway {

    suspend fun screenCapture(scale: Double, format: String): JsonObject

    suspend fun appLaunchOrManage(
        action: String,
        packageName: String?,
        intentUri: String?,
    ): JsonObject

    suspend fun phoneCallControl(
        action: String,
        phoneNumber: String?,
        enabled: Boolean?,
    ): JsonObject

    suspend fun smsManage(
        action: String,
        address: String?,
        body: String?,
        limit: Int,
    ): JsonObject

    suspend fun contactsCalendarQuery(
        target: String,
        action: String,
        query: String?,
        payload: JsonObject?,
        limit: Int,
    ): JsonObject

    suspend fun deviceControl(
        action: String,
        key: String?,
        value: String?,
    ): JsonObject

    suspend fun mediaLocationCapture(
        action: String,
        lens: String?,
        durationSec: Int,
    ): JsonObject
}

@Singleton
class PlatformAndroidSystemToolGateway(
    private val context: Context,
    private val rootExecutor: RootExecutor,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidSystemToolGateway {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        rootExecutor: RootExecutor,
    ) : this(
        context = context,
        rootExecutor = rootExecutor,
        ioDispatcher = Dispatchers.IO,
    )

    override suspend fun screenCapture(scale: Double, format: String): JsonObject =
        withContext(ioDispatcher) {
            val safeScale = scale.coerceIn(0.1, 1.0)
            val normalizedFormat = if (format.equals("png", ignoreCase = true)) "png" else "webp"
            val captureDir = File(context.cacheDir, "mcp_captures").apply { mkdirs() }
            val rawFile = File(captureDir, "raw_${System.currentTimeMillis()}.png")
            val outFile = File(captureDir, "cap_${System.currentTimeMillis()}.$normalizedFormat")

            try {
                val res = rootExecutor.execArgv(listOf("screencap", "-p", rawFile.absolutePath))
                if (!res.isSuccess || !rawFile.exists()) {
                    throw IllegalStateException("screencap failed: ${res.stderr.ifBlank { "exit=${res.exitCode}" }}")
                }

                val original = BitmapFactory.decodeFile(rawFile.absolutePath)
                    ?: throw IllegalStateException("Failed to decode captured PNG")
                val targetWidth = (original.width * safeScale).toInt().coerceAtLeast(1)
                val targetHeight = (original.height * safeScale).toInt().coerceAtLeast(1)
                val scaled = if (safeScale < 0.999) {
                    Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
                } else {
                    original
                }

                val compressFormat = when {
                    normalizedFormat == "png" -> Bitmap.CompressFormat.PNG
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Bitmap.CompressFormat.WEBP_LOSSY
                    else -> @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                }

                val bytesOut = ByteArrayOutputStream()
                scaled.compress(compressFormat, 80, bytesOut)
                val encodedBytes = bytesOut.toByteArray()
                outFile.writeBytes(encodedBytes)

                if (scaled !== original) scaled.recycle()
                original.recycle()

                buildJsonObject {
                    put("path", outFile.absolutePath)
                    put("format", normalizedFormat)
                    put("width", targetWidth)
                    put("height", targetHeight)
                    put("size_bytes", encodedBytes.size)
                    put("base64", Base64.getEncoder().encodeToString(encodedBytes))
                }
            } finally {
                rawFile.delete()
            }
        }

    override suspend fun appLaunchOrManage(
        action: String,
        packageName: String?,
        intentUri: String?,
    ): JsonObject = withContext(ioDispatcher) {
        when (action.lowercase()) {
            "list" -> {
                val pm = context.packageManager
                @Suppress("DEPRECATION")
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .sortedBy { it.packageName }
                buildJsonObject {
                    put("count", apps.size)
                    put(
                        "packages",
                        buildJsonArray {
                            apps.forEach { app ->
                                add(
                                    buildJsonObject {
                                        put("package_name", app.packageName)
                                        put("label", pm.getApplicationLabel(app)?.toString().orEmpty())
                                        put("enabled", app.enabled)
                                    },
                                )
                            }
                        },
                    )
                }
            }

            "launch" -> {
                if (!intentUri.isNullOrBlank()) {
                    val intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    buildJsonObject {
                        put("launched", true)
                        put("intent_uri", intentUri)
                    }
                } else {
                    val safePkg = ShellEscaping.validatePackageName(
                        requireNotNull(packageName) { "package_name or intent_uri is required for launch" },
                    )
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(safePkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        buildJsonObject {
                            put("launched", true)
                            put("package_name", safePkg)
                        }
                    } else {
                        val res = rootExecutor.execArgv(
                            listOf("monkey", "-p", safePkg, "-c", "android.intent.category.LAUNCHER", "1"),
                        )
                        buildJsonObject {
                            put("launched", res.isSuccess)
                            put("package_name", safePkg)
                            put("stdout", res.stdout)
                            put("stderr", res.stderr)
                        }
                    }
                }
            }

            "force_stop" -> {
                val safePkg = ShellEscaping.validatePackageName(
                    requireNotNull(packageName) { "package_name is required for force_stop" },
                )
                val res = rootExecutor.execArgv(listOf("am", "force-stop", safePkg))
                buildJsonObject {
                    put("success", res.isSuccess)
                    put("package_name", safePkg)
                    put("stderr", res.stderr)
                }
            }

            "clear_data" -> {
                val safePkg = ShellEscaping.validatePackageName(
                    requireNotNull(packageName) { "package_name is required for clear_data" },
                )
                val res = rootExecutor.execArgv(listOf("pm", "clear", safePkg))
                buildJsonObject {
                    put("success", res.isSuccess)
                    put("package_name", safePkg)
                    put("stdout", res.stdout)
                    put("stderr", res.stderr)
                }
            }

            "grant_perms" -> {
                val safePkg = ShellEscaping.validatePackageName(
                    requireNotNull(packageName) { "package_name is required for grant_perms" },
                )
                val report = rootExecutor.bootstrapPermissions(packageName = safePkg)
                buildJsonObject {
                    put("package_name", report.packageName)
                    put("restricted_settings_granted", report.restrictedSettingsGranted)
                    put("runtime_permissions_granted", report.runtimePermissionsGranted.size)
                    put("app_ops_granted", report.appOpsGranted.size)
                }
            }

            else -> throw IllegalArgumentException("Unsupported app_launch_or_manage action: $action")
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun phoneCallControl(
        action: String,
        phoneNumber: String?,
        enabled: Boolean?,
    ): JsonObject = withContext(ioDispatcher) {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        when (action.lowercase()) {
            "dial" -> {
                val number = requireNotNull(phoneNumber?.trim()?.takeIf { it.isNotEmpty() }) {
                    "phone_number is required for dial"
                }
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                buildJsonObject {
                    put("action", "dial")
                    put("phone_number", number)
                    put("success", true)
                }
            }

            "call" -> {
                val number = requireNotNull(phoneNumber?.trim()?.takeIf { it.isNotEmpty() }) {
                    "phone_number is required for call"
                }
                val uri = Uri.parse("tel:${Uri.encode(number)}")
                if (telecom != null) {
                    telecom.placeCall(uri, null)
                } else {
                    val intent = Intent(Intent.ACTION_CALL, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                buildJsonObject {
                    put("action", "call")
                    put("phone_number", number)
                    put("success", true)
                }
            }

            "end_call" -> {
                @Suppress("DEPRECATION")
                val ended = telecom?.endCall() ?: false
                buildJsonObject {
                    put("action", "end_call")
                    put("success", ended)
                }
            }

            "speaker_on" -> {
                val target = enabled ?: true
                @Suppress("DEPRECATION")
                audio?.isSpeakerphoneOn = target
                buildJsonObject {
                    put("action", "speaker_on")
                    put("enabled", target)
                    put("success", audio != null)
                }
            }

            "mute" -> {
                val target = enabled ?: true
                audio?.isMicrophoneMute = target
                buildJsonObject {
                    put("action", "mute")
                    put("enabled", target)
                    put("success", audio != null)
                }
            }

            else -> throw IllegalArgumentException("Unsupported phone_call_control action: $action")
        }
    }

    override suspend fun smsManage(
        action: String,
        address: String?,
        body: String?,
        limit: Int,
    ): JsonObject = withContext(ioDispatcher) {
        val safeLimit = limit.coerceIn(1, 200)
        when (action.lowercase()) {
            "list", "read" -> {
                val selection = if (!address.isNullOrBlank()) "${Telephony.Sms.ADDRESS} LIKE ?" else null
                val selectionArgs = if (!address.isNullOrBlank()) arrayOf("%${address.trim()}%") else null
                val messages = mutableListOf<JsonObject>()
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(
                        Telephony.Sms._ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.TYPE,
                    ),
                    selection,
                    selectionArgs,
                    "${Telephony.Sms.DATE} DESC",
                )?.use { cursor ->
                    while (cursor.moveToNext() && messages.size < safeLimit) {
                        messages += buildJsonObject {
                            put("id", cursor.getLong(0))
                            put("address", cursor.getString(1).orEmpty())
                            put("body", cursor.getString(2).orEmpty())
                            put("date_ms", cursor.getLong(3))
                            put("type", cursor.getInt(4))
                        }
                    }
                }
                buildJsonObject {
                    put("count", messages.size)
                    put("messages", JsonArray(messages))
                }
            }

            "send" -> {
                val targetAddress = requireNotNull(address?.trim()?.takeIf { it.isNotEmpty() }) {
                    "address is required for sms_manage send"
                }
                val textBody = requireNotNull(body?.takeIf { it.isNotEmpty() }) {
                    "body is required for sms_manage send"
                }
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                } ?: throw IllegalStateException("SmsManager unavailable")

                val parts = smsManager.divideMessage(textBody)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(targetAddress, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(targetAddress, null, textBody, null, null)
                }
                buildJsonObject {
                    put("sent", true)
                    put("address", targetAddress)
                    put("parts", parts.size)
                }
            }

            else -> throw IllegalArgumentException("Unsupported sms_manage action: $action")
        }
    }

    override suspend fun contactsCalendarQuery(
        target: String,
        action: String,
        query: String?,
        payload: JsonObject?,
        limit: Int,
    ): JsonObject = withContext(ioDispatcher) {
        val safeLimit = limit.coerceIn(1, 200)
        when (target.lowercase()) {
            "contacts" -> when (action.lowercase()) {
                "query" -> {
                    val selection = if (!query.isNullOrBlank()) {
                        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
                    } else null
                    val selectionArgs = if (!query.isNullOrBlank()) {
                        val pattern = "%${query.trim()}%"
                        arrayOf(pattern, pattern)
                    } else null

                    val items = mutableListOf<JsonObject>()
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ),
                        selection,
                        selectionArgs,
                        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
                    )?.use { cursor ->
                        while (cursor.moveToNext() && items.size < safeLimit) {
                            items += buildJsonObject {
                                put("contact_id", cursor.getLong(0))
                                put("display_name", cursor.getString(1).orEmpty())
                                put("phone_number", cursor.getString(2).orEmpty())
                            }
                        }
                    }
                    buildJsonObject {
                        put("target", "contacts")
                        put("count", items.size)
                        put("items", JsonArray(items))
                    }
                }

                "insert" -> {
                    val name = payload?.get("display_name")?.jsonPrimitive?.contentOrNull
                        ?: throw IllegalArgumentException("payload.display_name is required")
                    val phone = payload["phone_number"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val ops = arrayListOf(
                        ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                            .build(),
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                            )
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                            .build(),
                    )
                    if (phone.isNotEmpty()) {
                        ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                            )
                            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                            .withValue(
                                ContactsContract.CommonDataKinds.Phone.TYPE,
                                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                            )
                            .build()
                    }
                    context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                    buildJsonObject {
                        put("target", "contacts")
                        put("inserted", true)
                        put("display_name", name)
                    }
                }

                else -> throw IllegalArgumentException("Unsupported contacts action: $action")
            }

            "calendar" -> when (action.lowercase()) {
                "query" -> {
                    val selection = if (!query.isNullOrBlank()) {
                        "${CalendarContract.Events.TITLE} LIKE ? OR ${CalendarContract.Events.DESCRIPTION} LIKE ?"
                    } else null
                    val selectionArgs = if (!query.isNullOrBlank()) {
                        val pattern = "%${query.trim()}%"
                        arrayOf(pattern, pattern)
                    } else null
                    val items = mutableListOf<JsonObject>()
                    context.contentResolver.query(
                        CalendarContract.Events.CONTENT_URI,
                        arrayOf(
                            CalendarContract.Events._ID,
                            CalendarContract.Events.TITLE,
                            CalendarContract.Events.DTSTART,
                            CalendarContract.Events.DTEND,
                            CalendarContract.Events.EVENT_LOCATION,
                        ),
                        selection,
                        selectionArgs,
                        "${CalendarContract.Events.DTSTART} DESC",
                    )?.use { cursor ->
                        while (cursor.moveToNext() && items.size < safeLimit) {
                            items += buildJsonObject {
                                put("event_id", cursor.getLong(0))
                                put("title", cursor.getString(1).orEmpty())
                                put("start_ms", cursor.getLong(2))
                                put("end_ms", cursor.getLong(3))
                                put("location", cursor.getString(4).orEmpty())
                            }
                        }
                    }
                    buildJsonObject {
                        put("target", "calendar")
                        put("count", items.size)
                        put("items", JsonArray(items))
                    }
                }

                "insert" -> {
                    val title = payload?.get("title")?.jsonPrimitive?.contentOrNull
                        ?: throw IllegalArgumentException("payload.title is required")
                    val startMs = payload["start_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        ?: System.currentTimeMillis()
                    val endMs = payload["end_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        ?: (startMs + 3_600_000L)
                    val calendarId = payload["calendar_id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 1L
                    val values = ContentValues().apply {
                        put(CalendarContract.Events.CALENDAR_ID, calendarId)
                        put(CalendarContract.Events.TITLE, title)
                        put(CalendarContract.Events.DTSTART, startMs)
                        put(CalendarContract.Events.DTEND, endMs)
                        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                        payload["description"]?.jsonPrimitive?.contentOrNull?.let {
                            put(CalendarContract.Events.DESCRIPTION, it)
                        }
                    }
                    val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    buildJsonObject {
                        put("target", "calendar")
                        put("inserted", uri != null)
                        put("uri", uri?.toString().orEmpty())
                    }
                }

                else -> throw IllegalArgumentException("Unsupported calendar action: $action")
            }

            else -> throw IllegalArgumentException("Unsupported contacts_calendar_query target: $target")
        }
    }

    override suspend fun deviceControl(
        action: String,
        key: String?,
        value: String?,
    ): JsonObject = withContext(ioDispatcher) {
        when (action.lowercase()) {
            "lock" -> {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                val lockedByAdmin = try {
                    dpm?.lockNow()
                    dpm != null
                } catch (_: SecurityException) {
                    false
                }
                if (!lockedByAdmin) {
                    val res = rootExecutor.execArgv(listOf("input", "keyevent", "26"))
                    buildJsonObject {
                        put("action", "lock")
                        put("success", res.isSuccess)
                        put("via", "root_keyevent")
                    }
                } else {
                    buildJsonObject {
                        put("action", "lock")
                        put("success", true)
                        put("via", "device_policy_manager")
                    }
                }
            }

            "wake" -> {
                val res = rootExecutor.execArgv(listOf("input", "keyevent", "224"))
                buildJsonObject {
                    put("action", "wake")
                    put("success", res.isSuccess)
                }
            }

            "get_setting" -> {
                val (namespace, settingKey) = parseSettingKey(key)
                val res = rootExecutor.execArgv(listOf("settings", "get", namespace, settingKey))
                buildJsonObject {
                    put("namespace", namespace)
                    put("key", settingKey)
                    put("value", res.stdout.trim())
                    put("success", res.isSuccess)
                }
            }

            "put_setting" -> {
                val (namespace, settingKey) = parseSettingKey(key)
                val settingValue = requireNotNull(value) { "value is required for put_setting" }
                val res = rootExecutor.execArgv(listOf("settings", "put", namespace, settingKey, settingValue))
                buildJsonObject {
                    put("namespace", namespace)
                    put("key", settingKey)
                    put("value", settingValue)
                    put("success", res.isSuccess)
                }
            }

            "get_clip" -> {
                val clipText = runOnMainThread {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                }.orEmpty()
                buildJsonObject {
                    put("action", "get_clip")
                    put("text", clipText)
                }
            }

            "set_clip" -> {
                val text = requireNotNull(value) { "value is required for set_clip" }
                runOnMainThread {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("antigravity", text))
                }
                buildJsonObject {
                    put("action", "set_clip")
                    put("success", true)
                }
            }

            "wifi" -> {
                val mode = if (value.equals("off", true) || value.equals("disable", true) || value == "0" || value.equals("false", true)) {
                    "disable"
                } else {
                    "enable"
                }
                val res = rootExecutor.execArgv(listOf("svc", "wifi", mode))
                buildJsonObject {
                    put("action", "wifi")
                    put("mode", mode)
                    put("success", res.isSuccess)
                }
            }

            "bluetooth" -> {
                val mode = if (value.equals("off", true) || value.equals("disable", true) || value == "0" || value.equals("false", true)) {
                    "disable"
                } else {
                    "enable"
                }
                val res = rootExecutor.execArgv(listOf("svc", "bluetooth", mode))
                buildJsonObject {
                    put("action", "bluetooth")
                    put("mode", mode)
                    put("success", res.isSuccess)
                }
            }

            "volume" -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    ?: throw IllegalStateException("AudioManager unavailable")
                val stream = when (key?.lowercase()) {
                    "ring" -> AudioManager.STREAM_RING
                    "alarm" -> AudioManager.STREAM_ALARM
                    "call", "voice" -> AudioManager.STREAM_VOICE_CALL
                    "notification" -> AudioManager.STREAM_NOTIFICATION
                    else -> AudioManager.STREAM_MUSIC
                }
                val maxVol = audio.getStreamMaxVolume(stream)
                val targetVol = value?.toIntOrNull()?.coerceIn(0, maxVol)
                if (targetVol != null) {
                    audio.setStreamVolume(stream, targetVol, 0)
                }
                buildJsonObject {
                    put("action", "volume")
                    put("stream", key ?: "music")
                    put("current", audio.getStreamVolume(stream))
                    put("max", maxVol)
                }
            }

            else -> throw IllegalArgumentException("Unsupported device_control action: $action")
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun mediaLocationCapture(
        action: String,
        lens: String?,
        durationSec: Int,
    ): JsonObject = withContext(ioDispatcher) {
        when (action.lowercase()) {
            "get_location" -> {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    ?: throw IllegalStateException("LocationManager unavailable")
                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER,
                )
                var best: Location? = null
                for (provider in providers) {
                    val loc = try {
                        lm.getLastKnownLocation(provider)
                    } catch (_: SecurityException) {
                        null
                    }
                    if (loc != null && (best == null || loc.time > best.time)) {
                        best = loc
                    }
                }
                if (best == null) {
                    buildJsonObject {
                        put("available", false)
                        put("message", "No last known location available")
                    }
                } else {
                    buildJsonObject {
                        put("available", true)
                        put("latitude", best.latitude)
                        put("longitude", best.longitude)
                        put("accuracy_m", best.accuracy.toDouble())
                        put("provider", best.provider.orEmpty())
                        put("timestamp_ms", best.time)
                    }
                }
            }

            "take_photo" -> {
                val captureDir = File(context.cacheDir, "mcp_media").apply { mkdirs() }
                val photoFile = File(captureDir, "photo_${System.currentTimeMillis()}.jpg")
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("android.intent.extras.CAMERA_FACING", if (lens.equals("front", true)) 1 else 0)
                    putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile))
                }
                context.startActivity(intent)
                buildJsonObject {
                    put("action", "take_photo")
                    put("lens", if (lens.equals("front", true)) "front" else "back")
                    put("output_path", photoFile.absolutePath)
                    put("launched_camera", true)
                }
            }

            "record_audio" -> {
                val safeDuration = durationSec.coerceIn(1, 30)
                val sampleRate = 16_000
                val minBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(3200)

                val captureDir = File(context.cacheDir, "mcp_media").apply { mkdirs() }
                val outFile = File(captureDir, "audio_${System.currentTimeMillis()}.pcm")

                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf,
                )
                val totalBytesTarget = sampleRate * 2 * safeDuration
                var writtenBytes = 0
                try {
                    recorder.startRecording()
                    FileOutputStream(outFile).use { fos ->
                        val chunk = ByteArray(minBuf)
                        while (writtenBytes < totalBytesTarget) {
                            val read = recorder.read(chunk, 0, minOf(chunk.size, totalBytesTarget - writtenBytes))
                            if (read <= 0) break
                            fos.write(chunk, 0, read)
                            writtenBytes += read
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } finally {
                    try {
                        recorder.stop()
                    } catch (_: Throwable) {
                    }
                    recorder.release()
                }
                buildJsonObject {
                    put("action", "record_audio")
                    put("path", outFile.absolutePath)
                    put("sample_rate_hz", sampleRate)
                    put("duration_sec", safeDuration)
                    put("bytes_recorded", writtenBytes)
                }
            }

            else -> throw IllegalArgumentException("Unsupported media_location_capture action: $action")
        }
    }

    private fun parseSettingKey(rawKey: String?): Pair<String, String> {
        val cleaned = requireNotNull(rawKey?.trim()?.takeIf { it.isNotEmpty() }) {
            "key is required (format: namespace/key or key)"
        }
        val parts = cleaned.split("/", limit = 2)
        val (ns, name) = if (parts.size == 2) {
            parts[0].lowercase() to parts[1]
        } else {
            "system" to parts[0]
        }
        require(ns in ALLOWED_SETTING_NAMESPACES) {
            "Invalid settings namespace '$ns'; expected one of $ALLOWED_SETTING_NAMESPACES"
        }
        return ns to ShellEscaping.validateIdentifier(name)
    }

    private fun <T> runOnMainThread(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return block()
        }
        var result: T? = null
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try {
                result = block()
            } finally {
                latch.countDown()
            }
        }
        latch.await(3, TimeUnit.SECONDS)
        return result
    }

    companion object {
        private val ALLOWED_SETTING_NAMESPACES = setOf("global", "secure", "system")
    }
}
