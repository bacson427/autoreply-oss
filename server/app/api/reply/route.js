import { kv } from '@vercel/kv';

const SYSTEM_PROMPT =
  'You are a friendly person replying to messages. ' +
  'Reply in the same language as the incoming message, ' +
  'keep it short (1 sentence), natural and casual. ' +
  'Never say you are an AI. No markdown.';

const OWNER_SENDER = 'SELF';
const COMMANDS = new Set([
  'BAT_ALL', 'TAT_ALL',
  'BAT_AI', 'TAT_AI',
  'BAT_CHATBOT', 'TAT_CHATBOT',
]);
const CANNED_REPLY = 'Busy right now, will reply later.';

async function getState() {
  const [master, chatbot, ai] = await Promise.all([
    kv.get('state_master'),
    kv.get('state_chatbot'),
    kv.get('state_ai'),
  ]);
  return {
    master: master !== false,
    chatbot: chatbot !== false,
    ai: ai !== false,
  };
}

function formatStatus(s) {
  return `ALL: ${s.master ? 'ON' : 'OFF'} | CHATBOT: ${s.chatbot ? 'ON' : 'OFF'} | AI: ${s.ai ? 'ON' : 'OFF'}`;
}

async function handleCommand(text) {
  const cmd = text.trim().toUpperCase();
  if (!COMMANDS.has(cmd)) return null;

  let action = '';
  switch (cmd) {
    case 'BAT_ALL': await kv.set('state_master', true); action = 'ENABLE ALL'; break;
    case 'TAT_ALL': await kv.set('state_master', false); action = 'DISABLE ALL'; break;
    case 'BAT_CHATBOT': await kv.set('state_chatbot', true); action = 'ENABLE CHATBOT'; break;
    case 'TAT_CHATBOT': await kv.set('state_chatbot', false); action = 'DISABLE CHATBOT'; break;
    case 'BAT_AI': await kv.set('state_ai', true); action = 'ENABLE AI'; break;
    case 'TAT_AI': await kv.set('state_ai', false); action = 'DISABLE AI'; break;
  }

  const s = await getState();
  const icon = cmd.startsWith('BAT') ? '✅' : '⛔';
  return `${icon} ${action}\n${formatStatus(s)}`;
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
    } catch (e) { /* skip malformed entries */ }
  }
  messages.push({ role: 'user', content: `${sender}: ${text}` });
  return messages;
}

async function saveHistory(sender, text, reply) {
  const item = JSON.stringify({ text, reply, ts: Date.now() });
  await kv.lpush(`history:${sender}`, item);
  await kv.ltrim(`history:${sender}`, 0, 19);
}

async function askGroq(messages) {
  const key = process.env.GROQ_API_KEY;
  if (!key) throw new Error('missing GROQ_API_KEY');

  const res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${key}`,
    },
    body: JSON.stringify({
      model: process.env.GROQ_MODEL || 'openai/gpt-oss-20b',
      temperature: 0.85,
      max_tokens: 150,
      messages,
    }),
  });

  if (!res.ok) throw new Error(`groq ${res.status}`);
  const data = await res.json();
  return data?.choices?.[0]?.message?.content?.trim();
}

async function askGemini(messages) {
  const key = process.env.GEMINI_API_KEY;
  if (!key) throw new Error('missing GEMINI_API_KEY');

  const model = process.env.GEMINI_MODEL || 'gemini-2.5-flash';
  const systemMsg = messages.find(m => m.role === 'system')?.content || '';
  const contents = messages
    .filter(m => m.role !== 'system')
    .map(m => ({
      role: m.role === 'assistant' ? 'model' : 'user',
      parts: [{ text: m.content }],
    }));

  const res = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-goog-api-key': key,
      },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: systemMsg }] },
        contents,
        generationConfig: { maxOutputTokens: 150, temperature: 0.85 },
      }),
    }
  );

  if (!res.ok) throw new Error(`gemini ${res.status}`);
  const data = await res.json();
  return data?.candidates?.[0]?.content?.parts?.[0]?.text?.trim();
}

async function askCloudflare(messages) {
  const id = process.env.CF_ACCOUNT_ID;
  const token = process.env.CF_API_TOKEN;
  if (!id || !token) throw new Error('missing Cloudflare credentials');

  const model = process.env.CF_MODEL || '@cf/meta/llama-3.3-70b-instruct-fp8-fast';
  const res = await fetch(
    `https://api.cloudflare.com/client/v4/accounts/${id}/ai/run/${model}`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ max_tokens: 150, messages }),
    }
  );

  if (!res.ok) throw new Error(`cloudflare ${res.status}`);
  const data = await res.json();
  return data?.result?.response?.trim();
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

  // Remote control commands (only from SELF sender)
  if (sender === OWNER_SENDER) {
    const status = await handleCommand(text);
    if (status) return Response.json({ reply: status, provider: 'system' });
  }

  // Check master switches
  const state = await getState();
  if (!state.master || !state.chatbot) {
    return Response.json({ reply: '', provider: 'system', note: 'off' });
  }
  if (!state.ai) {
    return Response.json({ reply: CANNED_REPLY, provider: 'canned' });
  }

  // Build context and call AI providers
  const messages = await buildMessages(sender, text, customPrompt);

  const providers = [
    ['groq', askGroq],
    ['gemini', askGemini],
    ['cloudflare', askCloudflare],
  ];

  for (const [name, fn] of providers) {
    try {
      const reply = await fn(messages);
      if (reply) {
        await saveHistory(sender, text, reply);
        return Response.json({ reply, provider: name });
      }
    } catch (e) {
      console.warn(`[reply] ${name} failed:`, e.message);
    }
  }

  return Response.json({ error: 'all providers failed' }, { status: 503 });
}
