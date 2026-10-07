import { kv } from '@vercel/kv';

export async function POST(req) {
  if (req.headers.get('x-bot-secret') !== process.env.BOT_SECRET) {
    return Response.json({ error: 'unauthorized' }, { status: 401 });
  }
  const { sender, text } = await req.json();
  if (!sender || !text) return Response.json({ ok: false });
  await kv.lpush(`history:${sender}`, JSON.stringify({ text, reply: null, self: true, ts: Date.now() }));
  await kv.ltrim(`history:${sender}`, 0, 19);
  return Response.json({ ok: true });
}
