# BcCyberware 配置手册

BcCyberware 是一个面向 Paper 1.21.11 的纯服务端义体框架。物品默认使用 `PAPER`
作为载体，真实身份保存在 PDC 中；`item-model` 和 `custom-model-data` 只负责外观，
无法通过改名或仿制材质伪造义体。

## 1. 文件结构

```text
plugins/BcCyberware/
├─ config.yml                  # GUI、SQLite、容量、全局阈值
├─ messages.yml                # 玩家提示语
├─ resources.yml               # 资源包生成与统一部署
├─ Generation/
│  ├─ resource_pack.zip        # BcCyberware 生成的本地成品
│  └─ merge/                   # 服主最终覆盖层
├─ README-配置说明.md
├─ data/players.db             # SQLite 数据，不要手动编辑
└─ packs/
   ├─ README.yml
   └─ core/
      ├─ pack.yml
      ├─ slots.yml
      ├─ organs/*.yml
      ├─ cyberware/*.yml
      └─ thresholds/*.yml     # 可选
```

插件不会自动回写 YAML，所以注释和字段顺序会被保留。`/bccyberware reload`
会先完整读取、解析、检查全部文件，只在全部通过后原子替换运行中快照。
任何一项错误都会保留旧配置，并在控制台打印出错文件与路径。

> `database.file` 在插件启动时打开。运行中修改后请安全重启服务器，不要依赖
> reload 切换正在使用的数据库。

## 2. Pack 概念

Pack 是内容包，设计方式类似 MythicMobs 的可组合 Pack，但 BcCyberware 不依赖 MythicMobs。
一个 Pack 能单独包含槽位、原生器官、普通义体和容量阈值。

`pack.yml` 字段：

- `id`：目录级稳定 ID，只能使用小写字母、数字、`_` 和 `-`。
- `namespace`：自动补到本 Pack 内短 ID 前。例如 `native-heart` 变为 `core:native-heart`。
- `enabled`：关闭后整个 Pack 不加载。
- `priority`：数字越小越早加载；依赖关系的优先级更高。
- `depends`：硬依赖 Pack ID。缺失或关闭会使加载失败。
- `soft-depends`：存在时调整顺序，不存在也可继续。
- `allow-overrides`：允许本 Pack 用同一个完整 ID 替换早先定义。默认关闭，
  防止拼写错误造成静默覆盖。
- `Assets/`：该 Pack 自带的客户端资源，生成时按 Pack 顺序合并。
- `resource-packs` 已移除。每个 Pack 的客户端资源直接放入同目录 `Assets/`；
  非空旧字段会使配置校验失败并给出迁移提示，避免插件再次逐包下发。

引用本 Pack 的定义可以写短 ID。跨 Pack 引用必须写完整 ID，例如
`medical:native-lung`。每个定义最终都有唯一的 `namespace:id`。

## 3. 槽位

`slots.yml` 每个顶层键是一个身体槽位：

```yaml
eyes:
  type: EYES
  display-name: "<aqua>视觉系统"
  gui-slot: 13
  default-organ: native-eyes
  empty-effects:
    - trigger: PERIODIC
      interval: 4s
      actions:
        - type: POTION
          effect: BLINDNESS
          duration: 6s
          amplifier: 0
```

- `type` 是槽位类型。一种类型在全部 Pack 中只能对应一个身体位置；四肢应分别使用
  `LEFT_ARM`、`RIGHT_ARM`、`LEFT_LEG`、`RIGHT_LEG`。一件义体只能声明一个
  `slot-type`，因此不会在左右侧之间通用。
- `gui-slot` 从 0 开始；6 行箱子是 0～53。不填时会按定义顺序自动分配第一个空位。
- `default-organ` 是玩家首次建档时自动安装的原生器官；必须指向同类型的器官。
- `empty-effects` 是该部位为空时的缺陷。这意味着关键器官也可以拆下，但可以按配置
  失明、虚弱、持续受伤，直至死亡。

## 4. 器官与义体物品

`organs/*.yml` 和 `cyberware/*.yml` 都使用同一种 `items:` 结构。

```yaml
items:
  example-optics:
    slot-type: EYES
    material: PAPER
    item-model: "my_pack:example_optics"
    custom-model-data: 2101
    capacity-cost: 8
    original-organ: false
    display-name: "<aqua>示例光学模组"
    lore:
      - "<gray>容量：<aqua><capacity>"
    triggers: []
```

- `material`：义体的原版载体，默认 `PAPER`，可修改为其他合法 Material。
- `item-model`：Paper 1.21.11 的 item model 键，推荐为资源包中的完整命名空间 ID。
- `custom-model-data`：保留用于兼容服主已有的模型分配；物品身份不依赖它。
- `capacity-cost`：占用容量，可为 0。
- `original-organ: true`：创建时必须绑定原始主人 UUID 和姓名。它仍是可自由交易、
  拆卸和移植的真实物品，原始来源不会被改写。
- `display-name` 和 `lore` 支持 MiniMessage，并可使用 `<owner>`、`<owner_uuid>`、
  `<capacity>` 和 `<slot>`。

