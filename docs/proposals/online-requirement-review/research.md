# 线上需求评审：开源调研与复用决策

日期：2026-10-21。状态：设计提案，未接入、未部署第三方项目。配套：[需求文档](requirements.md)、[技术方案](technical-design.md)、[核验记录](verification.md)。

## 1. 结论

GitHub 上存在相似项目，不能得出“没有开源实现”的结论。但本次核验的项目中，没有发现同时满足现有单实例 Vue + Spring MVC + MySQL 基线、项目成员权限、问题回应与确认解决、需求修订关联，并能作为完整模块直接接入的方案。

建议复用 **Recogito Text Annotator 的文本选区、高亮和批注定位能力**，在当前项目内新增人工评审业务。组件不能代替责任人、正式答复、解决确认、评审轮次及修订记录。该建议尚未通过兼容性实测，不代表依赖已经安装或模块已经实现。

这次按“没有可直接接入的完整方案”交付完整需求和技术设计；没有擅自部署一个独立文档系统，也没有复制受其他许可证约束的业务源码。

## 2. 检索方法与判定条件

通过 GitHub 连接器搜索 `document annotation comments review`、`docmost`，结合上游仓库 README、许可证、依赖、部署配置、评论代码与官方文档核验。后续沿旧 Recogito 仓库的迁移提示找到当前维护项目。补充核验 Hypothesis、ONLYOFFICE 和 Tiptap。

比较维度：选区批注、回复、作者与时间、已解决/待处理、文档历史、问题与修订关联、嵌入 Vue 的成本、是否必须新增服务/数据库、许可证和收费边界。README 未证明的能力标为未核实；页面展示、源码核查与实际部署验收分开记录。本次没有部署这些候选。

“直接复用完整模块”要求同时通过：

1. 不引入 Redis、消息队列、另一种数据库和额外业务服务。
2. 复用本地登录，服务端逐请求验证当前需求的成员权限。
3. 原始 Agent 结果不可变，人工讨论不进入模型输入。
4. 支持提出问题、负责回应、确认解决和修订关联，而不仅是文字聊天。
5. 许可证、依赖来源和冻结版本经过记录及回归。

## 3. 候选对比

