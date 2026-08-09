# CDN Setup — Feature #38

## Read this first: what actually needs a CDN here

**Cloudinary-hosted media (project images/videos, KYC documents) does NOT need
a second CDN in front of it.** `res.cloudinary.com` URLs are already served
through Cloudinary's own global CDN — that's a core part of what Cloudinary
is. Putting CloudFront or Cloudflare in front of an already-CDN-backed host
adds a redundant hop and extra cost, not less bandwidth.

What the code changes in this feature actually did instead:
- **`f_auto,q_auto` on every upload** (`CloudinaryServiceImpl`) — Cloudinary
  now automatically serves the smallest format a requesting browser supports
  (WebP/AVIF instead of PNG/JPEG where possible) at the smallest acceptable
  quality. This is the concrete, code-level version of "reduce origin
  bandwidth" for media that's already CDN-hosted.
- **`Cache-Control: public, max-age=3600`** on `GET /api/v1/categories` — the
  one API response here that's genuinely safe to cache (changes only when an
  admin adds a category). The same `.cacheControl(...)` pattern can be
  applied to any other endpoint you're confident won't show stale data —
  intentionally NOT applied to explore/project-detail/funding data, which
  needs to stay current on a platform where people are deciding whether to
  back something based on live funding progress.

**A CDN is genuinely useful for one thing here: sitting in front of the
backend API and/or frontend itself** — caching the cacheable GET responses
above at the edge, absorbing traffic spikes, and (with Cloudflare
specifically) giving you free TLS termination, which is still an open item
from earlier in this project. That's account/DNS setup, not something a
code change can do — below is exactly what to click through.

## Option A: Cloudflare (recommended for this stage — free tier covers this, and solves TLS too)

1. Add your domain to Cloudflare (free plan). Update your domain's
   nameservers to Cloudflare's at your registrar.
2. Create DNS records pointing at your deploy server:
   - `A` record: `api.yourdomain.com` → your server's IP, proxy status
     **ON** (orange cloud)
   - `A` or `CNAME`: `yourdomain.com` / `www` → wherever the frontend is
     hosted, proxy status **ON**
3. SSL/TLS → Overview → set mode to **Full (strict)** once your origin has
   its own cert (see step 4), or **Full** if it doesn't yet — avoid
   **Flexible** for anything handling payments, it only encrypts
   browser↔Cloudflare, not Cloudflare↔your server.
4. Your origin (the Docker host) still needs *a* cert for Cloudflare to
   terminate "Full" mode correctly — the simplest path is a free
   [Cloudflare Origin Certificate](https://developers.cloudflare.com/ssl/origin-configuration/origin-ca/)
   (Cloudflare issues it, valid for 15 years, only trusted by Cloudflare
   itself — that's fine, since Cloudflare is the only thing connecting to
   your origin directly). Install it in Nginx/Caddy in front of the
   `backend` container — this project's `docker-compose.yml` exposes the app
   on plain HTTP (port 8080); a reverse proxy terminating TLS in front of it
   is separate work from this feature.
5. Caching → Configuration: leave default caching rules; they respect the
   `Cache-Control` headers the app already sends (browser cache + Cloudflare
   edge cache both honor `max-age`/`public` from step above).
6. Speed → Optimization: enable Auto Minify (JS/CSS/HTML) and Brotli if not
   already on — free, no code changes needed, reduces frontend bundle size
   in transit.

## Option B: AWS CloudFront (if you're already on AWS / need finer control)

1. Create a CloudFront distribution with your API/frontend origin(s).
2. Origin request policy: forward the `Authorization` header (CloudFront
   strips it by default) — without this, authenticated API calls through
   the CDN will fail with 401s.
3. Cache policy: use **CachingDisabled** for all `/api/v1/**` paths except
   `/api/v1/categories`, which can use a custom policy honoring
   `Cache-Control` (`Cache-Control` from origin, min/max/default TTL ~1hr).
4. Request an ACM certificate for your domain (free, auto-renewing) and
   attach it to the distribution for TLS.
5. Point your domain's DNS (Route 53 or elsewhere) at the CloudFront
   distribution.

## Not recommending right now

A CDN in front of Cloudinary specifically (custom CNAME delivery — Cloudinary
supports this on paid plans) — genuinely useful only once you're optimizing
for brand consistency on media URLs or hitting real cost-at-scale with
Cloudinary's own bandwidth pricing. Not worth the setup complexity at this
project's current stage.
