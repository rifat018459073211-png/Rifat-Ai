# AI API key handling and quota policy

## Goal
Keep Gemini credentials out of the Android APK and provide reliable retry handling for the AI Video Organizer.

## Security boundary
- The Android app must call a small backend endpoint; it must never contain Gemini API keys.
- Store credentials only as server-side environment secrets.
- GitHub Actions repository secrets are available to workflows, not to an installed Android app at runtime.
- Never commit API keys, paste them into source code, or print them in logs.

## Quota-safe request handling
1. Send a request using the configured Gemini project credential.
2. On a temporary rate-limit / quota response (HTTP 429), honor `Retry-After` and the provider's reset guidance. Queue or pause the job; do not switch credentials to get around the same project's quota.
3. On transient server errors (HTTP 5xx or network timeout), retry with bounded exponential backoff and jitter.
4. On invalid/revoked credentials (HTTP 401/403), stop and report a configuration error. Do not endlessly cycle keys.
5. On malformed requests or unsupported models (HTTP 400/404), report the error; do not retry unchanged.
6. Save job state so the user can resume after waiting or app restart.

## Multiple credentials
Multiple credentials may be configured for operational redundancy only when each credential belongs to an independently authorized project and its use complies with Google's current terms and quota rules. Multiple keys from one project do not create extra quota. This app must not rotate credentials to evade rate limits or account/project quotas.

## Planned implementation order
- Add a backend proxy with server-side secret storage.
- Add a job queue and retry scheduler that respects 429 / Retry-After.
- Add Android networking to submit jobs and poll status.
- Add video analysis and clip export after the request pipeline is verified.

## Current app status
The Android app currently provides video selection, preview, category buttons, and duration selection UI. AI analysis, remote backend calls, automatic highlight detection, and clip export are not implemented yet.
