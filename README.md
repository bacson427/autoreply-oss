# AutoReply OSS

An open-source Android app that automatically replies to messages using AI. Powered by a serverless backend on Vercel with multi-provider AI fallback (Groq → Gemini → Cloudflare).

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Platform](https://img.shields.io/badge/platform-Android-green.svg)
![Zero Login](https://img.shields.io/badge/auth-Zero%20Login%20Required-success.svg)

> 🛡️ **Privacy & Security:** Requires **ZERO social media logins**. The app listens directly to Android incoming notifications and replies through native notification actions. There is zero risk of account bans or credential leakage.

---

## ✨ Features

- **🛡️ Zero Login Required:** Works directly via Android notifications. No need to log in to your social accounts.
- **🌐 Universal App Support:** Supports ANY messaging app with an inline "Reply" action in notifications (Messenger, Telegram, Discord, TikTok, WhatsApp, etc.).
- **⚙️ Full In-App Configuration (New):**
  - Configure your **Vercel Server URL** and **Bot Secret** directly in the app. No need to recompile or edit source code!
  - Built-in **"Test Server Connection"** button to verify connectivity instantly.
  - Custom **AI Personality (Prompt)** editor.
  - Custom **Busy Message** editor for away hours.
  - **Supported Apps Selector:** Selectively pick which apps the bot should monitor.
  - **Whitelist Contacts (`YES_NAME`):** Only auto-reply to specific senders.
  - **Blacklist Contacts (`NO_NAME`):** Block auto-replies for unwanted senders.
  - **Automated Busy Schedule:** Set scheduled hours during which the bot sends the busy message instead of AI replies.
  - **Recognized Owner Names (`MY_NAMES`):** Automatically recognizes your outgoing messages to prevent self-reply loops.
- **📋 In-App System Logs (New):**
  - Real-time logging of incoming messages, AI responses, busy replies, and remote commands.
  - One-click **"Copy Logs"** to clipboard and **"Clear Logs"** button.
- **🛠️ Remote Control via Chat:** Control the bot remotely by sending text commands from your own account.
- **🔄 Auto Update Checker:** Checks GitHub releases for app updates with one-tap installation.

---

## 🏛️ System Architecture

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
                            │  7-day retention │
                            └──────────────────┘
```

---

## 📋 Requirements

- **Vercel** account (free tier is sufficient).
- **AI Provider API Key** (Groq free tier at [console.groq.com](https://console.groq.com) or Google Gemini at [aistudio.google.com](https://aistudio.google.com)).
- **Android Device** running Android 10 (API 29) or higher.

---

## 🚀 Server Setup (Vercel)

### 1. Fork this repository
Click **Fork** at the top right of the GitHub repository.

### 2. Deploy to Vercel
1. Open [vercel.com/new](https://vercel.com/new).
2. Import your forked repository.
3. Set **Root Directory** to `server`.
4. Click **Deploy**.

### 3. Create KV Storage (Redis)
1. In your Vercel project dashboard, go to the **Storage** tab.
2. Click **Create Database** → **Upstash for Redis**.
3. Select your preferred region and the **Free** plan.
4. Click **Connect Project** and select your server project.

### 4. Configure Environment Variables
Go to **Settings → Environment Variables** on Vercel and add:

| Variable | Description |
|---|---|
| `BOT_SECRET` | A long random secret string (e.g. `my_super_secret_token_123`) |
| `GROQ_API_KEY` | Your Groq API key (optional if using Gemini) |
| `GEMINI_API_KEY` | Your Google Gemini API key (optional if using Groq) |
| `CF_ACCOUNT_ID` | Cloudflare account ID (optional fallback) |
| `CF_API_TOKEN` | Cloudflare Workers AI token (optional fallback) |
| `GITHUB_REPO` | `your-username/autoreply-oss` (used by app update checker) |

*Optional model overrides: `GROQ_MODEL`, `GEMINI_MODEL`, `CF_MODEL` (see `server/.env.example`).*

### 5. Redeploy Server
Go to **Deployments** → Click the **•••** menu on the latest deployment → **Redeploy**.

### 6. Test Server Connection
```bash
curl -X POST https://your-project.vercel.app/api/reply \
  -H "Content-Type: application/json" \
  -H "x-bot-secret: YOUR_BOT_SECRET" \
  -d '{"sender":"Test","text":"hello"}'
```
Expected response:
```json
{"reply":"Hello! How can I help you today?","provider":"groq"}
```

---

## 📱 Android App Setup

### Method 1: Install Prebuilt APK (Easiest)
1. Download `app-debug.apk` from the latest GitHub Release or build artifact.
2. Install the APK on your Android device.
3. Open the app:
   - Tap **Notification Access** and enable AutoReply.
   - Tap **Disable Battery Opt** to ensure the background listener is not killed.
4. Switch to the **SETTINGS** tab:
   - Enter your **Vercel Backend URL** (e.g. `https://your-project.vercel.app`).
   - Enter your **Bot Secret** (`x-bot-secret`).
   - Tap **⚡ TEST SERVER CONNECTION** to verify.
   - Tap **💾 SAVE ALL SETTINGS**.

### Method 2: Build From Source (Android Studio)
1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/autoreply-oss.git
   ```
2. Open the `android/` directory in Android Studio.
3. Build the APK:
   ```bash
   ./gradlew assembleDebug
   ```
4. Install on your connected device:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 🛠️ Remote Control Commands

Control your bot remotely in real-time by sending text messages to yourself or from an authorized owner account (configured in `MY_NAMES`) inside any messaging app:

| Command | Action | Example |
|---|---|---|
| `STATUS` or `CHECK` | Receive an instant live diagnostic report (switches, names, prompt, busy message) | `STATUS` |
| `ENABLE_ALL` | Master switch ON: Activates automated message processing and AI responses | `ENABLE_ALL` |
| `DISABLE_ALL` | Master switch OFF: Completely silences all auto-replies across all apps | `DISABLE_ALL` |
| `ENABLE_AI` | Turn smart AI auto-reply ON (Groq &rarr; Gemini &rarr; Cloudflare fallback) | `ENABLE_AI` |
| `DISABLE_AI` | Turn smart AI auto-reply OFF | `DISABLE_AI` |
| `ENABLE_CHATBOT` | Turn busy auto-responder ON (sends template message instead of AI) | `ENABLE_CHATBOT` |
| `DISABLE_CHATBOT` | Turn busy auto-responder OFF | `DISABLE_CHATBOT` |
| `CHATBOT: <message>` | Update the custom busy reply message | `CHATBOT: Currently in a meeting, will reply shortly!` |
| `AI: <prompt>` | Dynamically update the AI personality &amp; system instructions | `AI: You are a friendly, witty assistant. Reply naturally in 1 concise sentence.` |
| `YES_NAME: <names>` | Whitelist contacts: Only auto-reply to listed senders (comma-separated; send empty `YES_NAME:` to clear) | `YES_NAME: Alice, Bob, Charlie` |
| `NO_NAME: <names>` | Blacklist contacts: Block auto-replies for unwanted senders (comma-separated; send empty `NO_NAME:` to clear) | `NO_NAME: Spammer, AdBot, Marketing` |
| `MY_NAME: <names>` | Update recognized owner names to prevent self-reply spam loops | `MY_NAME: Admin, Alex` |
| `HELP` | Show available remote command list and syntax | `HELP` |

> 💡 **Tip:** Send `YES_NAME:` or `NO_NAME:` (with nothing after the colon) to instantly clear the respective filter.

---

## 🔗 Server Endpoints

All endpoints except `/api/version` require the `x-bot-secret` header.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/reply` | Generates AI reply for incoming message |
| `POST` | `/api/logs` | Stores activity log (auto-expires after 7 days) |
| `GET` | `/api/logs` | Fetches recent activity logs |
| `GET` | `/api/version` | Returns latest GitHub release version tag and APK download URL |
| `POST` | `/api/log-self` | Stores outgoing messages as context |
| `POST` | `/api/log-reply` | Stores bot replies in conversation history |

---

## 🛠️ Troubleshooting

- **Bot does not reply:**
  - Verify Notification Access is granted in Android Settings.
  - Verify battery optimization is disabled (set to "Unrestricted").
  - Test `/api/reply` using the in-app **"TEST SERVER CONNECTION"** button in the Settings tab.
- **HTTP 401 Unauthorized:**
  - The `Bot Secret` in the Android app does not match `BOT_SECRET` in your Vercel Environment Variables.
- **All AI Providers Failed:**
  - Your AI provider quota may be exhausted or the API key is incorrect. Check your Vercel deployment logs for exact error details.
- **Xiaomi / Samsung / Oppo background kills:**
  - Add AutoReply to AutoStart / Background Unrestricted list and lock the app in the recent apps overview.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

## ⚠️ Disclaimer

This project is intended for personal use and automation assistance. Always comply with the Terms of Service of the respective messaging platforms. Use responsibly.
