# AutoReply OSS

🌐 **Website:** [nguyenbacson.io.vn](https://nguyenbacson.io.vn)

An open-source Android app that automatically replies to messages using AI. Powered by a serverless backend on Vercel with multi-provider AI fallback (Groq → Gemini → Cloudflare).

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Platform](https://img.shields.io/badge/platform-Android-green.svg)

> ⚠️ **Security warning:** Never commit your real `BOT_SECRET` or API keys to a public repository. Use environment variables on Vercel for the server, and keep the Android `BOT_SECRET` out of version control.

## What this app does

- Reads incoming notifications from supported apps (Messenger, Telegram, Discord)
- Sends the message to a Vercel serverless function
- The server asks an AI provider (Groq → Gemini → Cloudflare fallback) for a reply
- The reply is sent back through the notification's quick-reply action
- Logs each conversation for 7 days (auto-deleted)

**The OSS template is a minimal working base.** Advanced UI (settings tab, personality editor, scheduled mode) is not included.

## Requirements

- **Vercel** account (free tier is enough)
- **Groq** API key — free at [console.groq.com](https://console.groq.com)
- **Google AI Studio** API key — free at [aistudio.google.com](https://aistudio.google.com)
- **Cloudflare** account — optional, for the 3rd fallback layer
- **Android Studio** — to build the app

## Server setup

### 1. Fork this repository

Click **Fork** at the top right.

### 2. Deploy to Vercel

1. Open [vercel.com/new](https://vercel.com/new)
2. Import your forked repository
3. Set **Root Directory** to `server`
4. Click **Deploy**

### 3. Create KV storage

1. In your Vercel project → **Storage** tab
2. Click **Create Database** → **Upstash for Redis**
3. Pick the region closest to you
4. Select the **Free** plan
5. Click **Connect Project** → select your project

### 4. Configure environment variables

Go to **Settings → Environment Variables** and add:

| Variable | Description |
|----------|-------------|
| `BOT_SECRET` | Random secret string, e.g. `my_super_secret_123` |
| `GROQ_API_KEY` | Your Groq API key |
| `GEMINI_API_KEY` | Your Google Gemini API key |
| `CF_ACCOUNT_ID` | Cloudflare account ID (optional) |
| `CF_API_TOKEN` | Cloudflare Workers AI token (optional) |
| `GITHUB_REPO` | `your-username/autoreply-oss` for the update checker |

### 5. Redeploy

**Deployments** → 3-dot menu on latest → **Redeploy**.

### 6. Test the server

```bash
curl -X POST https://your-project.vercel.app/api/reply \
  -H "Content-Type: application/json" \
  -H "x-bot-secret: YOUR_BOT_SECRET" \
  -d '{"sender":"Test","text":"hello"}'
Expected response:

json
{"reply":"...","provider":"groq"}
Android app setup
1. Clone and open
bash
git clone https://github.com/your-username/autoreply-oss.git
cd autoreply-oss/android
Open the android/ folder in Android Studio.

2. Configure server URL and secret
Edit app/build.gradle.kts and update the two buildConfigField lines:

kotlin
buildConfigField("String", "SERVER_URL", "\"https://your-project.vercel.app/api/reply\"")
buildConfigField("String", "BOT_SECRET", "\"YOUR_BOT_SECRET_HERE\"")
⚠️ Never commit real values here. If you plan to fork and push your own build, keep the real secret in local.properties (already in .gitignore) and read it with Properties() — or set the value manually right before building and revert it after.

3. Build APK
bash
./gradlew assembleDebug
Or via Android Studio: Build → Build Bundle(s) / APK(s) → Build APK(s).

4. Install on device
bash
adb install app/build/outputs/apk/debug/app-debug.apk
5. Grant permissions
Open the app

Tap Grant Notification Access → enable AutoReply

Tap Disable Battery Optimization (important on Samsung / Xiaomi)

Optional: tap Check for Updates to verify the update endpoint

Remote control
Send these messages from your own device (from another phone or another app) to toggle the bot. The Android service matches the message against MY_NAMES in ReplyService.kt, so you may need to adjust that list to match your own display name.

Command	Effect
BAT_ALL	Enable everything
TAT_ALL	Disable everything
BAT_CHATBOT	Enable auto-reply
TAT_CHATBOT	Disable auto-reply
BAT_AI	Enable AI replies
TAT_AI	Disable AI (bot sends a canned busy message)
The bot writes the current status to Logcat (tag AutoReply). It does not reply in the chat with the status.

Note: Group chat notifications are ignored on purpose, so commands from a group will not be processed.

Server endpoints
Method	Path	Purpose
POST	/api/reply	Main endpoint. Body: {sender, text, prompt?}
POST	/api/logs	Store a log entry (auto-trims to 7 days)
GET	/api/logs	Read recent logs (limit=50 by default)
GET	/api/version	Returns the latest GitHub release tag + APK URL
POST	/api/log-self	Store your own outgoing message (used as context)
POST	/api/log-reply	Store a bot reply (used as context)
All endpoints require the x-bot-secret header.

Viewing logs
bash
curl https://your-project.vercel.app/api/logs \
  -H "x-bot-secret: YOUR_BOT_SECRET"
Project structure
text
autoreply-oss/
├── server/                    # Next.js backend
│   ├── app/api/
│   │   ├── reply/             # Main AI reply endpoint
│   │   ├── logs/              # Log storage (7-day retention)
│   │   ├── version/           # App update check
│   │   ├── log-self/          # Store outgoing messages
│   │   └── log-reply/         # Store bot replies
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
Troubleshooting
Bot does not reply
Confirm notification access is granted (Settings → Notifications → Notification access)

Disable battery optimization for AutoReply

Filter Logcat by tag AutoReply

Verify /api/reply returns valid JSON

unauthorized error
The BOT_SECRET in the app does not match the one set on Vercel.

all providers failed
All three AI providers (Groq / Gemini / Cloudflare) are down or out of quota. Check Vercel logs for the exact error.

Samsung / Xiaomi kills the service
Add AutoReply to the Unrestricted battery list and remove it from Sleeping apps / Deep sleeping apps.

Known limitations
Reply history is per-sender but limited to the last 10 messages.

Remote commands only work if MY_NAMES in ReplyService.kt matches your display name.

Group chat notifications are skipped.

The OSS template is intentionally minimal.

Auto-update: the debug APK is signed with a per-runner debug keystore, so installing a new APK over an old one may fail with INSTALL_FAILED_UPDATE_INCOMPATIBLE. Uninstall the old app first, or set up a release keystore stored in GitHub Secrets.

Contributing
Pull requests are welcome. For major changes, open an issue first to discuss what you would like to change.

License
MIT

Disclaimer
This project is for personal use only. Auto-reply bots may violate the Terms of Service of some messaging platforms. Use it responsibly and never at scale on platforms that forbid automation.
