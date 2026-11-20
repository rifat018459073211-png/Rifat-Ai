const KEY_NAMES = [
  "GEMINI_API_KEY", "GEMINI_API_KEY_2", "GEMINI_API_KEY_3", "GEMINI_API_KEY_4",
  "GEMINI_API_KEY_5", "GEMINI_API_KEY_6", "GEMINI_API_KEY_7", "GEMINI_API_KEY_8",
  "GEMINI_API_KEY_9", "GEMINI_API_KEY_10", "GEMINI_API_KEY_11", "GEMINI_API_KEY_12",
  "GEMINI_API_KEY_13", "GEMINI_API_KEY_14", "GEMINI_API_KEY_15", "GEMINI_API_KEY_16",
  "GEMINI_API_KEY_17", "GEMINI_API_KEY_18", "GEMINI_API_KEY_19", "GEMINI_API_KEY_20",
  "GEMINI_API_KEY_21", "GEMINI_API_KEY_22", "GEMINI_API_KEY_23", "GEMINI_API_KEY_24"
];

// Requested priority: model slot #4 first, then slot #1, followed by verified fallbacks.
const MODEL_FALLBACKS = [
  "gemini-3.5-flash-lite",
  "gemini-3.5-flash",
  "gemini-3.8-flash",
  "gemini-3.6-flash",
  "gemini-3.1-flash-lite"
];
const DEFAULT_MODEL = MODEL_FALLBACKS[0];
const MAX_BODY_BYTES = 1024 * 1024;

function json(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...extraHeaders }
  });
}

function getConfiguredProjects(env) {
  return KEY_NAMES
    .map((name, index) => ({ name, index: index + 1, key: env[name] }))
    .filter((entry) => typeof entry.key === "string" && entry.key.trim().length > 0);
}

function providerErrorMessage(response) {
  return response.clone().text().then((raw) => {
    try {
      const parsed = JSON.parse(raw);
      const message = parsed && parsed.error && typeof parsed.error.message === "string"
        ? parsed.error.message.trim()
        : "";
      return message.slice(0, 300);
    } catch {
      return "";
    }
  }).catch(() => "");
}

function safeErrorMessage(status) {
  if (status === 400) return "Gemini rejected the request. Check the request body.";
  if (status === 401 || status === 403) return "A configured project credential is invalid or lacks permission.";
  if (status === 404) return "The requested Gemini model or endpoint was not found.";
  if (status === 429) return "Gemini quota or rate limit reached. Retry after the provider reset window.";
  return "Gemini service is temporarily unavailable.";
}

