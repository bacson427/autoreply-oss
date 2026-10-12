"use client";
import React from 'react';

export default function Home() {
  return (
    <div style={{
      minHeight: '100vh',
      backgroundColor: '#000',
      color: '#e4e4e7',
      fontFamily: 'Inter, system-ui, sans-serif',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      padding: '40px 24px 80px',
      boxSizing: 'border-box',
      lineHeight: 1.6
    }}>
      
      {/* Background Glow */}
      <div style={{
        position: 'absolute',
        top: '-150px',
        left: '50%',
        transform: 'translateX(-50%)',
        width: '600px',
        height: '400px',
        background: 'radial-gradient(circle, rgba(255,255,255,0.15) 0%, rgba(0,0,0,0) 70%)',
        pointerEvents: 'none',
        zIndex: 0
      }} />

      {/* Container */}
      <main style={{
        position: 'relative',
        zIndex: 1,
        width: '100%',
        maxWidth: '800px',
        display: 'flex',
        flexDirection: 'column',
        gap: '40px'
      }}>
        
        {/* Header */}
        <header style={{ textAlign: 'center', marginBottom: '20px' }}>
          <h1 style={{
            fontSize: 'clamp(36px, 8vw, 56px)',
            fontWeight: 800,
            letterSpacing: '-0.04em',
            lineHeight: 1.1,
            margin: '0 0 16px 0',
            background: 'linear-gradient(to right, #fff, #71717a)',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent'
          }}>
            AutoReply OSS
          </h1>
          <p style={{
            fontSize: '18px',
            color: '#a1a1aa',
            maxWidth: '600px',
            margin: '0 auto',
            marginBottom: '16px'
          }}>
            An open-source Android app that automatically replies to messages using AI. Powered by a serverless backend on Vercel with multi-provider AI fallback (Groq → Gemini → Cloudflare).
          </p>
          <a href="https://nguyenbacson.io.vn/" target="_blank" rel="noopener noreferrer" style={{
            display: 'inline-block',
            color: '#60a5fa',
            textDecoration: 'none',
            fontSize: '16px',
            fontWeight: 600
          }}>
            🌐 Website: nguyenbacson.io.vn
          </a>
        </header>

        {/* Security Warning */}
        <div style={{
          padding: '16px',
          borderRadius: '8px',
          backgroundColor: 'rgba(239, 68, 68, 0.1)',
          border: '1px solid rgba(239, 68, 68, 0.2)',
          color: '#fca5a5'
        }}>
          <strong>⚠️ Security warning:</strong> Never commit your real BOT_SECRET or API keys to a public repository. Set server keys as environment variables on Vercel. The Android BOT_SECRET is compiled into the APK, so do not publish an APK built with your real secret.
        </div>

        {/* Sections */}
        <Section title="What this app does">
          <ul style={{ paddingLeft: '20px' }}>
            <li>Reads incoming notifications from supported apps (Messenger, Telegram, Discord)</li>
            <li>Sends the message to a Vercel serverless function</li>
            <li>The server asks an AI provider (Groq → Gemini → Cloudflare fallback) for a reply</li>
            <li>The reply is sent back through the notification's quick-reply action</li>
            <li>Keeps the last messages per sender as context for the AI</li>
            <li>Logs conversations to Vercel KV (log entries older than 7 days are removed)</li>
          </ul>
          <p style={{ marginTop: '16px' }}>This is a minimal working base. It has no settings screen, personality editor or scheduled mode: the app has three buttons (notification access, battery optimization, check for updates).</p>
        </Section>

        <Section title="Architecture">
          <pre style={{
            backgroundColor: 'rgba(255,255,255,0.05)',
            padding: '16px',
            borderRadius: '8px',
            overflowX: 'auto',
            fontSize: '14px',
            fontFamily: 'monospace'
          }}>
{`┌─────────────────┐       ┌──────────────────┐       ┌─────────────┐
│   Android App   │ ──────► │   Vercel Server  │ ──────► │   AI APIs   │
│    (Kotlin)     │  HTTPS  │     (Next.js)    │         │             │
│                 │         │                  │         │    Groq     │
│  Notification   │         │    /api/reply    │         │   Gemini    │
│    Listener     │ ◄────── │    /api/logs     │ ◄────── │  Cloudflare │
│                 │         │   /api/version   │         │             │
└─────────────────┘       └──────────────────┘       └─────────────┘
                                   │
                                   │
                              Vercel KV
                               (Redis)
                              └─────────┘`}
          </pre>
        </Section>

        <Section title="Requirements">
          <ul style={{ paddingLeft: '20px' }}>
            <li>Vercel account (free tier is enough)</li>
            <li>Groq API key, free at <a href="https://console.groq.com/" style={{ color: '#60a5fa' }}>console.groq.com</a></li>
            <li>Google AI Studio API key, free at <a href="https://aistudio.google.com/" style={{ color: '#60a5fa' }}>aistudio.google.com</a></li>
            <li>Cloudflare account, optional, for the 3rd fallback layer</li>
            <li>Android Studio to build the app</li>
          </ul>
        </Section>

        <Section title="Server setup">
          <ol style={{ paddingLeft: '20px' }}>
            <li style={{ marginBottom: '8px' }}><strong>1. Fork this repository:</strong> Click Fork at the top right.</li>
            <li style={{ marginBottom: '8px' }}><strong>2. Deploy to Vercel:</strong> Open <a href="https://vercel.com/new" style={{ color: '#60a5fa' }}>vercel.com/new</a>, import your forked repository, set Root Directory to server, click Deploy.</li>
            <li style={{ marginBottom: '8px' }}><strong>3. Create KV storage:</strong> In your Vercel project, open Storage tab, click Create Database → Upstash for Redis, pick region closest to you, select Free plan, click Connect Project and select your project.</li>
            <li style={{ marginBottom: '8px' }}>
              <strong>4. Configure environment variables:</strong> Go to Settings → Environment Variables and add:
              <ul style={{ margin: '8px 0' }}>
                <li><code>BOT_SECRET</code>: A long random secret string</li>
                <li><code>GROQ_API_KEY</code>: Your Groq API key</li>
                <li><code>GEMINI_API_KEY</code>: Your Google Gemini API key</li>
                <li><code>CF_ACCOUNT_ID</code>: Cloudflare account ID (optional)</li>
                <li><code>CF_API_TOKEN</code>: Cloudflare Workers AI token (optional)</li>
                <li><code>GITHUB_REPO</code>: your-username/autoreply-oss (for updates)</li>
              </ul>
              Optional model overrides: GROQ_MODEL, GEMINI_MODEL, CF_MODEL.
            </li>
            <li style={{ marginBottom: '8px' }}><strong>5. Redeploy:</strong> Deployments → 3-dot menu on latest deployment → Redeploy.</li>
            <li><strong>6. Test the server:</strong> Use curl or Postman to POST to <code>/api/reply</code> with <code>x-bot-secret</code> and a test JSON body.</li>
          </ol>
        </Section>

        <Section title="Android app setup">
          <ol style={{ paddingLeft: '20px' }}>
            <li style={{ marginBottom: '8px' }}><strong>1. Clone and open:</strong> Clone the repo and open the <code>android/</code> folder in Android Studio.</li>
            <li style={{ marginBottom: '8px' }}><strong>2. Configure server URL and secret:</strong> Edit <code>app/build.gradle.kts</code> and update <code>SERVER_URL</code> and <code>BOT_SECRET</code>. Do not commit real values.</li>
            <li style={{ marginBottom: '8px' }}><strong>3. Set your own names:</strong> In <code>ReplyService.kt</code>, replace placeholders in <code>Config.MY_NAMES</code> with your display names to recognize your own messages.</li>
            <li style={{ marginBottom: '8px' }}><strong>4. Build APK:</strong> <code>./gradlew assembleDebug</code> or Build → Build APK(s) in Android Studio.</li>
            <li style={{ marginBottom: '8px' }}><strong>5. Install on device:</strong> Install via <code>adb</code> or copy to your phone.</li>
            <li><strong>6. Grant permissions:</strong> Open app, grant Notification Access, disable Battery Optimization.</li>
          </ol>
        </Section>

        <Section title="Remote control">
          <p>Messages you send yourself are matched against the commands below. Detection relies on the notification text starting with "You:", "Bạn:", or one of your names in MY_NAMES.</p>
          <div style={{ overflowX: 'auto', marginTop: '16px' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.1)' }}>
                  <th style={{ textAlign: 'left', padding: '8px' }}>Command</th>
                  <th style={{ textAlign: 'left', padding: '8px' }}>Effect</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td style={{ padding: '8px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>BAT_ALL</td>
                  <td style={{ padding: '8px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>Enable everything</td>
                </tr>
                <tr>
                  <td style={{ padding: '8px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>TAT_ALL</td>
                  <td style={{ padding: '8px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>Disable everything</td>
                </tr>
                <tr>
                  <td style={{ padding: '8px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>BAT_CHATBOT</td>
                  <td style={{ padding: '8px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>Enable auto-reply</td>
                </tr>
                <tr>
                  <td style={{ padding: '8px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>TAT_CHATBOT</td>
                  <td style={{ padding: '8px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>Disable auto-reply</td>
                </tr>
                <tr>
                  <td style={{ padding: '8px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>BAT_AI</td>
                  <td style={{ padding: '8px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>Enable AI replies</td>
                </tr>
                <tr>
                  <td style={{ padding: '8px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>TAT_AI</td>
                  <td style={{ padding: '8px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>Disable AI</td>
                </tr>
              </tbody>
            </table>
          </div>
        </Section>

        <Section title="Troubleshooting & Limitations">
          <p><strong>Bot does not reply:</strong></p>
          <ul style={{ paddingLeft: '20px', marginBottom: '16px' }}>
            <li>Confirm notification access is granted.</li>
            <li>Disable battery optimization for AutoReply.</li>
            <li>Filter Logcat by tag AutoReply.</li>
            <li>Check if providers failed or if <code>BOT_SECRET</code> is unauthorized.</li>
          </ul>
          <p><strong>Known limitations:</strong></p>
          <ul style={{ paddingLeft: '20px' }}>
            <li>Per-sender history keeps the last 20 entries. Only /api/logs entries are removed after 7 days.</li>
            <li>Messages from third parties are stored in your Vercel KV. Use responsibly.</li>
            <li>Auto-update requires uninstalling debug APKs if keys differ.</li>
            <li>Android 13+ update notifications need runtime permission manually.</li>
          </ul>
        </Section>

        <Section title="Disclaimer & License">
          <p><strong>License:</strong> MIT</p>
          <p><strong>Disclaimer:</strong> This project is for personal use only. Auto-reply bots may violate the Terms of Service of some messaging platforms. Use it responsibly and never at scale on platforms that forbid automation.</p>
        </Section>

      </main>
    </div>
  );
}

// Section Helper Component
function Section({ title, children }) {
  return (
    <section style={{
      padding: '32px',
      borderRadius: '16px',
      border: '1px solid rgba(255,255,255,0.1)',
      background: 'rgba(255,255,255,0.02)',
      backdropFilter: 'blur(10px)'
    }}>
      <h2 style={{ 
        fontSize: '24px', 
        fontWeight: 600, 
        marginBottom: '20px', 
        color: '#fff',
        borderBottom: '1px solid rgba(255,255,255,0.1)', 
        paddingBottom: '12px' 
      }}>
        {title}
      </h2>
      <div style={{ color: '#a1a1aa' }}>
        {children}
      </div>
    </section>
  );
}
