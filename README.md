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
- 类 MythicCrucible 的 Pack Assets 合并与资源包生成；推荐注册给 ResourcePackManager，
  由全服统一合并、托管并只下发一个最终资源包；
- 保留 SELFHOST/EXTERNAL 独立模式，使用 Paper/Adventure 单包替换请求；
- 原创默认器官材质与示例义体资源包。

## 构建

```powershell
.\gradlew.bat clean test build
```

产物位于 `build/libs/BcCyberware-0.0.5.jar`。首次启动后，默认配置和核心 Pack 的 Assets 会释放到 `plugins/BcCyberware/`；插件不会在读取或重载时回写 YAML，因此服主注释不会被清除。

## 资源包部署

推荐把 [ResourcePackManager 2.3.1 或更新版本](https://nightbreak.io/plugin/resourcepackmanager/)
与本插件 JAR
一起放入服务端 `plugins/`，再将 `plugins/BcCyberware/resources.yml` 中
`deployment.enabled` 设为 `true`、保持 `type: RESOURCE_PACK_MANAGER`。义体资源仍放在
`plugins/BcCyberware/packs/<Pack ID>/Assets/`；生成的
`plugins/BcCyberware/Generation/resource_pack.zip` 会自动注册给统一管理器，不需要手动搬到
ResourcePackManager 目录，也不需要给 BcCyberware 填直链或开放 8168 端口。

ResourcePackManager 会把所有插件来源合成一个最终资源包并统一下发。请在它的配置中管理
公网托管、进服发送、强制加载和提示语，并按需把 `BcCyberware` 加入 `priorityOrder`。
GitHub Releases 页面上的旧 0.0.2 预发布包不含本插件使用的公开 API，请勿安装该旧包。

## 主要命令

- `/bccyberware`：打开自己的义体界面；
- `/bccyberware give <玩家> <义体ID> [数量]`：给予义体；
- `/bccyberware capacity <get|set|add> <玩家> [数值]`：查看或修改玩家永久容量；
- `/bccyberware reload`：完整校验后原子重载配置；
- `/bccyberware inspect`：查看手中义体的内部标识。
- `/bccyberware pack`：列出已加载的内容 Pack；
- `/bccyberware resourcepack generate`：合并 Pack Assets 与 `Generation/merge` 并生成资源包；
- `/bccyberware resourcepack [玩家]`：仅在 SELFHOST/EXTERNAL 独立模式下重新发送最终包；
  统一管理模式请使用 ResourcePackManager 的重发方式。

更多服主说明见 `src/main/resources/README-配置说明.md`。
