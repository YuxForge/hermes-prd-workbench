# Hermes PRD Workbench

[English](README.md) · [快速开始](#快速开始) · [文档](docs/README.md) · [交流讨论](https://github.com/YuxForge/hermes-prd-workbench/discussions)

[![采购审批旗舰案例：观看 60 秒成果回放](docs/examples/procurement/overview.jpg)](docs/examples/procurement/walkthrough.mp4)

[观看 60 秒成果回放](docs/examples/procurement/walkthrough.mp4) · [下载完整交付包](docs/examples/procurement/deliverables.zip) · [查看案例与验证记录](docs/examples/procurement/README.md)

0.4.0-beta.2 开源预览版。仅支持全新安装；已验收 macOS arm64 和 Windows 11 WSL2 + Docker Desktop Linux 容器，Linux 原生与原生 Windows 尚未声明支持。

可自托管的 PRD 工作台，以 [Hermes Agent](https://github.com/NousResearch/hermes-agent) 为推理核心。支持材料上传、PRD 生成与编辑、评审、HTML Demo、静态 POC、工作量估算、测试用例、技术方案与导出。

面向本地或可信网络中的单实例使用。此项目独立于 Hermes 上游；不提供公共 SaaS、多租户、高可用或自动恢复承诺。模型输出与工作量估算需要人工判断。

## 一个具体案例：采购额度冲突，研发应该按哪版做？

会议纪要说「不超过 5,000 元，主管批准即可」，补充规则却写「小于 10,000 元，无需财务复核」。**同一笔 8,600 元工作站申请，在两份材料下会走不同审批路径。**

我们用完全合成的中文材料，在本地真实启动的工作台中完成了这条链路：材料评审发现 8 项关键问题 → 维护者代表虚构业务方明确决定 → 生成四模块 PRD 并独立保存人工当前稿 → 生成可点击原型、测试用例、技术方案和工作量估算 → 导出交付包。

包内可以直接检查 PRD 文档包、原始评审与处理记录、HTML Demo、测试用例 XLSX、技术方案和估算 XLSX。[完整案例](docs/examples/procurement/README.md)同时列出待确认问题和实际验证范围，便于自己判断成果是否有用。视频是约 60 秒的真实浏览器操作剪辑，不代表模型只需 60 秒；所有业务数据虚构，静态原型不等于可上线的采购系统。

## 快速开始

需要 Docker Engine/Docker Desktop、Compose v2，以及 Python 3.10+ 用于启动脚本。Windows 请在 WSL2 的 Linux 文件系统中执行 setup 和所有命令，并启用 Docker Desktop 的 WSL2 集成；原生 Windows setup 不受支持。

```bash
git clone https://github.com/YuxForge/hermes-prd-workbench.git
cd hermes-prd-workbench
./workbench setup
./workbench start
./workbench doctor
```

setup 会询问支持 Chat Completions 和工具调用的模型服务地址、模型名、模型 Key 和初始管理员密码。敏感输入不回显；配置放 `.env`，密钥放 `.runtime/secrets`，均不进入 Git。启动后访问 **[http://127.0.0.1:8180](http://127.0.0.1:8180)**，使用显式设置的管理员账号登录；普通注册账号始终为 USER。

首次构建需要下载固定版本的运行时和依赖。MySQL 8.4.11 从固定摘要的官方多架构镜像构建，仅移除未使用的管理工具并以数据库用户启动；平台实际验证状态见[调整报告](docs/maintainers/change-report.md)，不能将提供 Dockerfile 等同于所有平台已验证。

停止：`./workbench stop`。此命令保留数据库、上传和 Hermes 卷。详见[部署与恢复](docs/deployment/self-hosting.md)。不需要 Nacos、Redis、MinIO 或消息队列。

## 当前能力

| 模式 | 产物 |
| --- | --- |
| REVIEW | 评审结论、问题与建议，可记录处理决定 |
| GENERATE | 多文件 PRD 包与可编辑当前稿 |
| DEMO | 自包含单文件 HTML，可选安全文本附件 |
| POC | 静态 HTML/CSS/JS 演示包与 ZIP |
| ESTIMATE | 基于材料和显式假设的工作量估算与 XLSX |
| TEST_CASE | 多文件测试用例与 XLSX |
| TECH_SPEC | 架构、数据模型、API 文档包与 Markdown |

支持中文文件名的 MD、TXT、DOCX、XLSX，以及粘贴文本上传。Hermes 原始结果保持只读；人工编辑的当前稿与评审处理决定独立保存，供后续运行使用。任务状态只有 PENDING/RUNNING/SUCCEEDED/FAILED，失败后手动重跑。

Demo 默认 `generic-admin-html`，没有公司网关、品牌模板或私有宿主导入协议。生成代码只做静态/语法诊断，不执行生成的 JavaScript、不证明所有交互正确。页面和新标签均通过无 `allow-same-origin` 的 iframe 隔离；下载的原始 HTML 是独立产物。

浏览器行为采集默认关闭，没有第三方分析服务。材料会按用户设置发送给模型服务，模型提供方的数据政策由部署者确认。详见[隐私与数据](docs/privacy.md)。

## 仓库结构

```text
apps/backend/       Spring MVC 模块化单体，唯一 Flyway 迁移来源
apps/web/           Vue 3 工作台
integrations/       独立 Hermes Profile 与 22 个插件工具
contracts/          静态 OpenAPI 与七类 JSON Schema
config/             冻结运行版本清单
deploy/             Docker 镜像、Nginx 与数据库初始化
scripts/            生命周期、原生开发、检查和发行工具
tests/              合成样例、安全和部署回归
docs/               当前产品、架构、运维、开发与验收说明
```

## 开发与维护

- [产品边界](docs/product.md)、[架构与数据](docs/architecture/overview.md)、[API 与结果](docs/architecture/contracts.md)
- [本地开发](docs/development/getting-started.md)、[测试清单](docs/testing/test-cases.md)
- [贡献指南](CONTRIBUTING.md)、[安全报告](SECURITY.md)、[发布门禁](docs/maintainers/releasing.md)

Java 21、Maven 3.9.16、Node 22.23.1、pnpm 11.9.0、Python 3.11.15、Hermes 0.16.0 和 MySQL 8.4.11 保持固定。依赖安全修复单独记录，不升级核心架构来绕过验收。

首次公开版本仅支持全新数据库安装。内部旧迁移链与公开版本 checksum 不同，**不得把本分支直接部署到旧数据库，不得执行 Flyway repair 掩盖差异**。迁移已有数据须另行备份、评审与验证。

## 许可证

项目采用 [Apache-2.0](LICENSE)。第三方组件保留原许可证，Hermes 是 MIT；详见 [NOTICE](NOTICE) 与[依赖来源](docs/maintainers/third-party.md)。作者已确认个人开发代码、Prompt 和资产的发布权利；首次发行从审核后的干净源码包建立新仓库，不包含内部历史、运行材料或预构建镜像。依赖安全与许可核对见[发行决策](docs/maintainers/security-release-decision.md)。
