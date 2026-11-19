# AI API key handling and multi-project credential management

## Goal
Keep Gemini credentials out of the Android APK and provide reliable request handling for the AI Video Organizer. The backend supports up to 24 independently authorized Google AI project credentials, subject to Google's current terms and quota rules.

## Security boundary
- The Android app must call a backend endpoint; it must never contain Gemini API keys.
- Store credentials only as backend environment secrets or the hosting provider's secret manager.
- GitHub Actions repository secrets are available to workflows, not to an installed Android app at runtime, and are not automatically copied to Cloudflare.
- Never commit API keys, paste them into source code, expose them in API responses, or print them in logs.
- Do not put real credentials in example files; use placeholder values only.

## Credential names
The Worker reads these exact environment secret names:
- `GEMINI_API_KEY`
- `GEMINI_API_KEY_2` through `GEMINI_API_KEY_24`
- `APP_ACCESS_TOKEN` protects the backend generation endpoint.

Each Gemini credential must belong to a separate project the account owner is authorized to use. Missing credentials are skipped. Secret values are never returned to the Android client.

## Safe request and failover rules
1. Use configured credentials only for permitted workload routing.
2. Try another independently authorized project only for transient network failures or HTTP 5xx service failures.
3. On HTTP 429, honor `Retry-After` and the provider's quota/reset guidance. Pause or queue the job. Do not cycle through projects to evade rate limits, daily quotas, or usage restrictions.
4. On HTTP 401/403, report a credential or permission configuration error.
5. On HTTP 400/404, report malformed input, model, or endpoint configuration; do not retry an unchanged request.
6. Keep secret values out of logs and responses.

## Important quota note
Separate projects have separate project-level quota configuration, but creating or using many projects does not grant permission to circumvent provider limits or terms. Do not implement automatic project switching whose purpose is to continue requests after a quota has been exhausted. Use the provider's approved quota increase process or wait for quota reset when required.

## Backend files
- `backend/worker.js`: Cloudflare Worker proxy.
- `backend/wrangler.toml`: Worker deployment configuration.
- `backend/README.md`: deployment and secret setup instructions.

## Current app status
The Android app currently provides video selection, preview, category buttons, and duration selection UI. It is not yet connected to the backend. Automatic highlight detection and clip export are also not implemented.
