# BcCyberware v0.0.5 交付包

1. 确认服务端为 Paper 1.21.11，使用 Java 21。
2. 将 `BcCyberware-0.0.5.jar` 和
   [ResourcePackManager 2.3.1 或更新版本](https://nightbreak.io/plugin/resourcepackmanager/)
   放入服务端 `plugins/`。不要使用 GitHub Releases 中不含公开 API 的旧 0.0.2 预发布包。
3. 首次启动后编辑 `plugins/BcCyberware/` 中的带注释配置。
4. 默认材质会自动释放到 `packs/core/Assets/`，并生成到
   `Generation/resource_pack.zip`。
5. 在 `resources.yml` 保持 `type: RESOURCE_PACK_MANAGER`，把
   `deployment.enabled` 改成 `true`。BcCyberware 会注册本地成品，由
   ResourcePackManager 与其他插件资源合并后统一托管并下发；不要再在
   `server.properties` 或其他插件中并行发送另一份资源包。
6. 义体资源继续放在 `plugins/BcCyberware/packs/<Pack ID>/Assets/`；无需手动移动
   `Generation/resource_pack.zip`，也无需给 BcCyberware 填写直链或开放 8168。
   资源冲突优先级在 ResourcePackManager 的 `priorityOrder` 中用 `BcCyberware` 配置。

## 完整性哈希

- 插件 JAR 与源码 JAR：以 GitHub Release 同批生成的 `SHA256SUMS.txt` 为准。
- 资源包 SHA-1：以 GitHub Release 同批生成的 `RESOURCE_PACK_SHA1.txt` 为准。

如不使用统一管理器，可改为 SELFHOST 或 EXTERNAL 独立模式；此时
BcCyberware 必须成为唯一资源包发送方，所有其他资源都要先合入 Pack `Assets/`
或 `Generation/merge/`。统一模式切换为独立模式后需要完整重启服务器。

完整配置手册会在首次启动时释放为
`plugins/BcCyberware/README-配置说明.md`。
