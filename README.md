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
