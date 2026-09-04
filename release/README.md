# BcCyberware v0.0.8 交付包

1. 确认服务端为 Paper 1.21.11，使用 Java 21。
2. 将 `BcCyberware-0.0.8.jar` 放入服务端 `plugins/`，不要安装 `sources.jar`。
   Oraxen 是可选依赖；需要自动合并下发材质时，再安装与你的 Paper 版本兼容的 Oraxen。
   未安装或未启用 Oraxen 时义体系统仍可运行，没有资源包时显示原版纸张。
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

v0.0.7 起会通过已加载的 Oraxen 实例解析其公开 API，兼容 AsPaper 等严格隔离插件
类路径的服务端；不要把 `sources.jar` 放入 `plugins/`。

完整配置手册会在首次启动时释放为
`plugins/BcCyberware/README-配置说明.md`。

升级请停服替换旧主 JAR，保留 `plugins/BcCyberware/` 下的配置和数据库。
旧物品在登录、打开容器或加载义体档案时更新外观标识，名称、说明、原主人和实例 UUID 不变。
已加载新包的玩家显示义体材质，未加载/拒绝/下载失败的玩家显示纸张。
如果关闭了 `generate-on-startup`，升级后执行 `/bccyberware resourcepack generate`；
旧包不含新纸张选择器，需让 Oraxen 重新生成并按其配置下发。
