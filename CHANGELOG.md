# 更新日志

本项目遵循[语义化版本](https://semver.org/lang/zh-CN/)。

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
