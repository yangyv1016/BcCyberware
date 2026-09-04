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
- `item-model`：资源包中的完整物品定义 ID，如 `my_pack:example_optics`，对应
  `Assets/assets/my_pack/items/example_optics.json`。v0.0.8 起由生成器将该文件的 `model`
  接入纸张选择器，而不是把自定义键直接写入物品；没有资源包时显示原版纸张。
  CustomModelData 的 `strings[0]` 保留给插件，格式为 `bccyberware/<item-model>`；
  数字、布尔、颜色和其余字符串不受迁移影响。物理 `material` 配置仍保留，但基础外观统一为纸张。
  未提供对应物品定义文件时，该分支同样显示纸张；模型及纹理文件请一起放入本插件 Assets。
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

BcCyberware 将 Oraxen 声明为可选依赖，并使用 Oraxen 官方公开的资源包生成事件。为兼容
AsPaper 等严格隔离插件类路径的服务端，运行时会从已启用的 Oraxen 实例取得它自己的
类加载器，再解析公开 API；Oraxen 类不会被复制到 BcCyberware JAR。`resources.yml`
默认配置如下：

```yaml
oraxen:
  enabled: true
  reload-after-generation: true
```

BcCyberware 完成本地合并后，只读取中间 ZIP 内的 `assets/` 文件，并在
`OraxenPackGeneratedEvent` 中加入 Oraxen 的输出列表；根目录的 `pack.mcmeta` 和 `pack.png`
不会覆盖 Oraxen 的文件。同路径文件会先从事件输出中移除，再加入 BcCyberware 的版本，
因此 BcCyberware 的 `Generation/merge/` 仍然是其自身资源的最终覆盖层。建议所有义体资源
使用独立的 `bccyberware` 命名空间，避免和服主已有 Oraxen 资源发生冲突。

`assets/minecraft/items/paper.json` 是上述覆盖规则的特例：生成器自动追加义体的字符串
模型选择规则，注入时把 Oraxen 原来的 `model` 保留为 fallback，并保留顶层设置。
不会替换 Oraxen 原有的数字模型编号，也不会影响普通纸张。重复生成不会叠加同一层规则。
客户端没有加载资源包、拒绝或下载失败时直接使用内置纸张模型，无须服务器轮询玩家状态。

`reload-after-generation=true` 时，BcCyberware 会调用官方 `OraxenPack.reloadPack()`。
若 Oraxen 启动时正在生成，BcCyberware 会等待其上传事件后重试，直到自己的资源真正进入
一次生成事件。Oraxen 随后按 `plugins/Oraxen/settings.yml` 的 `Pack.upload` 与
`Pack.dispatch` 配置生成最终 ZIP、上传并向玩家发送。BcCyberware 不监听额外 HTTP 端口，
不要求下载直链，也不会调用 Paper 的资源包发送接口再发第二份包。

若临时设置 `oraxen.enabled=false`，BcCyberware 会停止注入，并在允许自动重载时触发 Oraxen
重建，以便从最终包中移除旧的义体资源。没有 Oraxen 或 Oraxen 启动失败时，BcCyberware
仍能启动并运行纸张模式，不会调度 Oraxen 重试任务，也不会单独发送资源包。

从旧版升级只需停服替换主 JAR，保留数据与配置。背包和末影箱在登录时、容器在打开时、
已安装部件在档案加载时自动迁移外观，不重建部件 UUID，不改变原主人、名称、说明和数量。
若 `generate-on-startup=false`，升级后执行 `/bccyberware resourcepack generate` 更新模型选择器。
服主自己编辑了损坏的模型或纹理时仍应修复资源文件；纸张回退不等于任意错误资源包校验器。

默认核心材质会从插件 JAR 自动释放到 `packs/core/Assets/`，已存在的服主文件不会被覆盖。
旧 `external-packs` 列表不再支持，非空配置会拒绝加载并指向统一合并迁移方式。

## 9. 命令与权限

- `/bccyberware`（别名 `/cyberware`、`/cyber`）：打开个人受控 GUI。
- `/bccyberware give <玩家> <完整部件ID> [数量]`：发放真实部件。
- `/bccyberware capacity get|set|add <玩家> [数值]`：查看或修改永久容量。
- `/bccyberware inspect`：读取主手物品的 PDC 身份。
- `/bccyberware pack`：列出已加载 Pack 和顺序。
- `/bccyberware resourcepack generate`：重新合并 BcCyberware Assets，并调用 Oraxen 重建、
  上传最终包。
- `/bccyberware resourcepack [玩家]`：提示改用 Oraxen 自己的资源包重发功能。
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
