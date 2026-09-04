# BcCyberware v0.0.6 交付包

1. 确认服务端为 Paper 1.21.11，使用 Java 21。
2. 先安装与你的 Paper 版本兼容的 Oraxen，再将 `BcCyberware-0.0.6.jar` 放入服务端
   `plugins/`。BcCyberware 将 Oraxen 声明为硬依赖，没有 Oraxen 时不会启动。
3. 首次启动后编辑 `plugins/BcCyberware/` 中的带注释配置。
4. 默认材质会自动释放到 `packs/core/Assets/`，并生成到
   `Generation/resource_pack.zip`。
5. 保持 `resources.yml` 中 `oraxen.enabled: true`。BcCyberware 会监听
   `OraxenPackGeneratedEvent`，把自己的 `assets/` 文件直接加入 Oraxen 的最终输出。
6. 义体资源继续放在 `plugins/BcCyberware/packs/<Pack ID>/Assets/`；无需手动移动
   `Generation/resource_pack.zip`，无需在 BcCyberware 中填写直链或开放额外端口。
   上传、托管、强制加载、提示和进服下发只配置 `plugins/Oraxen/settings.yml`。

## 完整性哈希

- 插件 JAR 与源码 JAR：以 GitHub Release 同批生成的 `SHA256SUMS.txt` 为准。
- 资源包 SHA-1：以 GitHub Release 同批生成的 `RESOURCE_PACK_SHA1.txt` 为准。

BcCyberware 不再提供 SELFHOST、EXTERNAL 或 ResourcePackManager 模式，也不会单独向
玩家发送第二份资源包。手动重发请使用 Oraxen 自己的资源包重发功能。

完整配置手册会在首次启动时释放为
`plugins/BcCyberware/README-配置说明.md`。
