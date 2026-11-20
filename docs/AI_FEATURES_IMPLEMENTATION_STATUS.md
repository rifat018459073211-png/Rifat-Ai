# Rifat-Ai feature implementation status

Branch: `ai-video-organizer`

## Implemented in Android source

- Video picker, preview and AI-assisted video-analysis workflow in `MainActivity.java`; analysis sends sampled frames to the configured Cloudflare Worker/Gemini service. It returns suggested timestamps; it does not automatically cut every suggestion.
- `BestMomentsActivity.java`: on-device frame-change scoring to find candidate highlights. This is a lightweight heuristic, not semantic AI scene understanding.
- `VideoTrimmerActivity.java`: local MP4 trimming/export with Media3 Transformer.
- `PhotoToVideoActivity.java`: exports a still photo as an MP4 of a selected duration. This is not image-to-video AI and does not animate objects in the photo.
- `VideoTranscriptActivity.java`: extracts the audio track locally, downloads the Bengali sherpa-onnx Zipformer model on first use, transcribes on-device, and exports TXT and SRT subtitle files. Model size is about 90 MB. The feature is Bengali-focused; actual recognition quality and device performance still need testing on the target phone.
- `MainActivity.java`: local GPUVideo-based cartoon-style video filter/export flow. This is a conventional video filter, not a diffusion video-generation model.
- Prompt idea list: text prompts only; it does not contain playable generated video files.

## Not implemented as working AI generation

- Text-to-Image: no verified free, no-key image-generation engine is integrated.
- Text-to-Video: no verified free engine compatible with the target Android phone is integrated.
- Image-to-Video: no true motion-generation model is integrated. Photo-to-MP4 is a still-image export only.

The official Gemini API pricing currently lists image-generation models without Free Tier availability. Do not route image generation through Gemini while promising zero cost. Local diffusion/video models require model files, runtime integration, storage, memory and device benchmarks before they can be exposed as supported features.

## Safety, security and reliability constraints

- Never embed provider API keys or the Worker bearer token in the APK or source control.
- Do not rotate credentials to bypass provider quotas. Respect provider 429 responses.
- Do not label prompt ideas, static-photo MP4 exports, or conventional filters as AI video generation.
- Bengali speech model download is user-visible and happens on first use; internet is needed for that initial download. Transcription itself runs locally after the model is present.
- A successful CI build proves compilation/package creation, not successful installation or runtime behavior on the Redmi Note 15 4G. Real-device tests are still required.

## Verified external model documentation

- Bengali model: sherpa-onnx streaming Zipformer Bengali Vosk, released 2026-02-09.
- Model files documented by sherpa-onnx: encoder.onnx, decoder.onnx, joiner.onnx, tokens.txt and bpe.model.
- Model source and usage: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html

## Current truth

This file records the repository implementation, not an assertion that every feature is complete. The three AI generation engines above remain unavailable until a genuinely free and device-compatible implementation is verified.
