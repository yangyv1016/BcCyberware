package cn.bilicraft.bccyberware.api;

/**
 * 玩家义体容量的只读快照。
 *
 * @param enabled 容量系统是否已启用
 * @param used 已使用容量
 * @param maximum 容量上限
 * @param usedPercent 已使用百分比
 * @param installedCyberwareCount 已安装的非原生义体数量
 */
public record CapacityView(
        boolean enabled,
        double used,
        double maximum,
        double usedPercent,
        int installedCyberwareCount
) {
}
