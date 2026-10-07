export default function Home() {
  return (
    <main style={{ fontFamily: 'system-ui', padding: 40, maxWidth: 800, margin: '0 auto' }}>
      <h1>AutoReply OSS</h1>
      <p>Open source AI auto-reply server is running.</p>
      <h2>Endpoints</h2>
      <ul>
        <li><code>POST /api/reply</code> — Receive message, return AI reply</li>
        <li><code>POST /api/logs</code> — Store log (auto-delete after 7 days)</li>
        <li><code>GET /api/logs</code> — Retrieve recent logs</li>
        <li><code>GET /api/version</code> — Check for Android app update</li>
        <li><code>POST /api/log-self</code> — Store own messages</li>
        <li><code>POST /api/log-reply</code> — Store bot replies</li>
      </ul>
      <p>See <a href="https://github.com/your-username/autoreply-oss">README</a> for setup instructions.</p>
    </main>
  );
}
