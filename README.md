# AutoReply OSS

🌐 **Website:** [nguyenbacson.io.vn](https://nguyenbacson.io.vn)

An open-source Android app that automatically replies to messages using AI. Powered by a serverless backend on Vercel with multi-provider AI fallback (Groq → Gemini → Cloudflare).

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Platform](https://img.shields.io/badge/platform-Android-green.svg)

## Features

- **Notification Listener**: Reads incoming messages without root
- **Multi-AI Fallback**: Tries Groq → Gemini → Cloudflare Workers AI automatically
- **Per-Sender Memory**: Remembers the last 10 messages per contact for context-aware replies
- **Remote Control**: Toggle bot on/off from any device via secret commands (`BAT_ALL`, `TAT_ALL`, etc.)
- **Custom AI Personality**: Edit the system prompt directly in the app
- **Scheduled Busy Mode**: Set time ranges when the bot sends a pre-defined "busy" message
- **7-Day Log Retention**: Auto-deletes logs older than 7 days (Vercel KV with ZSET)
- **Auto-Update**: GitHub Actions builds APK on every push, app checks for updates automatically

## Architecture
┌─────────────────┐ ┌──────────────────┐ ┌─────────────┐
│ Android App │ ──────► │ Vercel Server │ ──────► │ AI APIs │
│ (Kotlin) │ HTTPS │ (Next.js) │ │ Groq │
│ │ │ │ │ Gemini │
│ Notification │ │ /api/reply │ │ Cloudflare │
│ Listener │ ◄────── │ /api/logs │ ◄────── │ │
│ │ │ /api/version │ └─────────────┘
└─────────────────┘ │ │
│ Vercel KV │
│ (Redis) │
└──────────────────┘

text

## Requirements

- **Vercel** account (free tier is enough)
- **Groq** API key (free at [console.groq.com](https://console.groq.com))
- **Google AI Studio** API key (free at [aistudio.google.com](https://aistudio.google.com))
- **Cloudflare** account (optional, for 3rd fallback)
- **Android Studio** (to build the app)

## Server Setup

### 1. Fork this repository

Click the **Fork** button at the top right of this page.

### 2. Deploy to Vercel

1. Go to [vercel.com/new](https://vercel.com/new)
2. Import your forked repository
3. Set **Root Directory** to `server`
4. Click **Deploy**

### 3. Create KV Storage

1. In your Vercel project, go to the **Storage** tab
2. Click **Create Database** → **Upstash for Redis**
3. Choose the region closest to you
4. Select the **Free** plan
5. Click **Connect Project** → select your project

### 4. Configure Environment Variables

Go to **Settings** → **Environment Variables** and add:

| Variable | Description |
|----------|-------------|
| `BOT_SECRET` | A random secret string (e.g., `my_super_secret_123`) |
| `GROQ_API_KEY` | Your Groq API key |
| `GEMINI_API_KEY` | Your Google Gemini API key |
| `CF_ACCOUNT_ID` | Cloudflare account ID (optional) |
| `CF_API_TOKEN` | Cloudflare Workers AI token (optional) |
| `GITHUB_REPO` | `your-username/autoreply-oss` (for auto-update feature) |

### 5. Redeploy

Go to **Deployments** → click the 3-dot menu on the latest deployment → **Redeploy**.

### 6. Test

```bash
curl -X POST https://your-project.vercel.app/api/reply \
  -H "Content-Type: application/json" \
  -H "x-bot-secret: YOUR_BOT_SECRET" \
  -d '{"sender":"Test","text":"hello"}'
Expected: {"reply":"...","provider":"groq"}

Android App Setup
1. Clone and open
bash
git clone https://github.com/your-username/autoreply-oss.git
cd autoreply-oss/android
Open the android/ folder in Android Studio.

2. Configure server URL and secret
Open app/build.gradle.kts and update:

kotlin
buildConfigField("String", "SERVER_URL", "\"https://your-project.vercel.app/api/reply\"")
buildConfigField("String", "BOT_SECRET", "\"YOUR_BOT_SECRET\"")
3. Build APK
bash
./gradlew assembleDebug
Or use Android Studio: Build → Build Bundle(s) / APK(s) → Build APK(s)

4. Install on device
bash
adb install app/build/outputs/apk/debug/app-debug.apk
5. Grant permissions
Open the app

Tap Notification Access → enable AutoReply

Disable battery optimization for the app (important on Samsung/Xiaomi)

Remote Commands
Send these messages from your own account to control the bot remotely:

Command	Effect
BAT_ALL	Enable everything
TAT_ALL	Disable everything
BAT_CHATBOT	Enable auto-reply
TAT_CHATBOT	Disable auto-reply
BAT_AI	Enable AI replies
TAT_AI	Disable AI (sends canned message)
The bot replies with the current status after each command.

AI Personalities
You can edit the AI system prompt directly in the app:

Open the app → Config tab

Scroll to 🧠 AI Personality

Edit the prompt (e.g., change tone, language, style)

Tap Save Personality

Changes take effect immediately without rebuilding the app.

Logs
All message logs are stored in Vercel KV and auto-deleted after 7 days.

View recent logs:

bash
curl https://your-project.vercel.app/api/logs \
  -H "x-bot-secret: YOUR_BOT_SECRET"
Project Structure
text
autoreply-oss/
├── server/                    # Next.js backend
│   ├── app/api/
│   │   ├── reply/             # Main AI reply endpoint
│   │   ├── logs/              # Log storage (7-day TTL)
│   │   ├── version/           # App update check
│   │   ├── log-self/          # Store own messages
│   │   └── log-reply/         # Store bot replies
│   ├── package.json
│   └── .env.example
├── android/                   # Android app
│   ├── app/src/main/
│   │   ├── java/.../
│   │   │   ├── MainActivity.kt
│   │   │   ├── ReplyService.kt
│   │   │   └── UpdateChecker.kt
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── .github/workflows/         # Auto-build APK
└── LICENSE
Troubleshooting
Bot doesn't reply
Check if the notification listener permission is granted

Make sure battery optimization is disabled for the app

Check Logcat with tag AutoReply

Verify /api/reply returns valid JSON

unauthorized error
The BOT_SECRET in the app doesn't match the one on Vercel.

all providers failed
All 3 AI providers (Groq/Gemini/Cloudflare) are down or out of quota. Check Vercel logs.

Samsung/Xiaomi kills the app
Add the app to the Unrestricted battery list and remove it from Sleeping apps.

Contributing
Pull requests are welcome. For major changes, please open an issue first.

License
MIT

Disclaimer
This project is for personal use. Auto-reply bots may violate the Terms of Service of some messaging platforms. Use it responsibly.
