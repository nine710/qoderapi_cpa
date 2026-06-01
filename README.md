# Qoder 逆向反代到智能体教程

本项目通过自研逆向工程，完成对 Qoder 的协议接入、鉴权逆向与接口暴露，再经由 CPA 反向代理接入到 CCSwitch 智能体系统。

整体链路：

```text
CCSwitch -> CPA -> qoder-gateway -> Qoder
```

开始之前，先获取 CPA：

```bash
git clone https://github.com/router-for-me/Cli-Proxy-API-Management-Center.git
```

## 第一步：获取 Qoder Key

前往 Qoder 官网的集成页面获取你的个人访问令牌：

- https://qoder.com/account/integrations

拿到 Key 后，配置到本项目中：

```env
QODER_PAT=xxxxx
```

## 第二步：构建并启动 qoder-gateway

本仓库根目录即为自研逆向项目 `qoder-gateway`（Spring Boot 3.3 / Java 17），将 Qoder 私有协议转换为 OpenAI 兼容接口。

**2.1 配置环境变量**

在项目根目录创建 `.env` 文件，写入你的 Qoder Key：

```env
QODER_PAT=xxxxx
```

**2.2 启动**

```bash
docker compose up --build -d
```

**2.3 验证**

```bash
curl http://你的IP:8888/health
# 返回 {"status":"ok"}
```

```bash
curl http://你的IP:8888/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"lite","messages":[{"role":"user","content":"hello"}]}'
```

默认监听 `0.0.0.0:8888`，可通过环境变量修改：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `QODER_PAT` | (必填) | Qoder 个人访问令牌 |
| `APP_HOST` | `0.0.0.0` | 监听地址 |
| `APP_PORT` | `8888` | 监听端口 |

## 第三步：配置并启动 CPA

CPA（Cli-Proxy-API-Management-Center）负责代理、管理 qoder-gateway 暴露的接口。

**3.1 配置 CPA**

在 CPA 的 `config.yaml` 中填入 qoder-gateway 的地址和模型信息：

```yaml
  - name: "qoder-gateway"
    disabled: false
    prefix: "qoder"
    disable-cooling: true
    base-url: "http://你的IP:8888/v1"

    api-key-entries:
      - api-key: "dummy"
        proxy-url: "direct"

    models:
      - name: "Qwen3.7-Max"
        alias: "qwen3.5-plus"       # 根据智能体调整：Claude Code 用 qwen3.5-plus，Codex 用 gpt-5.4

      - name: "lite"
        alias: "lite"

      - name: "performance"
        alias: "performance"

      - name: "auto"
        alias: "auto"
```

关键配置项：

- `base-url`：改成你的 qoder-gateway 地址和端口
- `prefix`：路由前缀，按你的命名习惯调整
- `models`：按实际需要暴露的模型配置

> **关于模型映射：** Qwen3.7-Max 在 CPA 上的 alias 必须映射为各智能体兼容的模型名，否则无法调用。例如 Claude Code 上应映射为 `qwen3.5-plus`，Codex 上应映射为 `gpt-5.4`，具体根据你的智能体所支持的模型名称来设置。

**3.2 启动 CPA**

具体启动方式以 CPA 项目自身文档为准。

## 第四步：接入 CCSwitch

把 CPA 暴露出来的地址和端口配置到 CCSwitch，也就是我们自己的智能体接入侧。

你需要在 CCSwitch 中填写：

- CPA 的访问 IP
- CPA 的监听端口
- 对应的接口 URL
- 需要对接的模型或路由配置

## 验证顺序

1. `curl http://IP:8888/health` — qoder-gateway 是否正常
2. `curl http://IP:8888/v1/chat/completions ...` — 接口是否可访问
3. CPA 是否正常启动并能转发请求
4. CCSwitch 是否已正确配置 CPA 的地址

## 项目说明

本仓库根目录是自研逆向网关 `qoder-gateway`（Spring Boot 3.3 / Java 17 / Maven），结构如下：

```
├── api/                        # HTTP 接口层（/v1/chat/completions、/health）
├── application/                # 核心编排层（流式/非流式、会话管理）
├── infra/                      # 基础设施层（鉴权、签名、SSE 客户端）
├── protocol/                   # 协议转换层（编码、消息转换、SSE 翻译）
├── support/                    # 配置与模型
├── pom.xml
├── Dockerfile
└── docker-compose.yaml
```

## 免责声明

- 请自行确认你对目标平台和账号的使用权限
- 请遵守相关平台的服务条款和适用法律
- `Cli-Proxy-API-Management-Center` 版权归原作者所有（MIT License）
