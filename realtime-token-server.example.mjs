import crypto from "node:crypto";
import http from "node:http";

const apiKey = process.env.OPENAI_API_KEY;
const appToken = process.env.MIUTAVERN_TOKEN || "";
const port = Number(process.env.PORT || 8787);

if (!apiKey) {
  throw new Error("请先设置 OPENAI_API_KEY");
}

const server = http.createServer(async (request, response) => {
  if (request.method !== "GET" || request.url !== "/token") {
    response.writeHead(404, { "Content-Type": "application/json" });
    response.end(JSON.stringify({ error: "not_found" }));
    return;
  }

  const supplied = (request.headers.authorization || "").replace(/^Bearer\s+/i, "");
  if (appToken && supplied !== appToken) {
    response.writeHead(401, { "Content-Type": "application/json" });
    response.end(JSON.stringify({ error: "unauthorized" }));
    return;
  }

  const identity = supplied
    || request.headers["x-forwarded-for"]
    || request.socket.remoteAddress
    || "local-user";
  const safetyIdentifier = crypto
    .createHash("sha256")
    .update(String(identity))
    .digest("hex");

  try {
    const upstream = await fetch(
      "https://api.openai.com/v1/realtime/client_secrets",
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${apiKey}`,
          "Content-Type": "application/json",
          "OpenAI-Safety-Identifier": safetyIdentifier,
        },
        body: JSON.stringify({
          session: {
            type: "realtime",
            model: "gpt-realtime-2.1",
            audio: {
              output: {
                voice: "marin",
              },
            },
          },
        }),
      },
    );
    const body = await upstream.text();
    response.writeHead(upstream.status, {
      "Content-Type": upstream.headers.get("content-type") || "application/json",
      "Cache-Control": "no-store",
    });
    response.end(body);
  } catch (error) {
    response.writeHead(502, { "Content-Type": "application/json" });
    response.end(JSON.stringify({
      error: error instanceof Error ? error.message : "upstream_failed",
    }));
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`MiuTavern Realtime token server: http://0.0.0.0:${port}/token`);
});
