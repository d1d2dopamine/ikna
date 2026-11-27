# Ikna Zero-Storage E2EE Relay

An optional, zero-cost, zero-storage HTTPS rendezvous relay so two open Ikna devices can pair and sync **through any VPN and across different networks worldwide** without any cloud database or accounts.

## Privacy & Security Guarantee
- **Zero cloud storage:** Chunks live strictly in volatile RAM for at most **30 seconds** and are deleted immediately when polled by the paired device. Nothing is ever written to disk, KV, R2, or any database.
- **End-to-end encryption (E2EE):** Ikna encrypts every frame locally with **AES-256-GCM** (fresh 96-bit CSPRNG nonce per frame, AAD bound to opaque channel ID + sender slot) before sending it to the relay.
- **Opaque metadata:** The relay only sees a SHA-256 channel ID (`64` hex chars) and a hashed slot ID — never deck titles, cards, reviews, device names, certificates, or credentials.
- **LAN stays primary:** When both devices are on the same local Wi-Fi without VPN interference, Ikna connects directly over LAN first and only falls back to the relay when LAN is unreachable.

## Option 1: Free Deploy on Cloudflare Workers (\$0/month)
Cloudflare Workers + Durable Objects Free Tier covers 100,000 requests/day at \$0.

```bash
cd tools/relay/cloudflare-worker
npx wrangler login
npx wrangler deploy
```

Wrangler prints your HTTPS URL (for example `https://ikna-e2ee-relay.<your-subdomain>.workers.dev`).

## Option 2: Self-Host Anywhere with Node.js 18+ (zero npm dependencies)
Put it behind any HTTPS reverse proxy (Caddy, Nginx, Fly.io, Render, etc.):

```bash
PORT=8787 node tools/relay/server.mjs
```

## Using in Ikna
1. Open **Settings → Data → Internet relay (VPN and different networks)** on one device and paste your `https://...` URL, then tap **Save relay**.
2. Create a QR code / pairing code — the relay URL is embedded automatically in the QR code so the second device receives it on scan.
3. Done: devices try direct LAN first and automatically use the E2EE relay whenever VPN or separate networks block LAN.
