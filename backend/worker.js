const KEY_NAMES = [
  "GEMINI_API_KEY", "GEMINI_API_KEY_2", "GEMINI_API_KEY_3", "GEMINI_API_KEY_4",
  "GEMINI_API_KEY_5", "GEMINI_API_KEY_6", "GEMINI_API_KEY_7", "GEMINI_API_KEY_8",
  "GEMINI_API_KEY_9", "GEMINI_API_KEY_10", "GEMINI_API_KEY_11", "GEMINI_API_KEY_12",
  "GEMINI_API_KEY_13", "GEMINI_API_KEY_14", "GEMINI_API_KEY_15", "GEMINI_API_KEY_16",
  "GEMINI_API_KEY_17", "GEMINI_API_KEY_18", "GEMINI_API_KEY_19", "GEMINI_API_KEY_20",
  "GEMINI_API_KEY_21", "GEMINI_API_KEY_22", "GEMINI_API_KEY_23", "GEMINI_API_KEY_24"
];

const DEFAULT_MODEL = "gemini-2.5-flash";
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

    const model = typeof payload.model === "string" && /^[a-zA-Z0-9._-]{1,80}$/.test(payload.model)
      ? payload.model
      : DEFAULT_MODEL;
    delete payload.model;

    const projects = getConfiguredProjects(env);
    if (projects.length === 0) {
      return json({ error: "No Gemini project credentials are configured on the backend." }, 503, cors);
    }

    // Try configured independent projects in order only for transient network/5xx
    // service failures. A 429 is returned immediately; never rotate to evade quota.
    let lastStatus = 502;
    for (const project of projects) {
      let response;
      try {
        response = await callGemini(project, model, payload);
      } catch {
        lastStatus = 502;
        continue;
      }

      if (response.ok) {
        const body = await response.text();
        return new Response(body, {
          status: response.status,
          headers: { "content-type": "application/json; charset=utf-8", ...cors }
        });
      }

      lastStatus = response.status;
      if (response.status === 429) {
        const retryAfter = response.headers.get("retry-after");
        return json({
          error: safeErrorMessage(429),
          status: 429,
          retryAfterSeconds: retryAfter ? Number(retryAfter) || null : null,
          project: project.index
        }, 429, {
          ...cors,
          ...(retryAfter ? { "retry-after": retryAfter } : {})
        });
      }

      if ([400, 401, 403, 404].includes(response.status)) {
        return json({
          error: safeErrorMessage(response.status),
          status: response.status,
          project: project.index
        }, response.status, cors);
      }

      if (response.status < 500) {
        return json({ error: safeErrorMessage(response.status), status: response.status }, response.status, cors);
      }
    }

    return json({
      error: safeErrorMessage(lastStatus),
      status: lastStatus,
      triedProjects: projects.length
    }, 503, cors);
  }
};
