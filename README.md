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
- 多资源包 URL、SHA-1、加载顺序和状态反馈；
- 原创默认器官材质与示例义体资源包。

## 构建

```powershell
.\gradlew.bat clean test build
```

产物位于 `build/libs/BcCyberware-0.0.1.jar`。首次启动后，默认配置会释放到 `plugins/BcCyberware/`；插件不会在读取或重载时回写 YAML，因此服主注释不会被清除。

## 主要命令

- `/bccyberware`：打开自己的义体界面；
- `/bccyberware give <玩家> <义体ID> [数量]`：给予义体；
- `/bccyberware capacity <get|set|add> <玩家> [数值]`：查看或修改玩家永久容量；
- `/bccyberware reload`：完整校验后原子重载配置；
- `/bccyberware inspect`：查看手中义体的内部标识。
- `/bccyberware pack`：列出已加载的内容 Pack；
- `/bccyberware resourcepack [玩家]`：重新发送客户端资源包。

更多服主说明见 `src/main/resources/README-配置说明.md`。
