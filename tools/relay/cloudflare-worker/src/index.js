/**
 * Ikna Zero-Storage E2EE Relay (Cloudflare Workers + Durable Objects Free Tier)
 *
 * Security & privacy properties:
 * - Zero disk / database / KV / R2 persistence: all queued chunks live strictly in volatile RAM
 *   inside the per-channel Durable Object instance and are deleted immediately when polled or
 *   after 30 seconds (TTL_MS).
 * - Zero visibility into learner content: clients encrypt every payload with AES-256-GCM before
 *   sending; this worker only sees an opaque 64-hex channel ID, slot tag, and ciphertext chunks.
 */

const CHANNEL_RE = /^[a-f0-9]{64}$/;
const SLOT_RE = /^[a-z0-9_-]{3,32}$/;
const MAX_BODY_BYTES = 1024 * 1024; // 1 MiB max per chunk envelope
const MAX_QUEUE_PER_SLOT = 160;
const TTL_MS = 30_000;
const MAX_WAIT_MS = 5_000;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/health" && request.method === "GET") {
      return new Response("ok", { status: 200 });
    }
    const match = url.pathname.match(/^\/v1\/room\/([a-f0-9]{64})\/(send|poll)$/);
    if (!match) {
      return new Response("not found", { status: 404 });
    }
    const [, channelId] = match;
    if (!CHANNEL_RE.test(channelId)) {
      return new Response("bad channel", { status: 400 });
    }
    const id = env.RELAY_ROOM.idFromName(channelId);
    const stub = env.RELAY_ROOM.get(id);
    return stub.fetch(request);
  },
};

export class RelayRoom {
  constructor() {
    /** @type {Map<string, Array<{ body: string, expiresAt: number }>>} */
    this.queues = new Map();
    /** @type {Map<string, Array<() => void>>} */
    this.waiters = new Map();
  }

  prune(now) {
    for (const [slot, items] of this.queues.entries()) {
      const fresh = items.filter((item) => item.expiresAt > now);
      if (fresh.length === 0) {
        this.queues.delete(slot);
      } else {
        this.queues.set(slot, fresh);
      }
    }
  }

  popSlot(slot) {
    const now = Date.now();
    this.prune(now);
    const items = this.queues.get(slot);
    if (!items || items.length === 0) return null;
    this.queues.delete(slot);
    return "[" + items.map((item) => item.body).join(",") + "]";
  }

  async fetch(request) {
    const url = new URL(request.url);
    const action = url.pathname.endsWith("/send") ? "send" : "poll";

    if (action === "send" && request.method === "POST") {
      const slot = url.searchParams.get("to") || "";
      if (!SLOT_RE.test(slot)) {
        return new Response("bad slot", { status: 400 });
      }
      const len = Number(request.headers.get("content-length") || "0");
      if (len > MAX_BODY_BYTES) {
        return new Response("payload too large", { status: 413 });
      }
      const body = await request.text();
      if (body.length === 0 || body.length > MAX_BODY_BYTES) {
        return new Response("invalid body", { status: 400 });
      }
      const now = Date.now();
      this.prune(now);
      const list = this.queues.get(slot) || [];
      if (list.length >= MAX_QUEUE_PER_SLOT) {
        return new Response("slot queue full", { status: 429 });
      }
      list.push({ body, expiresAt: now + TTL_MS });
      this.queues.set(slot, list);

      const pending = this.waiters.get(slot);
      if (pending && pending.length > 0) {
        this.waiters.delete(slot);
        for (const wake of pending) wake();
      }
      return new Response(null, { status: 202 });
    }

    if (action === "poll" && request.method === "GET") {
      const slot = url.searchParams.get("slot") || "";
      if (!SLOT_RE.test(slot)) {
        return new Response("bad slot", { status: 400 });
      }
      const immediate = this.popSlot(slot);
      if (immediate !== null) {
        return new Response(immediate, {
          status: 200,
          headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
        });
      }
      const waitMs = Math.max(0, Math.min(MAX_WAIT_MS, Number(url.searchParams.get("wait") || "0") || 0));
      if (waitMs > 0) {
        await new Promise((resolve) => {
          const timer = setTimeout(() => {
            const list = this.waiters.get(slot);
            if (list) {
              const filtered = list.filter((fn) => fn !== wake);
              if (filtered.length > 0) this.waiters.set(slot, filtered);
              else this.waiters.delete(slot);
            }
            resolve();
          }, waitMs);
          const wake = () => {
            clearTimeout(timer);
            resolve();
          };
          const list = this.waiters.get(slot) || [];
          list.push(wake);
          this.waiters.set(slot, list);
        });
        const afterWait = this.popSlot(slot);
        if (afterWait !== null) {
          return new Response(afterWait, {
            status: 200,
            headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
          });
        }
      }
      return new Response(null, { status: 204, headers: { "cache-control": "no-store" } });
    }

    return new Response("method not allowed", { status: 405 });
  }
}
