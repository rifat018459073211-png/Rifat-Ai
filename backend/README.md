# Rifat-Ai Gemini backend

This folder contains a Cloudflare Worker proxy. Gemini API keys stay on the server and are never included in the Android APK or returned to the app.

## Important: GitHub Secrets are not runtime Worker secrets

The existing GitHub Actions repository secrets cannot be read by a deployed Cloudflare Worker automatically. The Worker needs its own secrets in the Cloudflare dashboard (or via Wrangler secret commands). I cannot inspect or reveal GitHub secret values, and this repository does not contain them.

## Configure secrets in Cloudflare

Deploy this Worker, then add the following as Worker secrets in the Cloudflare dashboard. Add only the keys you actually have; missing values are skipped.

- `GEMINI_API_KEY`
- `GEMINI_API_KEY_2` through `GEMINI_API_KEY_24`
- `APP_ACCESS_TOKEN` (create a long, random private token; never commit it)

The current GitHub repository previously used names through `GEMINI_API_KEY_10`. Those names match this Worker. Add the remaining project keys in Cloudflare when ready. Do not paste any key into chat or commit it to Git.

## Deploy

Install Wrangler and authenticate with your own Cloudflare account, then run from this directory:

```sh
npx wrangler deploy
```

Set each secret without putting its value in source control:

```sh
npx wrangler secret put GEMINI_API_KEY
npx wrangler secret put GEMINI_API_KEY_2
# Repeat for the configured project keys, and also:
npx wrangler secret put APP_ACCESS_TOKEN
```

Wrangler prompts for each value. Do not put the secret value directly in the command line.

## Endpoints

- `GET /health`: returns only the number of configured project credentials.
- `POST /v1/generate`: accepts a Gemini `generateContent` JSON body and requires `Authorization: Bearer <APP_ACCESS_TOKEN>`.

Example request body:

```json
{
  "model": "gemini-2.5-flash",
  "contents": [
    {
      "parts": [
        { "text": "Write a short hello message." }
      ]
    }
  ]
}
```

## Credential and quota behavior

- The Worker can be configured with up to 24 separately authorized project credentials.
- It tries configured projects in order only after transient network or HTTP 5xx service failures.
- On HTTP 429 it returns the rate-limit response and any `Retry-After` value. It does not switch projects to evade quota limits.
- On HTTP 400, 401, 403, or 404 it stops and reports the configuration/request error.
- Secret values are never returned or logged.

## Current limitations

This is the backend proxy foundation only. The Android app is not connected to it yet. The app must later send requests to the deployed Worker URL and provide the access token using a suitable authenticated setup. Do not ship a shared permanent access token inside a public APK; a client-side token can be extracted. Before public release, add per-user authentication and server-side abuse/rate controls.
