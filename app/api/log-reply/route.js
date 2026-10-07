import { kv } from '@vercel/kv';

export async function POST(req) {
  if (req.headers.get('x-bot-secret') !== process.env.BOT_SECRET) {
    return Response.json({ error: 'unauthorized' }, { status: 401 });
  }
  const body = await req.json();
  const log = { ...body, ts: body.ts || Date.now() };
  await kv.lpush('logs', JSON.stringify(log));
  await kv.ltrim('logs', 0, 499);
  return Response.json({ ok: true });
}
