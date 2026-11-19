# AI API key handling and multi-project credential management

## Goal
Keep Gemini credentials out of the Android APK and provide reliable request handling for the AI Video Organizer. The backend is designed to support up to 24 independently authorized Google AI projects when their use is permitted by Google's current terms and quota rules.

## Security boundary
- The Android app must call a backend endpoint; it must never contain Gemini API keys.
- Store credentials only as backend environment secrets or the hosting provider's secret manager.
- GitHub Actions repository secrets are available to workflows, not to an installed Android app at runtime.
- Never commit API keys, paste them into source code, expose them in API responses, or print them in logs.
- Do not put real credentials in `.env.example`; use placeholder values only.

## Credential configuration
The backend configuration may define these secret names:
- `GEMINI_API_KEY_01` through `GEMINI_API_KEY_24`
- Each configured value must belong to a separate project that the account owner is authorized to use.
- The backend must treat each project as a separate quota and billing/security boundary.
- Missing credentials are skipped; credentials are never returned to the Android client.
- Keep a server-side project label/index for diagnostics, but never log the secret value.

## Safe request and failover rules
1. Use the configured project selected by the backend's permitted workload-routing policy.
2. A project may be marked temporarily unavailable after a transient network failure or HTTP 5xx. Retry with bounded exponential backoff and jitter; only use another independently authorized project for genuine service-reliability failover where the provider's terms permit it.
3. On HTTP 429, honor `Retry-After` and the provider's quota/reset guidance. Pause or queue the job for that project. Do not cycle through projects simply to evade rate limits, daily quotas, or usage restrictions.
4. On HTTP 401/403, mark that credential as needing owner attention and report a configuration/permission error. Do not endlessly cycle through credentials.
5. On HTTP 400/404, report malformed input, model, or endpoint configuration; do not retry an unchanged request.
6. Persist job state so the user can resume after waiting or app restart.
7. Keep per-project request accounting and expose only safe status information (project number/label, state, and retry time) to administrators.

## Important quota note
Separate projects have separate project-level quota configuration, but creating or using many projects does not grant permission to circumvent provider limits or terms. Do not implement automatic project switching whose purpose is to continue requests after a quota has been exhausted. Use the provider's approved quota increase process or wait for quota reset when required.

## Planned implementation order
- Add a backend proxy with server-side secret storage and support for up to 24 authorized project configurations.
- Add a job queue and retry scheduler that respects 429 / Retry-After.
- Add Android networking to submit jobs and poll status.
- Add video analysis and clip export after the request pipeline is verified.

## Current app status
The Android app currently provides video selection, preview, category buttons, and duration selection UI. AI analysis, remote backend calls, automatic highlight detection, and clip export are not implemented yet.
