# AutoReply OSS

🌐 **Website:** [nguyenbacson.io.vn](https://nguyenbacson.io.vn)

An open-source Android app that automatically replies to messages using AI. Powered by a serverless backend on Vercel with multi-provider AI fallback (Groq → Gemini → Cloudflare).

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Platform](https://img.shields.io/badge/platform-Android-green.svg)

> ⚠️ **Security warning:** Never commit your real `BOT_SECRET` or API keys to a public repository. Use environment variables on Vercel and `local.properties` for the Android app (already in `.gitignore`).

## What this app does

- Reads incoming notifications from supported apps (Messenger, Telegram, Discord)
- Sends the message to a Vercel serverless function
- The server asks an AI provider (Groq → Gemini → Cloudflare fallback) for a reply
- The reply is sent back through the notification's quick-reply action
- Optional: logs each conversation for 7 days (auto-deleted)

**The OSS template is a minimal working base.** It does not include advanced UI (settings tab, personality editor, scheduled mode). Those are kept private.

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
Expected:

json
{"reply":"...","provider":"groq"}
Android app setup
1. Clone and open
bash
git clone https://github.com/your-username/autoreply-oss.git
cd autoreply-oss/android
Open the android/ folder in Android Studio.

2. Configure server URL and secret
Do not put the real secret directly in build.gradle.kts. Add it to android/local.properties (already ignored by git):

properties
BOT_SECRET=your_real_secret_here
SERVER_URL=https://your-project.vercel.app/api/reply
Then in app/build.gradle.kts, load it from local.properties:

kotlin
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    defaultConfig {
        buildConfigField("String", "SERVER_URL", "\"${localProps["SERVER_URL"] ?: ""}\"")
        buildConfigField("String", "BOT_SECRET", "\"${localProps["BOT_SECRET"] ?: ""}\"")
    }
}
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
Send these messages from your own account (from another device or from a group chat where you're the sender) to toggle the bot:

Command	Effect
BAT_ALL	Enable everything
TAT_ALL	Disable everything
BAT_CHATBOT	Enable auto-reply
TAT_CHATBOT	Disable auto-reply
BAT_AI	Enable AI replies
TAT_AI	Disable AI (bot stays silent)
The bot writes the current status to Logcat (tag AutoReply). It does not reply in chat.

Server endpoints
Method	Path	Purpose
POST	/api/reply	Main endpoint. Body: {sender, text, prompt?}
POST	/api/logs	Store a log entry (auto-trims to 7 days)
GET	/api/logs	Read recent logs (limit=50 by default)
GET	/api/version	Returns the latest GitHub release tag + APK URL
POST	/api/log-self	Store your own outgoing message (context only)
POST	/api/log-reply	Store a bot reply (for history)
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
Reply history is per-sender but limited to 10 messages (soft cap; you can raise it in server/app/api/reply/route.js).

Remote commands are sent via the notification's own sender detection; they will only work from the exact display name listed in Config.MY_NAMES.

The OSS template is intentionally minimal. For a full-featured build (UI settings, personality, scheduling), fork it and add your own.

Auto-update: the debug APK is signed with a per-runner debug keystore. Installing a new APK over an old one may fail with INSTALL_FAILED_UPDATE_INCOMPATIBLE. Uninstall the old app first, or set up a release keystore stored in GitHub Secrets.

Contributing
Pull requests are welcome. For major changes, open an issue first to discuss what you'd like to change.

License
MIT

Disclaimer
This project is for personal use only. Auto-reply bots may violate the Terms of Service of some messaging platforms. Use it responsibly and never at scale on platforms that forbid automation.
