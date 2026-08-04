# KiraChat Sync Server

澄语客户端使用的轻量自部署同步服务。服务器只保存经过客户端 AES-GCM 加密的快照，并通过单调递增的修订号阻止旧设备静默覆盖新数据。

## 启动

需要 Node.js 20.12 或更高版本：

```bash
cd sync-server
export SYNC_TOKENS="请替换为至少24字符的随机同步令牌"
export SYNC_DATA_DIR="./data"
npm start
```

生产环境应通过 Caddy、Nginx 或其他反向代理提供 HTTPS。多个用户可在 `SYNC_TOKENS` 中用英文逗号分隔令牌；每个令牌对应独立快照文件。客户端还会要求单独的加密密码，该密码不会发送给服务器。

接口：`GET /v1/health`、`GET /v1/sync/meta`、`GET /v1/sync/snapshot`、`PUT /v1/sync/snapshot`。
