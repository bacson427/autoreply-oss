import { kv } from '@vercel/kv';

export async function POST(req) {
  if (req.headers.get('x-bot-secret') !== process.env.BOT_SECRET) {
    return Response.json({ error: 'unauthorized' }, { status: 401 });
  }
  const body = await req.json();
  const now = Date.now();
  const SEVEN_DAYS = 7 * 24 * 60 * 60 * 1000;
  await kv.zadd('app_logs', { score: now, member: JSON.stringify({ ...body, timestamp: now }) });
  await kv.zremrangebyscore('app_logs', '-inf', now - SEVEN_DAYS);
  return Response.json({ success: true });
}

export async function GET(req) {
  if (req.headers.get('x-bot-secret') !== process.env.BOT_SECRET) {
    return Response.json({ error: 'unauthorized' }, { status: 401 });
  }
  const limit = Number(new URL(req.url).searchParams.get('limit') || 50);
  const raw = await kv.zrange('app_logs', 0, limit - 1, { rev: true });
  const logs = raw.map(s => typeof s === 'string' ? JSON.parse(s) : s);
  return Response.json({ count: logs.length, logs });
}
