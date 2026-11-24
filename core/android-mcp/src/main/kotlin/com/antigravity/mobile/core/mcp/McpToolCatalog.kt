package com.antigravity.mobile.core.mcp

import com.antigravity.mobile.contracts.McpToolDefinition
import com.antigravity.mobile.contracts.McpToolNames
import com.antigravity.mobile.contracts.McpToolRisk

/**
 * Authoritative catalog of all 13 mandatory Android System MCP tool categories (15 callable tool
 * names including `fs_read`, `fs_write`, `fs_list`) matching `SPEC_ANTIGRAVITY_MOBILE.md` §4.4.
 */
object McpToolCatalog {

    val allTools: List<McpToolDefinition> = listOf(
        // 1. root_exec (CRITICAL)
        McpToolDefinition(
            name = McpToolNames.ROOT_EXEC,
            description = "Execute a privileged shell command with root access via libsu; returns stdout, stderr, and exitCode.",
            riskLevel = McpToolRisk.CRITICAL,
            inputSchemaJson = """{"type":"object","properties":{"command":{"type":"string","description":"Shell command to execute"},"timeout_ms":{"type":"integer","default":30000},"cwd":{"type":"string"}},"required":["command"]}""",
        ),
        // 2a. fs_read (HIGH)
        McpToolDefinition(
            name = McpToolNames.FS_READ,
            description = "Read a file anywhere on the Android filesystem (including /sdcard and /data/data) via root SuFile.",
            riskLevel = McpToolRisk.HIGH,
            inputSchemaJson = """{"type":"object","properties":{"path":{"type":"string"},"offset":{"type":"integer","default":0},"limit":{"type":"integer","default":65536}},"required":["path"]}""",
        ),
        // 2b. fs_write (HIGH)
        McpToolDefinition(
            name = McpToolNames.FS_WRITE,
            description = "Write or append UTF-8 content to a file anywhere on the Android filesystem via root SuFile.",
            riskLevel = McpToolRisk.HIGH,
            inputSchemaJson = """{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"},"append":{"type":"boolean","default":false}},"required":["path","content"]}""",
        ),
        // 2c. fs_list (HIGH)
        McpToolDefinition(
            name = McpToolNames.FS_LIST,
            description = "List entries in a directory anywhere on the Android filesystem via root SuFile.",
            riskLevel = McpToolRisk.HIGH,
            inputSchemaJson = """{"type":"object","properties":{"path":{"type":"string"},"offset":{"type":"integer","default":0},"limit":{"type":"integer","default":200}},"required":["path"]}""",
        ),
        // 3. ui_get_tree (LOW)
        McpToolDefinition(
            name = McpToolNames.UI_GET_TREE,
            description = "Capture the active window's compact JSON AccessibilityService UI tree with depth/node bounds.",
            riskLevel = McpToolRisk.LOW,
            inputSchemaJson = """{"type":"object","properties":{"include_invisible":{"type":"boolean","default":false},"max_depth":{"type":"integer","default":30},"max_nodes":{"type":"integer","default":500}}}""",
        ),
        // 4. ui_action (MEDIUM)
        McpToolDefinition(
            name = McpToolNames.UI_ACTION,
            description = "Perform a deterministic AccessibilityService UI action (CLICK, LONG_CLICK, SET_TEXT, SCROLL, BACK, HOME, RECENTS) by node_id, res_id, or text.",
            riskLevel = McpToolRisk.MEDIUM,
            inputSchemaJson = """{"type":"object","properties":{"action":{"type":"string","enum":["CLICK","LONG_CLICK","SET_TEXT","SCROLL","SCROLL_FORWARD","SCROLL_BACKWARD","BACK","HOME","RECENTS","NOTIFICATIONS","FOCUS"]},"node_id":{"type":"string"},"text":{"type":"string"},"res_id":{"type":"string"},"value":{"type":"string"}},"required":["action"]}""",
        ),
        // 5. ui_input_raw (MEDIUM)
        McpToolDefinition(
            name = McpToolNames.UI_INPUT_RAW,
            description = "Inject hardware-level touch/swipe/keyevent/text input via root 'input' command (fallback for Canvas/WebView/games).",
            riskLevel = McpToolRisk.MEDIUM,
            inputSchemaJson = """{"type":"object","properties":{"type":{"type":"string","enum":["tap","swipe","keyevent","text"]},"x1":{"type":"integer"},"y1":{"type":"integer"},"x2":{"type":"integer"},"y2":{"type":"integer"},"duration_ms":{"type":"integer"},"keycode":{"type":"string"},"text":{"type":"string"}},"required":["type"]}""",
        ),
        // 6. screen_capture (LOW)
        McpToolDefinition(
            name = McpToolNames.SCREEN_CAPTURE,
            description = "Capture the current Android display via root screencap, scale for token efficiency, and return path and base64.",
            riskLevel = McpToolRisk.LOW,
            inputSchemaJson = """{"type":"object","properties":{"scale":{"type":"number","default":0.5},"format":{"type":"string","enum":["webp","png"],"default":"webp"}}}""",
        ),
        // 7. app_launch_or_manage (HIGH)
        McpToolDefinition(
            name = McpToolNames.APP_LAUNCH_OR_MANAGE,
            description = "List installed apps, launch an app or intent URI, force-stop, clear data, or grant permissions.",
            riskLevel = McpToolRisk.HIGH,
            inputSchemaJson = """{"type":"object","properties":{"action":{"type":"string","enum":["list","launch","force_stop","clear_data","grant_perms"]},"package_name":{"type":"string"},"intent_uri":{"type":"string"}},"required":["action"]}""",
        ),
        // 8. phone_call_control (CRITICAL)
        McpToolDefinition(
            name = McpToolNames.PHONE_CALL_CONTROL,
            description = "Control telephony actions: dial, call, end_call, speaker_on, mute.",
            riskLevel = McpToolRisk.CRITICAL,
            inputSchemaJson = """{"type":"object","properties":{"action":{"type":"string","enum":["dial","call","end_call","speaker_on","mute"]},"phone_number":{"type":"string"},"enabled":{"type":"boolean"}},"required":["action"]}""",
        ),
        // 9. sms_manage (CRITICAL)
        McpToolDefinition(
            name = McpToolNames.SMS_MANAGE,
            description = "List or read SMS inbox messages or send an SMS message.",
            riskLevel = McpToolRisk.CRITICAL,
            inputSchemaJson = """{"type":"object","properties":{"action":{"type":"string","enum":["list","read","send"]},"address":{"type":"string"},"body":{"type":"string"},"limit":{"type":"integer","default":20}},"required":["action"]}""",
        ),
        // 10. contacts_calendar_query (MEDIUM)
        McpToolDefinition(
            name = McpToolNames.CONTACTS_CALENDAR_QUERY,
            description = "Query or insert Android Contacts and Calendar entries via ContentResolver.",
            riskLevel = McpToolRisk.MEDIUM,
            inputSchemaJson = """{"type":"object","properties":{"target":{"type":"string","enum":["contacts","calendar"]},"action":{"type":"string","enum":["query","insert"]},"query":{"type":"string"},"payload":{"type":"object"},"limit":{"type":"integer","default":50}},"required":["target","action"]}""",
        ),
        // 11. notifications_manage (MEDIUM)
        McpToolDefinition(
            name = McpToolNames.NOTIFICATIONS_MANAGE,
            description = "List active status-bar notifications, dismiss a notification, or send an inline RemoteInput reply.",
            riskLevel = McpToolRisk.MEDIUM,
            inputSchemaJson = """{"type":"object","properties":{"action":{"type":"string","enum":["list","dismiss","reply"]},"notification_key":{"type":"string"},"reply_text":{"type":"string"}},"required":["action"]}""",
        ),
        // 12. device_control (HIGH)
        McpToolDefinition(
            name = McpToolNames.DEVICE_CONTROL,
            description = "Control device lock/wake, global/secure/system settings, clipboard, Wi-Fi, Bluetooth, and audio volume.",
            riskLevel = McpToolRisk.HIGH,
            inputSchemaJson = """{"type":"object","properties":{"action":{"type":"string","enum":["lock","wake","get_setting","put_setting","get_clip","set_clip","wifi","bluetooth","volume"]},"key":{"type":"string"},"value":{"type":"string"}},"required":["action"]}""",
        ),
        // 13. media_location_capture (HIGH)
        McpToolDefinition(
            name = McpToolNames.MEDIA_LOCATION_CAPTURE,
            description = "Retrieve device GPS/network location, capture a photo from front/back lens, or record microphone audio.",
            riskLevel = McpToolRisk.HIGH,
            inputSchemaJson = """{"type":"object","properties":{"action":{"type":"string","enum":["get_location","take_photo","record_audio"]},"lens":{"type":"string","enum":["back","front"],"default":"back"},"duration_sec":{"type":"integer","default":5}},"required":["action"]}""",
        ),
    )

    val byName: Map<String, McpToolDefinition> = allTools.associateBy { it.name }
}
