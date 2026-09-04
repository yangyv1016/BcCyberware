# 更新日志

本项目遵循[语义化版本](https://semver.org/lang/zh-CN/)。

## v0.0.7 - 2026-09-05

- 修复 AsPaper 等具有更严格插件类路径隔离的服务端上，直接引用
  `io.th0rgal.oraxen.api.OraxenPack` 导致的 `NoClassDefFoundError`；
- 改由服务器中已启用的 Oraxen 实例取得其类加载器，再解析并调用同一套公开 API；
- 动态注册 `OraxenPackGeneratedEvent` 与 `OraxenPackUploadEvent`，避免事件签名产生静态类链接；
- API 类或方法不兼容时输出一次明确版本诊断并停止重试，不再让 Bukkit 定时任务反复抛错；
- 新增真实 Oraxen API 解析、同路径资源替换和缺类降级测试。

## v0.0.6 - 2026-09-05

- 删除错误引入的 ResourcePackManager、SELFHOST 与 EXTERNAL 部署链路；
- 将 Oraxen 改为运行时硬依赖，通过官方 `OraxenPackGeneratedEvent` 把全部义体 `assets/`
  注入 Oraxen 的单一最终资源包；
- 生成完成后调用 `OraxenPack.reloadPack()`，并在 Oraxen 启动生成尚未结束时自动重试；
- 最终 ZIP、上传、SHA-1、强制加载、提示语与玩家进服下发全部由 Oraxen 唯一负责；
- 玩家资源包重发继续交由 Oraxen，BcCyberware 不依赖其内部非公开 sender；
- 新增注入内容筛选测试，确保仅传入 `assets/`，不会覆盖 Oraxen 的 `pack.mcmeta` 或图标。

## v0.0.5 - 2026-09-05

- 修正 ResourcePackManager 2.3.1 首次注册后的真实合并兼容性：注册后不再立刻调用其全量重载，避免新资源尚未暂存就被重载流程清空监视状态；
- 改由 ResourcePackManager 自带的资源变化监视器等待文件稳定后暂存、合并和下发；
- 已在 Paper 1.21.11 + ResourcePackManager 2.3.1 的真实服务端组合上核验最终合并 ZIP。

## v0.0.4 - 2026-09-05

- 修正资源包架构：移除 BcCyberware 逐个发送生成包和额外外部包的旧路径；
- 新增 `RESOURCE_PACK_MANAGER` 推荐部署模式，通过公开 API 注册本地成品，统一管理器负责与其他插件资源合并、托管并只发送最终包；
- 统一模式下 BcCyberware 不再监听下载端口、不处理进服发送，也不会手动下发第二个包；
- SELFHOST/EXTERNAL 独立模式改用 Paper/Adventure 的单包替换请求，并明确要求它们独占服务器资源包下发；
- 旧 `external-packs` 与 Pack 内 `resource-packs` 的非空配置现在会给出明确迁移错误，防止恢复多包下发；
- 新增 ResourcePackManager 相对路径和统一配置校验测试。

## v0.0.3 - 2026-09-04

- 固定示例资源包的 ZIP 平台元数据，使 Windows 与 GitHub Actions 生成完全相同的文件和 SHA-1；
- 交付说明改为引用 Release 同批生成的校验文件，避免文档写死某次本机构建哈希。

## v0.0.2 - 2026-09-04

- 采用 MythicMobs + MythicCrucible 风格的资源资产工作流；
- 每个内容 Pack 可携带独立 `Assets/`，并按 Pack 优先级合并；
- 支持 `Generation/merge/` 最终覆盖层和 `Generation/resource_pack.zip` 自动生成；
- 新增 `SELFHOST` 内置托管、玩家加入自动下发和生成后在线热推送；
- 支持显式 SHA-1 的 `EXTERNAL` 部署；生成与上传分离，避免向玩家提前推送未上传文件；
- SELFHOST 使用哈希不可变下载地址并只保留最近 4 代缓存；
- 默认核心 Pack 材质随插件释放，无需服主另外下载默认资源包；
- 新增 `/bccyberware resourcepack generate` 管理命令。

## v0.0.1 - 2026-09-04

首个可用版本：

- 支持 Paper 1.21.11 与 Java 21；
- 提供 10 个身体槽位，左右臂、左右腿分别独立；
- 内置 18 个原创原生器官/示例义体及配套资源包；
- 提供受控箱子 GUI，用于安装、替换和拆卸真实义体物品；
- 提供可配置容量、阈值效果、空槽位缺点、周期触发器、条件与动作；
- 支持固定值、权限、计分板、PlaceholderAPI 与 mcMMO 数值源；
- 支持命名空间、依赖、优先级和受控覆盖的模块化内容 Pack；
- 使用 SQLite 保存单服玩家数据；
- 所有默认 YAML 均附中文注释，并提供完整配置说明。
