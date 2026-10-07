import { kv } from '@vercel/kv';

const SYSTEM_PROMPT =
  '' + '';

const OWNER_SENDER = 'SELF';
const COMMANDS = new Set(['BAT_ALL', 'TAT_ALL', 'BAT_AI', 'TAT_AI', 'BAT_CHATBOT', 'TAT_CHATBOT']);
const CANNED = 'T đang bận xíu, lát rep nha.';

async function getState() {
  const [master, chatbot, ai] = await Promise.all([
    kv.get('state_master'), kv.get('state_chatbot'), kv.get('state_ai'),
  ]);
  return { master: master !== false, chatbot: chatbot !== false, ai: ai !== false };
}

function formatStatus(s) {
  return `ALL: ${s.master ? 'BẬT' : 'TẮT'} | CHATBOT: ${s.chatbot ? 'BẬT' : 'TẮT'} | AI: ${s.ai ? 'BẬT' : 'TẮT'}`;
}

async function handleCommand(text) {
  const cmd = text.trim().toUpperCase();
  if (!COMMANDS.has(cmd)) return null;
  let action = '';
  switch (cmd) {
    case 'BAT_ALL': await kv.set('state_master', true); action = 'BẬT ALL'; break;
    case 'TAT_ALL': await kv.set('state_master', false); action = 'TẮT ALL'; break;
    case 'BAT_CHATBOT': await kv.set('state_chatbot', true); action = 'BẬT CHATBOT'; break;
    case 'TAT_CHATBOT': await kv.set('state_chatbot', false); action = 'TẮT CHATBOT'; break;
    case 'BAT_AI': await kv.set('state_ai', true); action = 'BẬT AI'; break;
    case 'TAT_AI': await kv.set('state_ai', false); action = 'TẮT AI'; break;
  }
  const s = await getState();
  return `${cmd.startsWith('BAT') ? '✅' : '⛔'} Đã ${action}\n${formatStatus(s)}`;
}

async function buildMessages(sender, text, customPrompt) {
  const history = (await kv.lrange(`history:${sender}`, 0, 9)) || [];
  const promptToUse = (customPrompt && customPrompt.length > 10) ? customPrompt : SYSTEM_PROMPT;
  const messages = [{ role: 'system', content: promptToUse }];
  for (const h of history.reverse()) {
    try {
      const item = JSON.parse(h);
      messages.push({ role: 'user', content: `${sender}: ${item.text}` });
      if (item.reply) messages.push({ role: 'assistant', content: item.reply });
    } catch (e) {}
  }
  messages.push({ role: 'user', content: `${sender}: ${text}` });
  return messages;
}

async function saveHistory(sender, text, reply) {
  await kv.lpush(`history:${sender}`, JSON.stringify({ text, reply, ts: Date.now() }));
  await kv.ltrim(`history:${sender}`, 0, 19);
}

async function askGroq(m) {
  const k = process.env.GROQ_API_KEY; if (!k) throw new Error('no key');
  const r = await fetch('https://api.groq.com/openai/v1/chat/completions', {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${k}` },
    body: JSON.stringify({ model: process.env.GROQ_MODEL || 'openai/gpt-oss-20b', temperature: 0.85, max_tokens: 150, messages: m }),
  });
  if (!r.ok) throw new Error(`groq ${r.status}`);
  return (await r.json())?.choices?.[0]?.message?.content?.trim();
}

async function askGemini(m) {
  const k = process.env.GEMINI_API_KEY; if (!k) throw new Error('no key');
  const model = process.env.GEMINI_MODEL || 'gemini-2.5-flash';
  const sys = m.find(x => x.role === 'system')?.content || '';
  const contents = m.filter(x => x.role !== 'system').map(x => ({ role: x.role === 'assistant' ? 'model' : 'user', parts: [{ text: x.content }] }));
  const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'x-goog-api-key': k },
    body: JSON.stringify({ systemInstruction: { parts: [{ text: sys }] }, contents, generationConfig: { maxOutputTokens: 150, temperature: 0.85 } }),
  });
  if (!r.ok) throw new Error(`gemini ${r.status}`);
  return (await r.json())?.candidates?.[0]?.content?.parts?.[0]?.text?.trim();
}

async function askCloudflare(m) {
  const id = process.env.CF_ACCOUNT_ID, tok = process.env.CF_API_TOKEN;
  if (!id || !tok) throw new Error('no key');
  const model = process.env.CF_MODEL || '@cf/meta/llama-3.3-70b-instruct-fp8-fast';
  const r = await fetch(`https://api.cloudflare.com/client/v4/accounts/${id}/ai/run/${model}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tok}` },
    body: JSON.stringify({ max_tokens: 150, messages: m }),
  });
  if (!r.ok) throw new Error(`cf ${r.status}`);
  return (await r.json())?.result?.response?.trim();
}

export async function POST(req) {
  if (req.headers.get('x-bot-secret') !== process.env.BOT_SECRET) {
    return Response.json({ error: 'unauthorized' }, { status: 401 });
  }
  const body = await req.json().catch(() => ({}));
  const sender = String(body.sender || '').slice(0, 60);
  const text = String(body.text || '').trim().slice(0, 1000);
  const customPrompt = String(body.prompt || '').trim().slice(0, 2000);
  if (!text) return Response.json({ error: 'empty text' }, { status: 400 });

  if (sender === OWNER_SENDER) {
    const st = await handleCommand(text);
    if (st) return Response.json({ reply: st, provider: 'system' });
  }

  const state = await getState();
  if (!state.master || !state.chatbot) return Response.json({ reply: '', provider: 'system', note: 'off' });
  if (!state.ai) return Response.json({ reply: CANNED, provider: 'canned' });

  const messages = await buildMessages(sender, text, customPrompt);
  for (const [name, fn] of [['groq', askGroq], ['gemini', askGemini], ['cloudflare', askCloudflare]]) {
    try {
      const reply = await fn(messages);
      if (reply) { await saveHistory(sender, text, reply); return Response.json({ reply, provider: name }); }
    } catch (e) { console.warn(`[reply] ${name} failed:`, e.message); }
  }
  return Response.json({ error: 'all providers failed' }, { status: 503 });
}
