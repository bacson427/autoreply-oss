import { kv } from '@vercel/kv';

const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000;
const MAX_LOGS = 500;

export async function POST(req) {
  if (req.headers.get('x-bot-secret') !== process.env.BOT_SECRET) {
    return Response.json({ error: 'unauthorized' }, { status: 401 });
  }

  const body = await req.json();
  const now = Date.now();

  // Store in sorted set with timestamp as score
  await kv.zadd('app_logs', {
    score: now,
    member: JSON.stringify({ ...body, timestamp: now }),
  });

  // Remove entries older than 7 days
  await kv.zremrangebyscore('app_logs', '-inf', now - SEVEN_DAYS_MS);

  return Response.json({ success: true });
}

export async function GET(req) {
  if (req.headers.get('x-bot-secret') !== process.env.BOT_SECRET) {
    return Response.json({ error: 'unauthorized' }, { status: 401 });
  }

  const limit = Number(new URL(req.url).searchParams.get('limit') || 50);
  const raw = await kv.zrange('app_logs', 0, Math.min(limit, MAX_LOGS) - 1, { rev: true });

  const logs = raw.map(s => {
    if (typeof s === 'string') {
      try { return JSON.parse(s); } catch { return { raw: s }; }
    }
    return s;
  });

  return Response.json({ count: logs.length, logs });
}