| 候选 | 上游已核实能力 | 复用阻碍或缺口 | 本项目决策 |
| --- | --- | --- | --- |
| [Docmost](https://github.com/docmost/docmost) | README 明列评论、页面历史、权限和搜索；评论服务存在父评论与回复处理 | 是完整协作文档产品；官方 Compose 使用 PostgreSQL、Redis；核心为 AGPL-3.0，部分目录采用企业许可证；没有核实本提案所需的责任人答复和修订闭环 | 作为产品交互参考，不整套嵌入、不复制其业务源码 |
| [Hypothesis client](https://github.com/hypothesis/client) / [h](https://github.com/hypothesis/h) | 浏览器网页批注客户端、可嵌入网页、配套批注 API 服务；客户端为 BSD-2-Clause | 客户端面向其服务协议；h 开发 Compose 包含 PostgreSQL、Elasticsearch、RabbitMQ；正式部署最小依赖未实测；不是 PRD 修订及解决确认模块 | 可参考批注设计；不接入外部批注服务、不加载公共注入脚本 |
| [Recogito Text Annotator](https://github.com/recogito/text-annotator-js) | JavaScript 核心支持创建选区、叠加高亮、生命周期事件、滚动定位；数据含引用与位置；BSD-3-Clause | 提供批注基础能力，应用负责持久化与业务流程；Vue 生命周期、中文和复杂 Markdown 需实测 | 首选组件候选，以薄适配器复用核心，不引入 React 包 |
| [RecogitoJS 旧版](https://github.com/recogito/recogito-js) | 旧文本批注库 | 仓库已归档且 README 明确废弃并指向后继项目 | 排除，避免从旧教程引入停维护依赖 |
| [ONLYOFFICE DocumentServer](https://github.com/ONLYOFFICE/DocumentServer) | 官方文档介绍段落评论、版本和 Track Changes，支持查看及接受/拒绝修改 | 需要文档服务器与文件集成，现有 Markdown 工作流接入成本高；许可证含 AGPL 及附加条款，不能按 Apache-2.0 代码直接复制 | 如未来以 Office 文件为核心，可另立方案评估；本期不接入 |
| [Tiptap](https://github.com/ueberdosis/tiptap) | 开源编辑器核心；官方另外提供评论和历史能力 | 官方将 Comments、Version history 列为付费平台能力；不能从核心开源推断整套评审功能免费可用 | 不把付费模块当作可免费复用的开源模块；本期不更换编辑器 |

许可证信息仅记录上游声明；选择、修改或分发相应代码前仍应审查具体文件与依赖，不能把不同项目整体重标为本仓库的 Apache-2.0。

## 4. 可复核证据

以下分支内容在调研当日读取；为减少后续分支漂移，列出对应仓库最近提交。此处提交是源码核验点，不是本项目已锁定的生产依赖。

| 项目 | 核验提交 | 主要证据 |
| --- | --- | --- |
| Docmost | `ded9b7ca9f147e987c5998abf047b59c731d14ff` | [README](https://github.com/docmost/docmost/blob/ded9b7ca9f147e987c5998abf047b59c731d14ff/README.md)、[Compose](https://github.com/docmost/docmost/blob/ded9b7ca9f147e987c5998abf047b59c731d14ff/docker-compose.yml)、[评论服务](https://github.com/docmost/docmost/blob/ded9b7ca9f147e987c5998abf047b59c731d14ff/apps/server/src/core/comment/comment.service.ts) |
| Hypothesis client | `a7b7400d0d706d4357ec37c2a3610b25dcca89d5` | [README](https://github.com/hypothesis/client/blob/a7b7400d0d706d4357ec37c2a3610b25dcca89d5/README.md)、[LICENSE](https://github.com/hypothesis/client/blob/a7b7400d0d706d4357ec37c2a3610b25dcca89d5/LICENSE) |
| Hypothesis h | `18e30d6b1feda29f291140e29dbdedd33c45fb47` | [README](https://github.com/hypothesis/h/blob/18e30d6b1feda29f291140e29dbdedd33c45fb47/README.md)、[开发 Compose](https://github.com/hypothesis/h/blob/18e30d6b1feda29f291140e29dbdedd33c45fb47/docker-compose.yml) |
| Recogito Text Annotator | `0d375b0cba2bf68378f1f6dff30ee8812df95c0c` | [README/API](https://github.com/recogito/text-annotator-js/blob/0d375b0cba2bf68378f1f6dff30ee8812df95c0c/README.md)、[LICENSE](https://github.com/recogito/text-annotator-js/blob/0d375b0cba2bf68378f1f6dff30ee8812df95c0c/LICENSE)、[核心包清单](https://github.com/recogito/text-annotator-js/blob/0d375b0cba2bf68378f1f6dff30ee8812df95c0c/packages/text-annotator/package.json)，清单版本 `4.3.7` |

其他依据：[旧 Recogito 废弃提示](https://github.com/recogito/recogito-js/blob/main/README.md)、[ONLYOFFICE 审阅说明](https://helpcenter.onlyoffice.com/docs/userguides/document_editor/review.aspx)、[ONLYOFFICE 许可证](https://github.com/ONLYOFFICE/DocumentServer/blob/master/LICENSE)、[Tiptap 官方能力分类](https://tiptap.dev/docs)、[Tiptap 收费说明](https://tiptap.dev/pricing)。价格、上游能力与许可证后续可能变化，接入前重新核验。

## 5. 复用验证计划

先做组件验证，再实现业务；验证失败显式记录原因并重新选型，不能把未验证的兼容性写成结论。

| 验证项 | 通过条件 |
| --- | --- |
| Vue 集成 | 在现有 Vue/Vite 中按挂载/卸载创建和销毁实例；路由、版本切换后无重复监听和残留高亮 |
| 中文选区 | 中文、emoji、组合字符、重复文本、表格和跨行选区稳定；不支持的跨块选择显式提示 |
| 安全 | 仅绑定已净化的评审 Markdown；不绑定生成 HTML iframe；不使用公共 CDN 或第三方 API |
| 版本与定位 | 原始版本可还原；渲染指纹或文本不匹配时显示“无法定位”，不错误高亮 |
| 依赖 | 核验 registry 包、完整依赖树、锁文件与许可证，固定精确版本；更新第三方清单及允许发布文件清单 |
| 可用性 | 390/1366/1440/1920 宽度真实点击，键盘可操作；不适合选区的内容仍可创建整篇问题 |

本轮实际复用状态：**仅形成可复用组件选型与接入设计，没有导入代码、安装依赖或改变运行环境。**
