# AtonAI

An open, independently built AI coding agent for Android. Written from scratch
in Kotlin + Jetpack Compose.

> ব্যাখ্যা: এই অ্যাপটি সম্পূর্ণ নিজে থেকে (from scratch) লেখা — কোনো
> থার্ড-পার্টি অ্যাপ ডিকম্পাইল বা রিব্র্যান্ড করে বানানো নয়।

## Features

- **Chat** — streaming multi-provider chat (OpenAI, Anthropic, Gemini, local Ollama)
- **Model picker** — switch provider and model from the toolbar
- **Code workspace** — create, open, edit and save files in a private sandbox
- **MCP** — connect Model Context Protocol servers over JSON-RPC (Streamable HTTP)
  and discover their tools
- **Settings** — per-provider API keys stored on device (excluded from backups)

## Project layout

```
app/src/main/
├── AndroidManifest.xml
├── assets/
│   ├── providers.json        # your provider + model catalog
│   └── prompts/system.txt    # system prompt
├── java/com/atonai/
│   ├── AtonApp.kt            # Application + dependency graph
│   ├── MainActivity.kt
│   ├── data/                 # settings, providers, workspace, LLM client
│   ├── mcp/                  # MCP client + repository
│   └── ui/                   # Compose screens + view models
└── res/                      # theme, colors, strings, launcher icon
```

## Build

The project does **not** require a committed Gradle wrapper. CI installs Gradle
directly. Locally you can either:

- open the folder in Android Studio (recommended), or
- run `gradle :app:assembleDebug` (Gradle 8.11.1, JDK 17), or
- generate a wrapper once with `gradle wrapper --gradle-version 8.11.1`.

### GitHub Actions

- `.github/workflows/build.yml` — builds a **debug APK** on every push to `main`
  and uploads it as an artifact.
- `.github/workflows/release.yml` — builds a **signed release APK** on manual
  dispatch. Add these repository secrets first:

  | Secret | Meaning |
  |---|---|
  | `KEYSTORE_BASE64` | base64 of your `.keystore`/`.jks` file |
  | `KEYSTORE_PASSWORD` | keystore password |
  | `KEY_ALIAS` | key alias |
  | `KEY_PASSWORD` | key password |

  Create a keystore: `keytool -genkeypair -v -keystore release.keystore -alias aton -keyalg RSA -keysize 2048 -validity 10000`
  Then: `base64 -w0 release.keystore` and paste as `KEYSTORE_BASE64`.

## Configuration

Edit `app/src/main/assets/providers.json` to add providers or models. API keys
are entered at runtime in the Settings screen and kept in a private DataStore
file that is excluded from Android backups.

## License

Your project — add the license you want (for example MIT) in a `LICENSE` file.