安装在身体数据中的部件不进入死亡掉落，会经 SQLite 跨重生、重登和重启保留。

## 5. 触发器、条件和动作

触发类型：`PASSIVE`、`EQUIP`、`UNEQUIP`、`PERIODIC`、`ATTACK`、`DAMAGED`、
`KILL`、`RIGHT_CLICK`、`SNEAK_SWAP`。纯服务端不能添加新按键，`SNEAK_SWAP` 表示潜行时
按“交换主手/副手”键。

```yaml
triggers:
  - type: PERIODIC
    interval: 10s
    chance: 0.5
    cooldown: 0s
    conditions:
      - type: HEALTH_PERCENT
        comparison: LTE
        value: 35
    actions:
      - type: HEAL
        amount: 2.0
```

`chance` 范围是 0～1。`interval` 是周期触发间隔，`cooldown` 是非周期触发冷却。
时间可写 `20t`、`5s`、`2m`。

条件类型：

- `HEALTH_PERCENT`：`comparison` + `value`，比较可用 `GTE/GT/LTE/LT/EQ`。
- `SNEAKING`：`value: true/false`。
- `PERMISSION`：`permission: some.permission`。
- `WORLD`：`world: world_name`。
- `TARGET_TYPE`：`entity: ZOMBIE`，只对有事件目标的触发生效。

动作类型：

- `POTION`：`effect`、`duration`、`amplifier`、可选 `target: SELF/TARGET`。
- `DAMAGE` / `HEAL`：`amount`，可选 `target`。
- `MESSAGE` / `ACTION_BAR`：`text`。
- `TITLE`：`title` 和 `subtitle`。
- `SOUND`：`sound`、`volume`、`pitch`。
- `PARTICLE`：`particle`、`count`，可选 `target`。
- `COMMAND`：`executor: CONSOLE/PLAYER` 和 `command`，支持 `<player>` 和 `<target>`。
- `DAMAGE_NEARBY`：`radius`、`amount`、`entity-filter: HOSTILE/NON_PLAYER/ALL`。
- `ATTRIBUTE`：只用于 `PASSIVE`；`attribute`、`amount`、`operation`。operation 可用
  Paper 的 `ADD_NUMBER`、`ADD_SCALAR`、`MULTIPLY_SCALAR_1`。

每个动作也能单独配置 `chance: 0.0～1.0`。

## 6. 容量与外部数值源

`capacity.enabled: false` 可完全关闭安装容量限制。启用时，最终上限从 `base`
开始，可加入玩家永久值，再按列表顺序合并外部数值源。

支持的 `type`：

- `FIXED`：读取 `value`。
- `PLAYER_DATA`：读取 BcCyberware 的玩家永久容量。
- `PERMISSION`：从 `permissions: {permission.node: value}` 中取已拥有节点的最大值。
- `MCMO_POWER_LEVEL`：原生读取 mcMMO 总能力等级。
- `MCMO_SKILL_LEVEL`：原生读取 mcMMO 的 `skill` 等级。
- `PLACEHOLDER`：通过 PlaceholderAPI 读取任意插件的数值占位符。
- `SCOREBOARD`：读取原版计分板 `objective`。

`formula` 只允许数值、`value`、括号和 `+ - * /`，例如 `(value + 10) * 0.25`。
计算后先限制到 `min/max`，再按 `operation` 合并：`ADD`、`SUBTRACT`、`SET`、
`MIN`、`MAX`。来源失败、插件未安装或返回非数字时使用 `fallback`，并按
`refresh-interval` 缓存，不会阻断玩家登录。

## 7. 数量与容量阈值

阈值可写在 `config.yml > capacity.thresholds` 或 Pack 的 `thresholds/*.yml`中。
`metric` 支持：

- `INSTALLED_COUNT`：已安装的非原生义体数。
- `USED_CAPACITY`：已使用容量。
- `USED_PERCENT`：容量使用百分比。

`comparison` 支持 `GTE/GT/LTE/LT/EQ`。条件持续满足时，每到 `interval`
执行一次 `actions`；所以可配置警告、药水效果、伤害、命令或特殊视听反应。

## 8. Pack Assets、生成与自动下发

资源资产采用 MythicMobs + MythicCrucible 风格。每个启用的内容 Pack 都可以携带
`Assets/` 目录，其内部就是标准资源包根目录结构：

```text
plugins/BcCyberware/packs/example/Assets/
├─ pack.mcmeta
├─ pack.png
└─ assets/<namespace>/...
```

插件在后台按 Pack 的 `priority` 合并这些目录，再用 `Generation/merge/` 中的服主文件执行
最终覆盖，输出为 `Generation/resource_pack.zip`。

推荐从 Nightbreak 安装 ResourcePackManager 2.3.1 或更新版本，并在 `resources.yml` 使用。
不要使用其 GitHub Releases 页面上的旧 0.0.2 预发布包，该旧包不含所需公开 API：

```yaml
deployment:
  enabled: true
  type: RESOURCE_PACK_MANAGER
```

