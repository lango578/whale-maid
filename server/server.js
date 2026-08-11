/**
 * 鲸鱼娘·汐汐 / 鲨鱼娘·澜澜 —— 轻量同步后端（零依赖 Node.js，无需 npm install）
 *
 * 功能：
 *   1) POST /api/chat   代理 DeepSeek API（SSE 流式转发）—— API Key 只存在后端 config.json
 *   2) GET/POST /api/state   手机/电脑的聊天记录与设置互相同步（存到 data.json）
 *   3) GET  /health     健康检查
 *
 * 运行：  node server.js
 * 配置：  复制 config.example.json 为 config.json 并填写（apiKey / syncKey / port）
 * 安全：  apiKey 绝不发给前端；syncKey 保护 /api/state 与 /api/chat
 */
'use strict';
const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');

const DIR = __dirname;
const CONFIG = loadConfig();

function loadConfig() {
  try {
    const c = JSON.parse(fs.readFileSync(path.join(DIR, 'config.json'), 'utf8'));
    return Object.assign({ port: 8787, apiKey: '', syncKey: '' }, c);
  } catch (e) {
    return { port: 8787, apiKey: process.env.DEEPSEEK_API_KEY || '', syncKey: '' };
  }
}

function readState() {
  try { return JSON.parse(fs.readFileSync(path.join(DIR, 'data.json'), 'utf8')); }
  catch (e) { return {}; }
}
function writeState(s) {
  fs.writeFileSync(path.join(DIR, 'data.json'), JSON.stringify(s, null, 2));
}

function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-Sync-Key');
}

function auth(req, res) {
  if (CONFIG.syncKey && req.headers['x-sync-key'] !== CONFIG.syncKey) {
    res.writeHead(403, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: '同步口令错误（X-Sync-Key）' }));
    return false;
  }
  return true;
}

function readBody(req, cb) {
  let b = '';
  req.on('data', c => {
    b += c;
    if (b.length > 5e6) { req.destroy(); }
  });
  req.on('end', () => cb(b));
  req.on('error', () => cb(''));
}

function json(res, code, obj) {
  res.writeHead(code, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(obj));
}

function proxyChat(req, res) {
  readBody(req, b => {
    let payload;
    try { payload = JSON.parse(b || '{}'); }
    catch (e) { return json(res, 400, { error: '请求体不是合法 JSON' }); }

    const thinkingEnabled = !(payload.thinking && payload.thinking.type === 'disabled');
    const out = JSON.stringify({
      model: payload.model || 'deepseek-v4-flash',
      messages: payload.messages || [],
      stream: true,
      max_tokens: payload.max_tokens || 2048,
      thinking: { type: thinkingEnabled ? 'enabled' : 'disabled' },
      ...(thinkingEnabled
        ? { reasoning_effort: payload.reasoning_effort || 'low' }
        : { temperature: payload.temperature || 0.8 })
    });

    const upstream = https.request({
      hostname: 'api.deepseek.com',
      path: '/chat/completions',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + CONFIG.apiKey,
        'Content-Length': Buffer.byteLength(out)
      }
    }, r2 => {
      res.writeHead(r2.statusCode || 200, {
        'Content-Type': 'text/event-stream; charset=utf-8',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive'
      });
      r2.pipe(res);
    });
    upstream.on('error', e => json(res, 502, { error: 'DeepSeek 连接失败：' + e.message }));
    upstream.end(out);
  });
}

const server = http.createServer((req, res) => {
  cors(res);
  if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return; }

  const url = req.url.split('?')[0];

  if (req.method === 'GET' && url === '/health') {
    return json(res, 200, {
      ok: true, name: 'whalemaid-server',
      hasKey: !!CONFIG.apiKey, persona: 'whale+shark'
    });
  }

  if (url === '/api/state') {
    if (!auth(req, res)) return;
    if (req.method === 'GET') return json(res, 200, readState());
    if (req.method === 'POST') {
      return readBody(req, b => {
        try { writeState(JSON.parse(b || '{}')); json(res, 200, { ok: true }); }
        catch (e) { json(res, 400, { error: 'JSON 解析失败' }); }
      });
    }
  }

  if (req.method === 'POST' && url === '/api/chat') {
    if (CONFIG.apiKey && !auth(req, res)) return;
    return proxyChat(req, res);
  }

  json(res, 404, { error: 'Not Found' });
});

const port = CONFIG.port || 8787;
server.listen(port, '0.0.0.0', () => {
  console.log('🐳 鲸鱼娘·汐汐 / 🦈 鲨鱼娘·澜澜 同步后端已启动：');
  console.log('   局域网访问地址： http://<本机IP>:' + port);
  console.log('   本机访问地址：   http://127.0.0.1:' + port);
  console.log(CONFIG.apiKey ? '   ✅ DeepSeek API Key 已配置' : '   ⚠️ 未配置 API Key：请在 config.json 中填写 apiKey');
  console.log(CONFIG.syncKey ? '   🔑 同步口令已配置' : '   ⚠️ 未配置 syncKey：所有人可读写 /api/state，建议设置！');
});
