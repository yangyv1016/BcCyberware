# BcCyberware

面向 Paper 1.21.11 / Java 21 的纯服务端义体系统。玩家通过箱子 GUI 安装、替换或拆除真实义体物品；槽位、容量、外部数值源、空部位缺点、触发条件、动作和资源包均由带中文注释的 YAML 配置驱动。

## 当前 MVP 能力

- `PAPER + PDC + item_model/CustomModelData` 的真实义体物品；
- 玩家专属但可交易、可移植的原生器官；
- 可配置身体槽位和默认 GUI 位置；
- 左/右臂与左/右腿是四个严格独立槽位，每种义体类型只对应一个位置；
- 类 MythicMobs 的模块化 Pack：命名空间、依赖、软依赖、优先级和受控覆盖；
- 受控菜单安装、替换、拆卸和分页选择；
- SQLite 单服持久化，已安装部件不参与死亡掉落；
- 可关闭的容量系统，支持固定值、玩家永久值、权限、计分板、mcMMO 与 PlaceholderAPI 数值源；
- 件数、已用容量、容量百分比三类周期阈值；
- 通用触发器、条件和动作；
- 类 MythicCrucible 的 Pack Assets 合并与资源包生成；
- 通过 Oraxen 官方生成事件把全部义体资源注入其最终包，由 Oraxen 唯一负责上传和下发；
- 原创默认器官材质与示例义体资源包。

## 构建

```powershell
.\gradlew.bat clean test build
```

产物位于 `build/libs/BcCyberware-0.0.8.jar`。首次启动后，默认配置和核心 Pack 的 Assets 会释放到 `plugins/BcCyberware/`；插件不会在读取或重载时回写 YAML，因此服主注释不会被清除。

## 资源包部署

Oraxen 是可选依赖：没有安装或启用失败时，义体系统仍可运行；客户端没有加载资源包时显示原版纸张。
安装 Oraxen 后，义体资源放在
`plugins/BcCyberware/packs/<Pack ID>/Assets/`，BcCyberware 会先按内容 Pack 优先级合并，
然后在 `OraxenPackGeneratedEvent` 中把 `assets/` 文件加入 Oraxen 的输出列表，并调用
`OraxenPack.reloadPack()` 触发最终构建。BcCyberware 不监听 HTTP 端口、不配置下载直链、
也不调用 Paper API 另发一份包；最终 ZIP、上传、SHA-1、提示语、强制加载和玩家进服发送
均使用 `plugins/Oraxen/settings.yml` 中 Oraxen 自己的配置。

为兼容 AsPaper 等严格隔离插件类路径的服务端，BcCyberware 会从已启用的 Oraxen 实例取得
Oraxen 自己的类加载器，再调用上述公开 API；不会把 Oraxen 类复制或打包进本插件 JAR。

默认器官材质也走同一注入链路。生成后的
`plugins/BcCyberware/Generation/resource_pack.zip` 只是 BcCyberware 的中间产物，
不用复制到 Oraxen 目录。发生同路径冲突时，BcCyberware 注入的文件覆盖 Oraxen 输出列表中
已经存在的同路径文件；建议继续使用独立的 `bccyberware` 命名空间避免冲突。

v0.0.8 起，物品使用客户端内置的 `minecraft:paper` 模型，通过 CustomModelData 的首个
字符串选择自定义外观。没有资源包、拒绝加载或下载失败时，自然回退为纸张，不会因为引用
不存在的自定义 item_model 而出现紫黑错误模型；名称、说明、身份与义体功能不受影响。
生成器自动构造 `assets/minecraft/items/paper.json`；注入时保留 Oraxen 原有纸张模型作为
fallback，因此不会覆盖其原有的数字 CustomModelData 分派规则。不需要维护第二份资源包。

升级时停服替换主 JAR，保留配置和数据库。旧背包/末影箱物品在玩家登录时、容器中的旧物品在
打开时、已安装部件在档案加载时迁移外观，保留原始 UUID、主人和其他元数据。
若关闭了 `generate-on-startup`，升级后请执行一次 `/bccyberware resourcepack generate`，
让 Oraxen 重新生成含纸张选择器的新包；旧资源包不包含新选择器，只会显示纸张。

## 主要命令

- `/bccyberware`：打开自己的义体界面；
- `/bccyberware give <玩家> <义体ID> [数量]`：给予义体；
- `/bccyberware capacity <get|set|add> <玩家> [数值]`：查看或修改玩家永久容量；
- `/bccyberware reload`：完整校验后原子重载配置；
- `/bccyberware inspect`：查看手中义体的内部标识。
- `/bccyberware pack`：列出已加载的内容 Pack；
- `/bccyberware resourcepack generate`：合并 Pack Assets 与 `Generation/merge` 并生成资源包；
- `/bccyberware resourcepack [玩家]`：提示改用 Oraxen 的资源包重发功能；BcCyberware 不调用
  Oraxen 内部非公开 sender，避免版本升级时破坏兼容性。

更多服主说明见 `src/main/resources/README-配置说明.md`。
