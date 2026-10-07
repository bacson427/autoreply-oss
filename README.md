# AutoReply OSS

An open-source Android app that automatically replies to messages using AI. Powered by a serverless backend on Vercel with multi-provider AI fallback (Groq → Gemini → Cloudflare).

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Platform](https://img.shields.io/badge/platform-Android-green.svg)

## Features

- **Notification Listener**: Reads incoming messages from Messenger, Telegram, Discord, TikTok without root
- **Multi-AI Fallback**: Tries Groq → Gemini → Cloudflare Workers AI automatically
- **Per-Sender Memory**: Remembers the last 10 messages per contact for context-aware replies
- **Remote Control**: Toggle bot on/off from any device via secret commands (`BAT_ALL`, `TAT_ALL`, etc.)
- **Custom AI Personality**: Edit the system prompt directly in the app
- **Scheduled Busy Mode**: Set time ranges when the bot sends a pre-defined "busy" message
- **7-Day Log Retention**: Auto-deletes logs older than 7 days (Vercel KV with ZSET)
- **Auto-Update**: GitHub Actions builds APK on every push, app checks for updates automatically

## Architecture
