# Qoder 逆向反代到智能体教程

本项目是一个教程型仓库，目标是通过两个外部项目完成对 Qoder 的逆向接入与反向代理，再把代理后的能力接到我们自己的智能体系统中。

教程主线分为两部分：

- 使用 `qoder2api` 逆向并暴露 Qoder 的 OpenAI 兼容接口
- 使用 `Cli-Proxy-API-Management-Center`（CPA）做映射、代理和管理

> 说明：本仓库本身不包含 `qoder2api` 源码。教程中会要求你自行从原仓库获取并运行它。

## 准备项目

第一步，先获取两个外部项目：

```bash
git clone https://github.com/cubk1/qoder2api.git
git clone https://github.com/router-for-me/Cli-Proxy-API-Management-Center.git
```

建议目录结构如下：

```text
workspace/
├─ qoder2api/
└─ Cli-Proxy-API-Management-Center/
```

## 获取 Qoder Key

第二步，前往 Qoder 官网的集成页面获取你自己的访问 Key：

- https://qoder.com/account/integrations

后续会把这个 Key 配置到 `qoder2api` 中，变量名为：

```env
QODER_PAT=xxxxx
```

## 配置并启动 qoder2api

第三步，在 `qoder2api` 项目目录中创建 `.env` 文件，并写入你的 Qoder Key：

```env
QODER_PAT=xxxxx
```

第四步，在 `qoder2api` 项目目录启动服务：

```bash
docker compose up -d
```

启动完成后，它会暴露一个 OpenAI 兼容接口。你需要把它映射到你自己的主机端口，例如：

```text
http://你的IP:xxxx/v1/chat/completions
```

其中：

- `xxxx` 是你对外暴露的宿主机端口
- 容器内部对应的是 `8963`
- 完整目标路径为 `/v1/chat/completions`

如果你本机直接映射 8963 端口，那么接口通常就是：

```text
http://你的IP:8963/v1/chat/completions
```

## 配置并启动 CPA

第五步，进入 `Cli-Proxy-API-Management-Center` 项目，打开它的 `config.yaml`，把上一步得到的 Qoder OpenAI 兼容接口配置进去，完成映射。

本仓库根目录下的 `config.txt` 提供了一个可参考的 `config.yaml` 片段示例，内容如下：

```yaml
  - name: "qoder2api"
    disabled: false
    prefix: "qoder"
    disable-cooling: true
    base-url: "http:xxxx:8963/v1"

    api-key-entries:
      - api-key: "dummy"
        proxy-url: "direct"

    models:
      - name: "Qwen3.7-Max"
        alias: "qwen3.5-plus"

      - name: "lite"
        alias: "lite"

      - name: "performance"
        alias: "performance"

      - name: "auto"
        alias: "auto"
```

你至少需要按自己的实际环境修改：

- `base-url`：改成你自己的 qoder2api 地址
- `prefix`：按你的路由命名习惯调整
- `models`：按你实际需要暴露给 CPA 的模型名和别名调整

配置时，你需要重点处理：

- 代理目标地址
- 上游接口映射关系
- 监听端口
- 访问地址
- 你自己的转发规则或模型路由规则

完成 `config.yaml` 配置后，启动 CPA。

> 这里的具体启动方式请以 CPA 项目自身文档为准，因为不同版本的启动方式可能会变化。

## 接入 CCSwitch

最后一步，把 CPA 暴露出来的端口、IP、URL 配置到 CCSwitch，也就是我们自己的智能体接入侧。

你最终需要在 CCSwitch 中填写的是：

- CPA 的访问 IP
- CPA 的监听端口
- 对应的接口 URL
- 需要对接的模型或路由配置

这样整条链路就会变成：

```text
CCSwitch -> CPA -> qoder2api -> Qoder
```

## 推荐验证顺序

建议按下面顺序检查：

1. `qoder2api` 是否已成功启动
2. `http://你的IP:xxxx/v1/chat/completions` 是否可访问
3. CPA 的 `config.yaml` 是否已正确指向 qoder2api
4. CPA 是否正常启动并能转发请求
5. CCSwitch 是否已正确配置 CPA 的地址

## 项目定位

本仓库是教程与集成说明仓库，不是 `qoder2api` 的再发布仓库。

- `qoder2api`：用于逆向和接口验证
- `Cli-Proxy-API-Management-Center`：用于代理和管理
- 本仓库：用于整理教程、流程和接入方法

## 免责声明

- 请自行确认你对目标平台和账号的使用权限。
- 请遵守相关平台的服务条款、许可证和适用法律。
- `qoder2api` 与 `Cli-Proxy-API-Management-Center` 的版权归各自原作者或原项目所有。
