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
`plugins/BcCyberware/Generation/resource_pack.zip`。启用部署后默认使用 `SELFHOST`
模式，由插件自己托管成品，并在玩家加入时自动下发；服主需要在 `resources.yml`
填写玩家可访问的服务器公网地址、将 `deployment.enabled` 改为 `true`，并放行资源包端口。

这些图标为本项目生成的原创资产，没有使用《赛博朋克 2077》的名称、描述、图标或模型。