async function callGemini(project, model, payload) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`;
  return fetch(url, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-goog-api-key": project.key
    },
    body: JSON.stringify(payload)
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "access-control-allow-origin": "*",
          "access-control-allow-methods": "GET, POST, OPTIONS",
          "access-control-allow-headers": "content-type, authorization",
          "access-control-max-age": "86400"
        }
      });
    }

    const cors = { "access-control-allow-origin": "*" };

    if (url.pathname === "/health" && request.method === "GET") {
      const projects = getConfiguredProjects(env);
      return json({
        ok: true,
        service: "Rifat-Ai Cloudflare Worker",
        configuredProjects: projects.length,
        configuredProjectIndexes: projects.map((entry) => entry.index).join(", "),
        appAccessTokenConfigured: typeof env.APP_ACCESS_TOKEN === "string" && env.APP_ACCESS_TOKEN.length > 0,
        model: DEFAULT_MODEL,
        modelFallbacks: MODEL_FALLBACKS,
        maxRequestBytes: MAX_BODY_BYTES,
        cloudflareUsageAvailable: false,
        note: "Secret values and credential names are never returned. Cloudflare plan usage is not exposed by this health endpoint."
      }, 200, cors);
    }

    if (url.pathname !== "/v1/generate" || request.method !== "POST") {
      return json({ error: "Not found" }, 404, cors);
    }

    if (!env.APP_ACCESS_TOKEN) {
      return json({ error: "Backend access token is not configured." }, 503, cors);
    }
    const authorization = request.headers.get("authorization") || "";
    if (authorization !== `Bearer ${env.APP_ACCESS_TOKEN}`) {
      return json({ error: "Unauthorized." }, 401, cors);
    }

    const contentLength = Number(request.headers.get("content-length") || 0);
    if (contentLength > MAX_BODY_BYTES) {
      return json({ error: "Request body is too large." }, 413, cors);
    }

    let payload;
    try {
      const raw = await request.text();
      if (new TextEncoder().encode(raw).length > MAX_BODY_BYTES) {
        return json({ error: "Request body is too large." }, 413, cors);
      }
      payload = JSON.parse(raw);
    } catch {
      return json({ error: "A valid JSON request body is required." }, 400, cors);
    }

    if (!payload || typeof payload !== "object" || !Array.isArray(payload.contents)) {
      return json({ error: "Request must include a contents array." }, 400, cors);
    }

    // The model order is fixed server-side so the preferred model is always tried first.
    // Never rotate models or credentials to bypass a 429 quota response.
    delete payload.model;

    const projects = getConfiguredProjects(env);
    if (projects.length === 0) {
      return json({ error: "No Gemini project credentials are configured on the backend." }, 503, cors);
    }

    let lastStatus = 502;
    let lastProject = null;
    let lastProviderMessage = "";

    for (const model of MODEL_FALLBACKS) {
      let modelNotFound = false;
      let authFailures = 0;

      for (const project of projects) {
        let response;
        try {
          response = await callGemini(project, model, payload);
        } catch {
          lastStatus = 502;
          lastProject = project.index;
          continue;
        }

        if (response.ok) {
          const body = await response.text();
          return new Response(body, {
            status: response.status,
            headers: {
              "content-type": "application/json; charset=utf-8",
              ...cors,
              "x-jarvis-gemini-model": model
            }
          });
        }

        lastStatus = response.status;
        lastProject = project.index;

        if (response.status === 429) {
          const retryAfter = response.headers.get("retry-after");
          return json({
            error: safeErrorMessage(429),
            status: 429,
            model,
            retryAfterSeconds: retryAfter ? Number(retryAfter) || null : null,
            project: project.index
          }, 429, {
            ...cors,
            ...(retryAfter ? { "retry-after": retryAfter } : {})
          });
        }

        if (response.status === 404) {
          lastProviderMessage = await providerErrorMessage(response);
          modelNotFound = true;
          break;
        }

        if (response.status === 401 || response.status === 403) {
          authFailures++;
          lastProviderMessage = await providerErrorMessage(response);
          continue;
        }

        if (response.status === 400) {
          const providerMessage = await providerErrorMessage(response);
          return json({
            error: safeErrorMessage(400),
            status: 400,
            model,
            project: project.index,
            ...(providerMessage ? { providerMessage } : {})
          }, 400, cors);
        }

        if (response.status < 500) {
          const providerMessage = await providerErrorMessage(response);
          return json({
            error: safeErrorMessage(response.status),
            status: response.status,
            model,
            project: project.index,
            ...(providerMessage ? { providerMessage } : {})
          }, response.status, cors);
        }

        lastProviderMessage = await providerErrorMessage(response);
      }

      if (modelNotFound) continue;

      if (authFailures === projects.length) {
        return json({
          error: safeErrorMessage(lastStatus),
          status: lastStatus,
          model,
          project: lastProject,
          ...(lastProviderMessage ? { providerMessage: lastProviderMessage } : {})
        }, lastStatus, cors);
      }

      // Network/5xx failure across configured projects: try the next model.
    }

    return json({
      error: safeErrorMessage(lastStatus),
      status: lastStatus,
      modelFallbacksTried: MODEL_FALLBACKS,
      project: lastProject,
      ...(lastProviderMessage ? { providerMessage: lastProviderMessage } : {})
    }, 503, cors);
  }
};
