#!/usr/bin/env python3
"""Antigravity PreToolUse shell command guard for parallel /swarm safety."""
import json
import re
import sys

BLOCKED_PATTERNS = [
    (r"\bgit\s+reset\s+--hard\b", "git reset --hard paralel worktree değişikliklerini yok edebilir."),
    (r"\bgit\s+push\s+.*(--force|-f)\b", "Force push yasaktır."),
    (r"\bgit\s+clean\s+-[a-zA-Z]*f[a-zA-Z]*d", "Yıkıcı git clean -fd yasaktır."),
    (r"\brm\s+-[a-zA-Z]*r[a-zA-Z]*f\s+(/|~|\$HOME|\*)(?:\s|$)", "Kök veya kullanıcı dizininde yıkıcı rm -rf yasaktır."),
    (
        r"\badb\s+shell\s+.*rm\s+-[a-zA-Z]*r[a-zA-Z]*f\s+/(system|vendor|data|sdcard|efs|persist)\b",
        "Bağlı Android cihazda kritik bölümleri silen adb shell rm -rf yasaktır.",
    ),
    (r"\badb\s+reboot\s+(bootloader|fastboot|edl)\b", "Cihazı bootloader/EDL moduna sokmak yasaktır."),
    (r"\bfastboot\s+(flash|erase|format|oem)\b", "Fastboot yazma/silme komutları yasaktır."),
]


def extract_command(payload: dict) -> str:
    """Extract command text from Antigravity PreToolUse or generic hook payloads."""
    tool_call = payload.get("toolCall") or payload.get("tool_call")
    if isinstance(tool_call, dict):
        args = tool_call.get("args")
        if isinstance(args, dict):
            return str(
                args.get("CommandLine")
                or args.get("command")
                or args.get("cmd")
                or ""
            )
        if isinstance(args, str):
            return args

    tool_input = payload.get("tool_input") or payload.get("input") or {}
    if isinstance(tool_input, dict):
        return str(
            tool_input.get("CommandLine")
            or tool_input.get("command")
            or tool_input.get("cmd")
            or ""
        )
    if isinstance(tool_input, str):
        return tool_input
    return ""


def main() -> int:
    raw = sys.stdin.read().lstrip("\ufeff").strip()
    if not raw:
        sys.stdout.write(json.dumps({"decision": "allow"}) + "\n")
        return 0
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        sys.stdout.write(json.dumps({"decision": "allow"}) + "\n")
        return 0

    if not isinstance(payload, dict):
        sys.stdout.write(json.dumps({"decision": "allow"}) + "\n")
        return 0

    is_native_antigravity = "hookEventName" in payload
    cmd = extract_command(payload)

    for pattern, reason in BLOCKED_PATTERNS:
        if re.search(pattern, cmd, re.IGNORECASE):
            sys.stdout.write(
                json.dumps({"decision": "deny", "reason": f"[GUARD BLOCKED] {reason}"}) + "\n"
            )
            sys.stderr.write(f"[GUARD BLOCKED] {reason}\nCommand: {cmd}\n")
            return 0 if is_native_antigravity else 2

    sys.stdout.write(json.dumps({"decision": "allow"}) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
