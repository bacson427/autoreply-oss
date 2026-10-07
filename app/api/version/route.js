const GITHUB_REPO = process.env.GITHUB_REPO || '';

export async function GET() {
  if (!GITHUB_REPO) return Response.json({ error: 'GITHUB_REPO not set' }, { status: 500 });
  try {
    const r = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/releases/latest`, {
      headers: { 'Accept': 'application/vnd.github+json' },
      cache: 'no-store',
    });
    if (!r.ok) throw new Error(`github ${r.status}`);
    const data = await r.json();
    const apk = data.assets?.find(a => a.name.endsWith('.apk'));
    if (!apk) throw new Error('no apk asset');
    return Response.json({
      version: data.tag_name,
      url: apk.browser_download_url,
      changelog: data.body || '',
      published: data.published_at,
    });
  } catch (e) {
    return Response.json({ error: e.message }, { status: 500 });
  }
}
