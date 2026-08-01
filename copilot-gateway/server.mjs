import crypto from "node:crypto";
import express from "express";
import { CopilotClient } from "@github/copilot-sdk";

const app = express();
const port = Number.parseInt(process.env.PORT || "8787", 10);
const clients = new Map();
const clientIdleMs = 30 * 60 * 1000;

app.disable("x-powered-by");
app.use(express.json({ limit: "30mb" }));

function bearerToken(request) {
  const header = String(request.headers.authorization || "");
  const match = /^Bearer\s+(.+)$/i.exec(header);
  if (!match) {
    const error = new Error("缺少 GitHub OAuth Bearer 令牌");
    error.status = 401;
    throw error;
  }
  return match[1].trim();
}

function tokenKey(token) {
  return crypto.createHash("sha256").update(token).digest("hex");
}

async function clientFor(token) {
  const key = tokenKey(token);
  let entry = clients.get(key);
  if (!entry) {
    const client = new CopilotClient({
      gitHubToken: token,
      useLoggedInUser: false,
      mode: "empty",
      logLevel: "error",
    });
    entry = {
      client,
      started: client.start(),
      lastUsed: Date.now(),
    };
    clients.set(key, entry);
    try {
      await entry.started;
    } catch (error) {
      clients.delete(key);
      throw error;
    }
  } else {
    await entry.started;
  }
  entry.lastUsed = Date.now();
  return entry.client;
}

function contentText(content) {
  if (typeof content === "string") return content;
  if (!Array.isArray(content)) return "";
  return content
    .filter((item) => item && item.type === "text")
    .map((item) => String(item.text || ""))
    .join("\n");
}

function imageAttachments(messages) {
  const attachments = [];
  for (const message of messages) {
    if (!Array.isArray(message?.content)) continue;
    for (const item of message.content) {
      if (!item || item.type !== "image_url") continue;
      const value = typeof item.image_url === "string"
        ? item.image_url
        : item.image_url?.url;
      const match = /^data:(image\/[a-z0-9.+-]+);base64,(.+)$/is.exec(
        String(value || ""),
      );
      if (!match) continue;
      attachments.push({
        type: "blob",
        mimeType: match[1],
        data: match[2].replace(/\s+/g, ""),
      });
    }
  }
  return attachments;
}

function splitConversation(messages) {
  const system = [];
  const transcript = [];
  for (const message of messages) {
    const role = String(message?.role || "user");
    const text = contentText(message?.content).trim();
    const hasImage = Array.isArray(message?.content)
      && message.content.some((item) => item?.type === "image_url");
    const body = text || (hasImage ? "[图片]" : "");
    if (!body) continue;
    if (role === "system" || role === "developer") {
      system.push(body);
    } else {
      const label = role === "assistant" ? "助手" : "用户";
      transcript.push(`${label}：${body}`);
    }
  }
  return {
    system: system.join("\n\n") || "你是一个可靠的聊天助手。",
    prompt: transcript.join("\n\n"),
  };
}

function hasNativeSearchTool(body) {
  return Array.isArray(body?.tools) && body.tools.some((tool) => {
    const type = String(tool?.type || tool?.function?.name || "");
    return type.includes("web_search") || type.includes("google_search");
  });
}

app.get("/health", (_request, response) => {
  response.json({ ok: true, service: "chengyu-copilot-gateway" });
});

app.get("/v1/models", async (request, response, next) => {
  try {
    const client = await clientFor(bearerToken(request));
    const models = await client.listModels();
    response.json({
      object: "list",
      data: (Array.isArray(models) ? models : []).map((model) => ({
        id: model.id,
        object: "model",
        owned_by: "github-copilot",
        name: model.name || model.id,
      })),
    });
  } catch (error) {
    next(error);
  }
});

app.post("/v1/chat/completions", async (request, response, next) => {
  let session;
  try {
    // The Android client will automatically fall back to its own search when
    // this OpenAI-style native tool is unavailable in the Copilot SDK bridge.
    if (hasNativeSearchTool(request.body)) {
      const error = new Error("unsupported tool: web_search");
      error.status = 400;
      throw error;
    }
    const token = bearerToken(request);
    const client = await clientFor(token);
    const messages = Array.isArray(request.body?.messages)
      ? request.body.messages
      : [];
    const conversation = splitConversation(messages);
    if (!conversation.prompt) {
      const error = new Error("messages 中没有可发送的用户内容");
      error.status = 400;
      throw error;
    }
    session = await client.createSession({
      sessionId: `chengyu-${crypto.randomUUID()}`,
      model: String(request.body?.model || "gpt-5.4"),
      streaming: false,
      memory: { enabled: false },
      infiniteSessions: { enabled: false },
      availableTools: [],
      systemMessage: {
        mode: "replace",
        content: conversation.system,
      },
      onPermissionRequest: () => ({
        kind: "reject",
        feedback: "澄语聊天网关不允许执行服务器工具。",
      }),
    });
    const result = await session.sendAndWait({
      prompt: conversation.prompt,
      attachments: imageAttachments(messages),
    });
    const content = String(result?.data?.content || "").trim();
    if (!content) throw new Error("GitHub Copilot 没有返回文字内容");
    response.json({
      id: `chatcmpl-${crypto.randomUUID()}`,
      object: "chat.completion",
      created: Math.floor(Date.now() / 1000),
      model: String(request.body?.model || "gpt-5.4"),
      choices: [{
        index: 0,
        message: { role: "assistant", content },
        finish_reason: "stop",
      }],
    });
  } catch (error) {
    next(error);
  } finally {
    if (session) {
      try {
        await session.disconnect();
      } catch {
        // The response has already completed; cleanup errors are non-fatal.
      }
    }
  }
});

app.use((error, _request, response, _next) => {
  const status = Number.isInteger(error?.status) ? error.status : 502;
  const message = String(error?.message || "Copilot SDK 网关请求失败")
    .replace(/\s+/g, " ")
    .slice(0, 300);
  response.status(status).json({
    error: { message, type: "copilot_gateway_error" },
  });
});

setInterval(async () => {
  const now = Date.now();
  for (const [key, entry] of clients) {
    if (now - entry.lastUsed < clientIdleMs) continue;
    clients.delete(key);
    try {
      await entry.client.stop();
    } catch {
      // Keep idle cleanup best-effort.
    }
  }
}, 5 * 60 * 1000).unref();

app.listen(port, () => {
  console.log(`Chengyu Copilot gateway listening on :${port}`);
});
