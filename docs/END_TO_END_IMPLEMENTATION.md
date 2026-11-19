# Rifat-Ai — end-to-end implementation plan

This document records the real implementation stages for a free-first Android AI Video Organizer. It distinguishes completed foundations from work that still needs code, deployment, and device testing.

## User workflow
1. Pick a local video using Android's Storage Access Framework.
2. Preview the selected video.
3. Choose a target clip duration or let the app suggest one.
4. Choose Funny, Action, Animation, or Best Moments.
5. Upload or sample video segments for analysis.
6. Ask Gemini to return structured candidate timestamps and short reasons.
7. Validate and clamp timestamps against the source video's duration.
8. Cut selected segments locally on the phone with an Android-compatible FFmpeg integration.
9. Preview the generated clips.
10. Save/share clips through Android's MediaStore/SAF APIs.

## Architecture
- Android app: video picker, preview, category/duration selection, processing screen, upload state, results list, local export.
- Cloudflare Worker: authenticated request validation and Gemini proxy. Keep provider keys server-side.
- Gemini: scene understanding and timestamp suggestions; it does not itself perform local video cutting.
- FFmpeg on Android: performs actual trimming/encoding on the device.
- GitHub Actions: builds a debug APK and uploads it as a workflow artifact.

## Progress reporting
Show measured upload bytes while uploading. During AI work, show named stages (uploading, analyzing, validating timestamps, cutting, saving). Do not fabricate a continuously increasing percentage when the provider does not expose progress. For analysis, use an indeterminate indicator or stage progress; for FFmpeg, report progress only from actual process callbacks.

## Security and free-tier constraints
- Never put Gemini keys in the APK or source control.
- GitHub Actions secrets are not automatically available to a deployed Cloudflare Worker; configure Worker secrets separately.
- Do not rotate credentials to bypass provider quotas. Respect 429 responses and retry windows.
- Free tiers have limits and may change. Large video uploads and long videos may exceed free request/storage/time limits. Prefer on-device sampling or segment extraction and send only required, size-limited material.
- A permanent shared bearer token embedded in a public APK is extractable. Before public distribution, use per-user authentication or another defensible access-control design.

## Current verified repository state
- Android video picker and preview UI exist.
- Duration and category selection UI exist.
- An AI processing screen exists, but it is currently a placeholder and does not upload or analyze a video.
- A Cloudflare Worker Gemini proxy foundation exists, but it is not an end-to-end video-analysis service.
- Android INTERNET permission has now been added.
- GitHub Actions has a debug APK workflow; a successful workflow run and install test must be verified before claiming an APK is ready.

## Remaining implementation stages
A. Connect Android networking to the deployed Worker with safe authentication.
B. Implement bounded video sampling/upload and request lifecycle.
C. Define and validate a strict JSON response schema for scene timestamps.
D. Implement local FFmpeg trimming, cancellation, and actual progress callbacks.
E. Add results preview and MediaStore export.
F. Test on a real Android device with short and long videos, network failures, quota errors, and low storage.
G. Verify the GitHub Actions artifact and provide its actual download link.