BcCyberware 会通过 ResourcePackManager 的公开 API 注册相对于 `plugins/` 的本地 ZIP。
统一管理器负责把它与其他插件提供的资源合成一个最终服务器包，并负责托管、进服发送、
强制加载、提示语和重发。此模式下 BcCyberware 不监听 8168，不读取 `public-url`，
也绝不会再向玩家单独发送第二个包。相关发送选项只在 ResourcePackManager 中配置，
避免两套配置同时成为权限来源。

注册或重新生成后，BcCyberware 不会强制重启 ResourcePackManager；它会让统一管理器自带的
文件稳定监视器完成暂存和重新合并。这样兼容 ResourcePackManager 2.3.1 的初始化顺序，
也避免资源刚注册、尚未暂存就被全量重载清掉监视状态。等待数秒后可用 `/rspm status`
确认最终包状态。

如需决定同名模型、纹理发生冲突时谁覆盖谁，请把插件名 `BcCyberware` 加入
`plugins/ResourcePackManager/config.yml` 的 `priorityOrder`；未列出时会按
ResourcePackManager 的默认最低优先级参与合并。

从 `RESOURCE_PACK_MANAGER` 热切换为其他模式或关闭部署时，配置重载会保留原有资源包状态
并要求完整重启服务器。这是因为其公开 API 没有取消注册入口；拒绝热切换可以防止旧注册
仍被合并、同时 BcCyberware 又开始独立发送。

如果服务器不使用统一管理器，可以选择 `SELFHOST`。它会在配置端口托管该 ZIP；填写玩家
可访问的 `public-url`、启用部署并放行端口后，`auto-send.enabled=true` 会在玩家加入时
自动下发，`send-on-update=true` 会在重新生成后立即推送给在线玩家。插件使用带 SHA-1
的不可变下载路径，旧缓存只保留最近 4 代。

`EXTERNAL` 适合自行上传：先执行 generate，再上传 `Generation/resource_pack.zip`，把
完整下载地址和已上传文件的 40 位 `sha1` 写入配置，最后 reload 才会切换和热推送。
为避免上传时序错误，EXTERNAL 的 generate 只生成本地文件，不会擅自推送尚未上传的新包。

SELFHOST 面向单服正常玩家流量：最多同时传输 64 个请求，额外请求等待
最多 30 秒。大型资源包或高并发公网服建议在前方使用反向代理，或改用 EXTERNAL/CDN。
仅当修改监听地址或端口时，旧监听会给在途下载最多 5 秒排空时间；同端口的日常内容
热更新使用新的哈希地址，不受这项限制。

SELFHOST 与 EXTERNAL 都是“独立模式”：BcCyberware 使用单个 `replace=true` 的资源包请求，
并必须成为该服务器唯一的资源包发送方。请先把其他插件资源复制或生成到 Pack `Assets/`
或 `Generation/merge/`，同时清空 `server.properties` 中的资源包地址并关闭其他插件的发送。
否则其他发送方仍可能在不同时机额外弹出资源包；BcCyberware 无法替其他插件撤销请求。

默认核心材质会从插件 JAR 自动释放到 `packs/core/Assets/`，已存在的服主文件不会被覆盖。
旧 `external-packs` 列表不再支持，非空配置会拒绝加载并指向统一合并迁移方式。

## 9. 命令与权限

- `/bccyberware`（别名 `/cyberware`、`/cyber`）：打开个人受控 GUI。
- `/bccyberware give <玩家> <完整部件ID> [数量]`：发放真实部件。
- `/bccyberware capacity get|set|add <玩家> [数值]`：查看或修改永久容量。
- `/bccyberware inspect`：读取主手物品的 PDC 身份。
- `/bccyberware pack`：列出已加载 Pack 和顺序。
- `/bccyberware resourcepack generate`：重新生成；统一模式会触发 ResourcePackManager
  重新合并，SELFHOST 可按配置热推，EXTERNAL 需上传并更新 SHA-1 后再重载。
- `/bccyberware resourcepack [玩家]`：仅在 SELFHOST/EXTERNAL 独立模式下重新发送一个
  最终包；统一模式会提示改用 ResourcePackManager 的重发方式。
- `/bccyberware reload`：校验后原子重载配置。

普通玩家默认拥有 `bccyberware.use` 和 `bccyberware.install`。管理命令使用
`bccyberware.admin.*` 分权限，`bccyberware.admin` 默认授予 OP。

GUI 中所有点击与拖拽都由插件控制，不允许自由放置、Shift 快速移动、
数字键换位或双击收集。替换是一次原子操作：新部件从背包移出时，旧部件同时返回该空位。

## 10. 对其他插件的 API

将 BcCyberware 声明为 `depend` 或 `softdepend`，然后获取服务：

```java
BcCyberwareApi api = Bukkit.getServicesManager().load(BcCyberwareApi.class);
if (api != null) {
    api.openMenu(player); // 可从 Citizens NPC、自定义菜单等地方调用
}
```

API 还支持创建/检查部件、读取安装快照和容量快照、设置/累加玩家永久容量。
这些方法要在 Paper 主线程上调用。已安装物品只返回副本，其他插件不能通过修改 Map
绕过容量和槽位规则。
