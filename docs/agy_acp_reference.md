# Antigravity CLI (`agy`) ve ACP Protokol Referansı

- **Durum:** `VERIFIED_LOCAL_BINARY`
- **Yakalanan Sürüm:** `1.4.11`
- **İkili Yol:** `C:\Users\gokbe\AppData\Local\agy\bin\agy.exe`
- **Toplanma Tarihi:** 2026-11-24

> Bu dosya, alt ajanların Antigravity CLI bayraklarını veya **Agent Client Protocol (ACP)** arayüzünü tahmin ederek hatalı kod üretmesini önlemek için yerel `agy` ikilisinden alınan ham `--help` çıktılarını içerir.

---

## 1. `agy --version`

```text
1.4.11
```

---

## 2. `agy --help` (Genel Kullanım, Başsız `-p`, Etkileşimli `-i`, Oturum ve Mod Bayrakları)

```text
Usage of agy:
  --add-dir                       Add a directory to the workspace (repeatable) (default [])
  --agent                         Agent for the current CLI session
  -c                              Short alias for --continue
  --continue                      Continue the most recent conversation
  --conversation                  Resume a previous conversation by ID
  --dangerously-skip-permissions  Auto-approve all tool permission requests without prompting
  --disable-slash-commands        Disable slash command and skill expansion in print mode
  --effort                        Reasoning effort for the current CLI session (low|medium|high|xhigh|max)
  -i                              Short alias for --prompt-interactive
  --input-format                  Input format for print mode (text, stream-json). stream-json reads one NDJSON message per line from stdin and runs a turn for each; it requires --output-format stream-json (default text)
  --json-schema                   Optional JSON schema string or path to a schema file to enforce structured output (for stream-json, only applicable to the final result)
  --log-file                      Override CLI log file path
  --mode                          Set the agent execution mode for this session (accept-edits, auto, plan)
  --model                         Model for the current CLI session
  --new-project                   Create a new project for this session
  --output-format                 Output format for print mode (text, json, stream-json) (default text)
  -p                              Short alias for --print
  --print                         Run a single prompt non-interactively and print the response
  --print-timeout                 Optional time limit for print mode; 0 waits until the turn completes (default 0s)
  --project                       Project ID or project name for the current CLI session
  --prompt                        Alias for --print
  --prompt-interactive            Run an initial prompt interactively and continue the session
  --remote-control                Create a remote connection for the CLI session on start up
  --sandbox                       Run in a sandbox with terminal restrictions enabled
  --settings                      Settings for this session only, never saved: a JSON object of settings.json keys, inline or as a file path

Available subcommands:
  agent           List available agents
  agents          List available agents
  changelog       Show changelog and release notes
  help            Show help for subcommands
  install         Configure environment paths and shell settings
  mcp             Manage MCP servers (add, remove, list, enable, disable)
  mic-serve       Serve this machine's microphone to a CLI on another host
  models          List available models
  plugin          Manage plugins (install, uninstall, list, enable, disable)
  plugins         Alias for plugin
  remote-control  Manage the remote-control background daemon (start, status, stop)
  update          Update CLI
```

