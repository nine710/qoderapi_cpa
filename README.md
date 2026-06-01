# Qoder Gateway

OpenAI 兼容的 Qoder API 网关，将 Qoder 私有 API 转换为标准 `/v1/chat/completions` 接口。

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- Docker（可选）

### 构建

```bash
mvn -DskipTests package
```

### 运行

```bash
QODER_PAT=<你的个人令牌> java -jar target/qoder-gateway-0.1.0.jar
```

默认监听 `0.0.0.0:8888`，可通过环境变量修改：

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `QODER_PAT` | (必填) | Qoder 个人访问令牌 |
| `APP_HOST` | `0.0.0.0` | 监听地址 |
| `APP_PORT` | `8888` | 监听端口 |
| `QODER_UPSTREAM_TIMEOUT_SECONDS` | `60` | 上游超时秒数 |

### Docker

```bash
QODER_PAT=<你的个人令牌> docker compose up --build -d
```

## API

### 聊天补全

```
POST /v1/chat/completions
```

支持流式 (`stream: true`) 和非流式两种模式，兼容 OpenAI Chat Completions 请求格式。

**非流式请求：**

```bash
curl http://localhost:8888/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"lite","messages":[{"role":"user","content":"hello"}]}'
```

**流式请求：**

```bash
curl http://localhost:8888/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"lite","messages":[{"role":"user","content":"hello"}],"stream":true}'
```

**Tool Call：**

```bash
curl http://localhost:8888/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model":"lite",
    "messages":[{"role":"user","content":"2+3等于多少?"}],
    "tools":[{"type":"function","function":{"name":"calculator","parameters":{"type":"object","properties":{"expr":{"type":"string"}}}}}]
  }'
```

### 健康检查

```
GET /health
```

## 可用模型

| 模型名 | 说明 |
|--------|------|
| `lite` | 轻量模型 |
| `performance` | 高性能模型 |
| `auto` | 自动选择 |

## 项目结构

```
├── api/                        # HTTP 接口层
│   ├── ChatController.java     # /v1/chat/completions
│   ├── ChatResponseWriter.java # SSE/JSON 响应写入
│   ├── HealthController.java   # /health
│   └── ApiExceptionHandler.java
├── application/                # 应用编排层
│   ├── ChatGatewayService.java # 核心编排（流式/非流式）
│   └── SessionFacade.java      # 会话管理
├── infra/                      # 基础设施层
│   ├── http/
│   │   └── HttpClientFactory.java
│   └── qoder/
│       ├── BootstrapHttpClient.java  # 引导鉴权（jobToken + heartbeat）
│       ├── SessionTokenFactory.java  # Bearer 会话构造（RSA/AES 加密）
│       ├── SignatureSupport.java     # 请求签名
│       ├── SignedGatewayClient.java  # 上游 SSE 客户端
│       └── SseLineReader.java
├── protocol/                   # 协议转换层
│   ├── openai/
│   │   └── ChatRequestNormalizer.java
│   └── qoder/
│       ├── PayloadCodec.java         # 自定义编码
│       ├── StreamEventTranslator.java # 上游事件 → OpenAI delta
│       └── UpstreamPayloadAssembler.java # 消息格式转换
└── support/                    # 支撑模块
    ├── config/
    │   └── AppProperties.java
    └── model/
        ├── OpenAiChatRequest.java
        ├── NormalizedChatRequest.java
        ├── QoderSession.java
        ├── SessionBootstrap.java
        └── StreamDelta.java
```

## 架构概览

```
客户端 (OpenAI SDK / curl)
        │
        ▼
  /v1/chat/completions          ← OpenAI 兼容接口
        │
        ▼
  ChatGatewayService            ← 流式/非流式编排
   ├── UpstreamPayloadAssembler ← OpenAI → Qoder 消息转换
   ├── StreamEventTranslator    ← Qoder → OpenAI delta 转换
   └── ChatResponseWriter       ← SSE/JSON 响应输出
        │
        ▼
  SignedGatewayClient           ← Bearer 签名 + SSE 连接
        │
        ▼
  Qoder 上游 API                ← api3.qoder.sh
```

鉴权采用两阶段模型：

1. **引导阶段** — 用 `QODER_PAT` 换取 jobToken，建立机器身份
2. **会话阶段** — 基于 RSA/AES 加密构造 Bearer 令牌，对所有上游请求签名
