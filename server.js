/**
 * FileBridge Relay Server
 * ------------------------------------------------------------------
 * Purpose: two phones you own (a "Host" phone that holds the files,
 * and a "Client" phone you browse from) can be on totally different
 * networks (home wifi vs mobile data vs anywhere). This server sits
 * in the middle, so each phone only needs to reach the INTERNET, not
 * each other directly.
 *
 * How pairing works:
 *   1. Host app connects here and says "register_host" with a 6-digit
 *      PAIR CODE it generated and is showing on its own screen.
 *   2. Client app connects here and says "join" with that same code
 *      (typed in, or read from a QR code shown by the host).
 *   3. From then on, any message the client sends is forwarded to the
 *      paired host, and any message the host sends is forwarded back
 *      to the paired client. The server itself never reads file
 *      contents meaningfully -- it just passes bytes through.
 *
 * This is intentionally simple (no database, in-memory only) so you
 * can read every line of it. Good first "real backend" project.
 * ------------------------------------------------------------------
 */

const http = require('http');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 8080;

// routing key (hash of the user's password) -> { hostWs, clientWs }
const pairs = new Map();

// Reverse lookup so we know which pair a socket belongs to when it closes
const socketToCode = new Map();

function log(...args) {
  console.log(new Date().toISOString(), ...args);
}

function send(ws, obj) {
  if (ws && ws.readyState === ws.OPEN) {
    ws.send(JSON.stringify(obj));
  }
}

const server = http.createServer((req, res) => {
  // simple health check endpoint, useful once this is deployed
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ ok: true, activePairs: pairs.size }));
    return;
  }
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('FileBridge relay server is running.\n');
});

const wss = new WebSocketServer({ server });

wss.on('connection', (ws) => {
  ws.isAlive = true;
  ws.on('pong', () => { ws.isAlive = true; });

  ws.on('message', (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch (e) {
      send(ws, { type: 'error', message: 'invalid_json' });
      return;
    }

    switch (msg.type) {
      case 'register_host': {
        // The "code" here is NOT a secret shown on screen anymore -- it's
        // a one-way hash the app derives from the user's own permanent
        // password (see PasswordCrypto.kt). The server never sees the
        // real password, only this routing key, and never sees file
        // contents either (those are end-to-end encrypted by the apps).
        const code = String(msg.code || '');
        if (!code) {
          send(ws, { type: 'register_failed', reason: 'missing_code' });
          return;
        }
        const existing = pairs.get(code);
        if (existing && existing.hostWs && existing.hostWs.readyState === existing.hostWs.OPEN) {
          send(ws, { type: 'register_failed', reason: 'already_active' });
          return;
        }
        pairs.set(code, { hostWs: ws, clientWs: null });
        socketToCode.set(ws, code);
        ws.role = 'host';
        send(ws, { type: 'host_registered', code });
        log('host registered');
        break;
      }

      case 'join': {
        const code = String(msg.code || '');
        const pair = pairs.get(code);
        if (!pair || !pair.hostWs || pair.hostWs.readyState !== pair.hostWs.OPEN) {
          send(ws, { type: 'join_failed', reason: 'invalid_or_offline_code' });
          return;
        }
        pair.clientWs = ws;
        socketToCode.set(ws, code);
        ws.role = 'client';
        send(ws, { type: 'joined', code });
        send(pair.hostWs, { type: 'client_connected' });
        log('client joined');
        break;
      }

      // Anything else is app-level traffic: list requests, file chunks,
      // download requests, etc. We don't need to understand it, we
      // just forward it to whichever side of the pair didn't send it.
      default: {
        const code = socketToCode.get(ws);
        if (!code) {
          send(ws, { type: 'error', message: 'not_paired_yet' });
          return;
        }
        const pair = pairs.get(code);
        if (!pair) return;
        const target = ws.role === 'host' ? pair.clientWs : pair.hostWs;
        if (target) {
          send(target, msg);
        } else {
          send(ws, { type: 'error', message: 'peer_not_connected' });
        }
      }
    }
  });

  ws.on('close', () => {
    const code = socketToCode.get(ws);
    if (!code) return;
    const pair = pairs.get(code);
    if (pair) {
      if (ws.role === 'host') {
        // host left: tell client, then drop the whole pair
        send(pair.clientWs, { type: 'peer_disconnected', who: 'host' });
        pairs.delete(code);
        log('host left, pair closed');
      } else {
        pair.clientWs = null;
        send(pair.hostWs, { type: 'peer_disconnected', who: 'client' });
        log('client left pair');
      }
    }
    socketToCode.delete(ws);
  });
});

// Drop dead connections (phone lost signal, app killed, etc.)
const interval = setInterval(() => {
  wss.clients.forEach((ws) => {
    if (ws.isAlive === false) return ws.terminate();
    ws.isAlive = false;
    ws.ping();
  });
}, 30000);

wss.on('close', () => clearInterval(interval));

server.listen(PORT, () => {
  log(`FileBridge relay server listening on port ${PORT}`);
});
