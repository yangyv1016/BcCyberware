# BcCyberware 示例资源包

`BcCyberware-Example-Pack.zip` 面向 Minecraft Java 1.21.11，资源包版本为 75.0。
它包含 Core Pack 的 10 个原生身体部件外观（四肢分左右）和 8 件示例义体外观。

- 可上传的完成品：`BcCyberware-Example-Pack.zip`
- SHA-1：`BcCyberware-Example-Pack.sha1`
- 可编辑展开目录：`BcCyberware-Example-Pack/`
- 高分辨率透明原图：`source-art/`
- 可重复构建脚本：`../tools/build_resource_pack.py`

插件会在首次启动时把默认资源释放到
`plugins/BcCyberware/packs/core/Assets/`，然后生成
`plugins/BcCyberware/Generation/resource_pack.zip`。这是中间产物，安装并启用 Oraxen 后，
BcCyberware 会通过其公共生成事件合并到唯一的最终资源包，上传和下发由 Oraxen 负责。
无需填写 BcCyberware 下载直链或开放额外端口。没有 Oraxen 时仍可使用纸张模式。

v0.0.8 起包含 `assets/minecraft/items/paper.json` 字符串选择器。客户端没有加载资源包时
物品显示原版纸张；加载后使用对应义体材质。插件注入时会保留 Oraxen 原有纸张模型规则。

这些图标为本项目生成的原创资产，没有使用《赛博朋克 2077》的名称、描述、图标或模型。