### Kritik Başsız / Oturum Notları (`agy` 1.4.11)
- **Başsız (Non-Interactive) Çalıştırma:** `-p` / `--print` / `--prompt` tek seferlik çıktı verir; `--input-format stream-json --output-format stream-json` NDJSON tabanlı çok turlu akış sağlar.
- **Etkileşimli Başlangıç:** `-i` / `--prompt-interactive` başlangıç istemiyle etkileşimli oturum açar.
- **Oturum Devamı:** `-c` / `--continue` (en son konuşma) ve `--conversation <id>` (belirli konuşma ID'si).
- **Ayrı `auth` / `session` alt komutu yoktur:** Hesap/profil izolasyonu chroot içinde `HOME`, `XDG_CONFIG_HOME`, `XDG_DATA_HOME` ve `agy acp` bayrakları (`--app-data-dir`, `--gemini-dir`) üzerinden yönetilir.

---

## 3. `agy acp --help` (Agent Client Protocol — Stdio JSON-RPC Sunucusu)

```text
Usage: agy acp [flags]

Start the Agent Client Protocol (ACP) stdio server

Flags:
  --add-dir                         Add a directory to the workspace (repeatable) (default [])
  --agent                           Agent for the ACP session
  --app-data-dir                    Path where application data is stored (default antigravity-cli)
  --app_data_dir                    Path where application data is stored (default antigravity-cli)
  --cli_dir_name                    Subdirectory under app_data_dir for CLI settings and logs
  --cli_log_file                    Override ACP log file path
  --cloud_code_endpoint             CCPA API URL (default https://daily-cloudcode-pa.googleapis.com)
  --dangerously-skip-permissions    Auto-approve all tool permission requests
  --disable-telemetry               If true, disable telemetry
  --disable_telemetry               If true, disable telemetry
  --gemini-dir                      Path where Gemini files are stored (default .gemini)
  --gemini_dir                      Path where Gemini files are stored (default .gemini)
  -h                                Show help
  --help                            Show help
  --ide-mcp-port                    Port for host IDE tools MCP server (requires 'nc' in $PATH) (default 0)
  --ide_mcp_port                    Port for host IDE tools MCP server (requires 'nc' in $PATH) (default 0)
  --include-default-customizations  Include default customizations (default true)
  --is_google3_workspace            Whether running in Google environment
  --is_google_internal              Deprecated: use is_google3_workspace
  --log-file                        Override ACP log file path
  --override_user_agent_name        Override user agent name for HTTP requests
  --process-per-session             Serve each session from an isolated worker process
  --sandbox                         Run in a sandbox with terminal restrictions enabled
  --stdio                           Use stdio JSON-RPC transport (default)
  --use_stubby_auth                 Use LOAS auth
  --worker                          Serve a single session for an ACP front process (internal)
```

---

## 4. Alt Komut `--help` Çıktıları

### `agy mcp --help`
```text
Usage: agy mcp <subcommand> [flags] [args]

Available subcommands:
  add      Add or update an MCP server configuration
  remove   Remove an MCP server configuration
  list     List all configured MCP servers
  enable   Enable an MCP server
  disable  Disable an MCP server

Run "agy mcp <subcommand> --help" for flags, argument rules, and examples.
```

### `agy mcp add --help`
```text
Usage: agy mcp add [flags] <name> <commandOrUrl> [args...]

Add or update an MCP server configuration

Flags:
  -H        Short alias for --header (default [])
  -e        Short alias for --env (default [])
  --env     Environment variable KEY=value (repeatable) (default [])
  -h        Show help
  --header  HTTP header Key: Value (repeatable) (default [])
  --help    Show help
  -t        Short alias for --type (default stdio)
  --type    Server type: 'stdio' or 'http' (default stdio)

Arguments:
  <name>          Unique name for the server.
  <commandOrUrl>  Executable for a stdio server, or the URL for an http server.
  [args...]       Arguments passed to the stdio command.

Notes:
  Flags must come before <name>; a flag placed after it is rejected.
  http/https URLs are detected automatically, so --type is usually unnecessary.
  Use -- before the command to pass a command or args that begin with '-'.

Examples:
  agy mcp add fs npx -y @modelcontextprotocol/server-filesystem /work
  agy mcp add --header "Authorization: Bearer TOKEN" api https://example.com/mcp
  agy mcp add --env GITHUB_TOKEN=xxx gh -- docker run -i ghcr.io/github/mcp
```

### `agy agent --help`
```text
Usage: agy agent [flags]

List available agents

Flags:
  -h      Show help
  --help  Show help
```

### `agy models --help`
```text
Usage: agy models [flags]

List available models

Flags:
  -h      Show help
  --help  Show help
```

### `agy plugin --help`
```text
Usage: agy plugin <command> [arguments]

Commands:
  list [--json]          List installed plugins
  import [source]        Import plugins from gemini or claude
  install <target>       Install a plugin (supports plugin[@marketplace])
  uninstall <name>       Uninstall a plugin
  enable <name>          Enable a plugin
  disable <name>         Disable a plugin
  validate [path] [--strict] [--json]
                         Check a plugin's manifest and files
  link <mp> <target>     Generate link to a marketplace
  help                   Show this help
```

### `agy remote-control --help`
```text
Usage: agy remote-control <subcommand>

Available subcommands:
  start    Register and start the remote-control daemon
  status   Show the daemon's status
  stop     Stop the daemon and unregister it

Run "agy remote-control <subcommand> --help" for flags.
```

### `agy install --help`
```text
Usage: agy install [flags]

Configure environment paths and shell settings

Flags:
  --dir           Custom directory target to configure PATH for
  -h              Show help
  --help          Show help
  --skip-aliases  Bypasses shell profile alias purging
  --skip-path     Bypasses shell profile PATH appending
```

### `agy update --help`
```text
Usage of update:
```

### `agy changelog --help`
```text
Usage: agy changelog [flags]

Show changelog and release notes

Flags:
  -h      Show help
  --help  Show help
```

### `agy mic-serve --help`
```text
Usage: agy mic-serve [flags]

Serve this machine's microphone to a CLI on another host

Flags:
  --addr  Address to listen on; keep it on loopback (default 127.0.0.1:4713)
  -h      Show help
  --help  Show help
```
