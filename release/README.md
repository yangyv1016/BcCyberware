# BcCyberware v0.0.2 交付包

1. 确认服务端为 Paper 1.21.11，使用 Java 21。
2. 将 `BcCyberware-0.0.2.jar` 复制到服务端 `plugins/`。
3. 首次启动后编辑 `plugins/BcCyberware/` 中的带注释配置。
4. 默认材质会自动释放到 `packs/core/Assets/`，并生成到
   `Generation/resource_pack.zip`。
5. 在 `resources.yml` 填写玩家可访问的服务器公网地址和 SELFHOST 端口，把
   `deployment.enabled` 改成 `true`，并在防火墙/服务器面板放行该 TCP 端口；
   玩家加入后会自动下载。

## 完整性哈希

- 插件 JAR 与源码 JAR：以 GitHub Release 同批生成的 `SHA256SUMS.txt` 为准。
- 资源包 SHA-1: `717788d55f2d2f152b96c3d4f550810b1ef8cf37`

内置 SELFHOST 适合单服正常流量；大型资源包或高并发公网服建议配合反向代理，
或切换到 EXTERNAL/CDN。

完整配置手册会在首次启动时释放为
`plugins/BcCyberware/README-配置说明.md`。
