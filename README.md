# AutoReply OSS

🌐 **Website:** [nguyenbacson.io.vn](https://nguyenbacson.io.vn)

An open-source Android app that automatically replies to messages using AI. Powered by a serverless backend on Vercel with multi-provider AI fallback (Groq → Gemini → Cloudflare).

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Platform](https://img.shields.io/badge/platform-Android-green.svg)

> ⚠️ **Security warning:** Never commit your real `BOT_SECRET` or API keys to a public repository. Set server keys as environment variables on Vercel. The Android `BOT_SECRET` is compiled into the APK, so do not publish an APK built with your real secret.

## What this app does

- Reads incoming notifications from supported apps (Messenger, Telegram, Discord)
- Sends the message to a Vercel serverless function
- The server asks an AI provider (Groq → Gemini → Cloudflare fallback) for a reply
- The reply is sent back through the notification's quick-reply action
- Keeps the last messages per sender as context for the AI
- Logs conversations to Vercel KV (log entries older than 7 days are removed)

**This is a minimal working base.** It has no settings screen, personality editor or scheduled mode: the app has three buttons (notification access, battery optimization, check for updates).

## Architecture

```text
┌─────────────────┐         ┌──────────────────┐         ┌─────────────┐
│  Android App    │ ──────► │  Vercel Server   │ ──────► │   AI APIs   │
│  (Kotlin)       │  HTTPS  │  (Next.js)       │         │   Groq      │
│                 │         │                  │         │   Gemini    │
│  Notification   │         │  /api/reply      │         │   Cloudflare│
│  Listener       │ ◄────── │  /api/logs       │ ◄────── │             │
│                 │         │  /api/version    │         └─────────────┘
└─────────────────┘         │                  │
                            │  Vercel KV       │
                            │  (Redis)         │
                            └──────────────────┘
```

## Requirements

- **Vercel** account (free tier is enough)
- **Groq** API key, free at [console.groq.com](https://console.groq.com)
- **Google AI Studio** API key, free at [aistudio.google.com](https://aistudio.google.com)
- **Cloudflare** account, optional, for the 3rd fallback layer
- **Android Studio** to build the app

## Server setup

### 1. Fork this repository

Click **Fork** at the top right.

### 2. Deploy to Vercel

1. Open [vercel.com/new](https://vercel.com/new)
2. Import your forked repository
3. Set **Root Directory** to `server`
4. Click **Deploy**

### 3. Create KV storage

1. In your Vercel project, open the **Storage** tab
2. Click **Create Database** → **Upstash for Redis**
3. Pick the region closest to you
4. Select the **Free** plan
5. Click **Connect Project** and select your project

### 4. Configure environment variables

Go to **Settings → Environment Variables** and add:

| Variable | Description |
|----------|-------------|
| `BOT_SECRET` | A long random secret string |
| `GROQ_API_KEY` | Your Groq API key |
| `GEMINI_API_KEY` | Your Google Gemini API key |
| `CF_ACCOUNT_ID` | Cloudflare account ID (optional) |
| `CF_API_TOKEN` | Cloudflare Workers AI token (optional) |
| `GITHUB_REPO` | `your-username/autoreply-oss`, used by the update checker |

Optional model overrides: `GROQ_MODEL`, `GEMINI_MODEL`, `CF_MODEL` (see `server/.env.example`).

### 5. Redeploy

**Deployments** → 3-dot menu on the latest deployment → **Redeploy**.

### 6. Test the server

```bash
curl -X POST https://your-project.vercel.app/api/reply \
  -H "Content-Type: application/json" \
  -H "x-bot-secret: YOUR_BOT_SECRET" \
  -d '{"sender":"Test","text":"hello"}'
```

Expected response:

```json
{"reply":"...","provider":"groq"}
```

## Android app setup

### 1. Clone and open

```bash
git clone https://github.com/your-username/autoreply-oss.git
cd autoreply-oss/android
```

Open the `android/` folder in Android Studio.

### 2. Configure server URL and secret

Edit `app/build.gradle.kts` and update the two `buildConfigField` lines:

```kotlin
buildConfigField("String", "SERVER_URL", "\"https://your-project.vercel.app/api/reply\"")
buildConfigField("String", "BOT_SECRET", "\"YOUR_BOT_SECRET\"")
```

⚠️ Do not commit real values. Set them right before building and revert (`git checkout app/build.gradle.kts`) before you commit. Note that the GitHub Action builds and publishes an APK on every push to `android/**`, so a real secret in the repository ends up public.

### 3. Set your own names

In `ReplyService.kt`, replace the placeholders in `Config.MY_NAMES` with your display name(s). The bot uses this list to recognize your own messages.

### 4. Build APK

```bash
./gradlew assembleDebug
```

Or use Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

### 5. Install on device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 6. Grant permissions

1. Open the app
2. Tap **Grant Notification Access** and enable AutoReply
3. Tap **Disable Battery Optimization** (important on Samsung / Xiaomi)
4. Optional: tap **Check for Updates** to test the update endpoint

## Remote control

Messages you send yourself are matched against the commands below. Detection relies on the notification text starting with `You:` / `Bạn:` or one of your names in `MY_NAMES`, and only works in one-to-one chats (group notifications are ignored).

| Command | Effect |
|---------|--------|
| `BAT_ALL` | Enable everything |
| `TAT_ALL` | Disable everything |
| `BAT_CHATBOT` | Enable auto-reply |
| `TAT_CHATBOT` | Disable auto-reply |
| `BAT_AI` | Enable AI replies |
| `TAT_AI` | Disable AI (the bot sends a canned busy message) |

The resulting status is written to Logcat (tag `AutoReply`). The bot does not post it in the chat.

## Server endpoints

All endpoints except `/api/version` require the `x-bot-secret` header.

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/reply` | Main endpoint. Body: `{sender, text, prompt?}` |
| POST | `/api/logs` | Store a log entry and drop entries older than 7 days |
| GET | `/api/logs` | Read recent logs (`limit`, default 50, max 500) |
| GET | `/api/version` | Latest GitHub release tag and APK URL (no secret needed) |
| POST | `/api/log-self` | Store your own outgoing message as context |
| POST | `/api/log-reply` | Push an entry to a separate `logs` list (the app does not call it) |

View recent logs:

```bash
curl https://your-project.vercel.app/api/logs \
  -H "x-bot-secret: YOUR_BOT_SECRET"
```

## Project structure

```text
autoreply-oss/
├── server/                    # Next.js backend
│   ├── app/api/
│   │   ├── reply/             # Main AI reply endpoint
│   │   ├── logs/              # Log storage (7-day retention)
│   │   ├── version/           # App update check
│   │   ├── log-self/          # Store outgoing messages
│   │   └── log-reply/         # Extra log list (unused by the app)
│   ├── package.json
│   └── .env.example
├── android/                   # Android app (Kotlin)
│   ├── app/src/main/
│   │   ├── java/io/github/autoreply/oss/
│   │   │   ├── MainActivity.kt
│   │   │   ├── ReplyService.kt
│   │   │   └── UpdateChecker.kt
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── .github/workflows/
│   └── build-apk.yml          # Auto-build APK on push
└── LICENSE
```

## Troubleshooting

### Bot does not reply

- Confirm notification access is granted (Settings → Notifications → Notification access)
- Disable battery optimization for AutoReply
- Filter Logcat by tag `AutoReply`
- Verify `/api/reply` returns valid JSON

### `unauthorized` error

The `BOT_SECRET` in the app does not match the one set on Vercel.

### `all providers failed`

All three AI providers (Groq / Gemini / Cloudflare) are down, out of quota, or returned an empty reply. Check Vercel logs for the exact error.

### Samsung / Xiaomi kills the service

Add AutoReply to the **Unrestricted** battery list and remove it from Sleeping apps / Deep sleeping apps.

## Known limitations

- Per-sender history keeps the last 20 entries (the 10 most recent are sent to the AI). History entries have no expiry; only `/api/logs` entries are removed after 7 days.
- Messages from third parties (sender names and text) are stored in your Vercel KV. Use this only if that is acceptable for you and the people you chat with.
- Auto-update: the debug APK is signed with a per-runner debug keystore, so installing a new APK over an old one may fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Uninstall the old app first, or set up a fixed release keystore in GitHub Secrets.
- Update notifications on Android 13+ need the notification permission, which the app does not request at runtime.

## Contributing

Pull requests are welcome. For major changes, open an issue first to discuss what you'd like to change.

## License

MIT

## Disclaimer

This project is for personal use only. Auto-reply bots may violate the Terms of Service of some messaging platforms. Use it responsibly and never at scale on platforms that forbid automation.
