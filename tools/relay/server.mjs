#!/usr/bin/env node
/**
 * Ikna Zero-Storage E2EE Relay (zero-dependency Node.js 18+ standalone server)
 *
 * Keeps encrypted sync chunks strictly in volatile RAM for <= 30 seconds and deletes them
 * immediately upon pickup. Zero disk writes, zero database, zero accounts.
 *
 * Usage:
 *   PORT=8787 node tools/relay/server.mjs
 */

import http from "node:http";

const PORT = Number(process.env.PORT || "8787");
const HOST = process.env.HOST || "0.0.0.0";
const CHANNEL_RE = /^[a-f0-9]{64}$/;
const SLOT_RE = /^[a-z0-9_-]{3,32}$/;
const MAX_BODY_BYTES = 1024 * 1024;
const MAX_QUEUE_PER_SLOT = 160;
const TTL_MS = 30_000;
const MAX_WAIT_MS = 5_000;

/** @type {Map<string, Array<{ body: string, expiresAt: number }>>} */
const queues = new Map();
/** @type {Map<string, Array<() => void>>} */
const waiters = new Map();

function prune(now) {
  for (const [key, list] of queues.entries()) {
    const fresh = list.filter((item) => item.expiresAt > now);
    if (fresh.length === 0) queues.delete(key);
    else queues.set(key, fresh);
  }
}

function pop(key) {
  prune(Date.now());
  const list = queues.get(key);
  if (!list || list.length === 0) return null;
  queues.delete(key);
  return "[" + list.map((item) => item.body).join(",") + "]";
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const parts = [];
    let total = 0;
    req.on("data", (chunk) => {
      total += chunk.length;
      if (total > MAX_BODY_BYTES) {
        reject(new Error("too_large"));
        req.destroy();
        return;
      }
      parts.push(chunk);
    });
    req.on("end", () => resolve(Buffer.concat(parts).toString("utf8")));
    req.on("error", reject);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  if (url.pathname === "/health" && req.method === "GET") {
    res.writeHead(200, { "content-type": "text/plain" });
    res.end("ok");
    return;
  }
  const match = url.pathname.match(/^\/v1\/room\/([a-f0-9]{64})\/(send|poll)$/);
  if (!match) {
    res.writeHead(404);
    res.end("not found");
    return;
  }
  const [, channelId, action] = match;
  if (!CHANNEL_RE.test(channelId)) {
    res.writeHead(400);
    res.end("bad channel");
    return;
  }

  if (action === "send" && req.method === "POST") {
    const slot = url.searchParams.get("to") || "";
    if (!SLOT_RE.test(slot)) {
      res.writeHead(400);
      res.end("bad slot");
      return;
    }
    try {
      const body = await readBody(req);
      if (!body) {
        res.writeHead(400);
        res.end("empty body");
        return;
      }
      const key = `${channelId}:${slot}`;
      const now = Date.now();
      prune(now);
      const list = queues.get(key) || [];
      if (list.length >= MAX_QUEUE_PER_SLOT) {
        res.writeHead(429);
        res.end("slot queue full");
        return;
      }
      list.push({ body, expiresAt: now + TTL_MS });
      queues.set(key, list);
      const waiting = waiters.get(key);
      if (waiting && waiting.length > 0) {
        waiters.delete(key);
        for (const wake of waiting) wake();
      }
      res.writeHead(202);
      res.end();
    } catch (err) {
      res.writeHead(err.message === "too_large" ? 413 : 400);
      res.end();
    }
    return;
  }

  if (action === "poll" && req.method === "GET") {
    const slot = url.searchParams.get("slot") || "";
    if (!SLOT_RE.test(slot)) {
      res.writeHead(400);
      res.end("bad slot");
      return;
    }
    const key = `${channelId}:${slot}`;
    const immediate = pop(key);
    if (immediate !== null) {
      res.writeHead(200, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
      res.end(immediate);
      return;
    }
    const waitMs = Math.max(0, Math.min(MAX_WAIT_MS, Number(url.searchParams.get("wait") || "0") || 0));
    if (waitMs > 0) {
      await new Promise((resolve) => {
        const timer = setTimeout(() => {
          const list = waiters.get(key);
          if (list) {
            const filtered = list.filter((fn) => fn !== wake);
            if (filtered.length > 0) waiters.set(key, filtered);
            else waiters.delete(key);
          }
          resolve();
        }, waitMs);
        const wake = () => {
          clearTimeout(timer);
          resolve();
        };
        const list = waiters.get(key) || [];
        list.push(wake);
        waiters.set(key, list);
      });
      const afterWait = pop(key);
      if (afterWait !== null) {
        res.writeHead(200, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
        res.end(afterWait);
        return;
      }
    }
    res.writeHead(204, { "cache-control": "no-store" });
    res.end();
    return;
  }

  res.writeHead(405);
  res.end("method not allowed");
});

server.listen(PORT, HOST, () => {
  console.log(`Ikna zero-storage E2EE relay listening on http://${HOST}:${PORT}`);
});
