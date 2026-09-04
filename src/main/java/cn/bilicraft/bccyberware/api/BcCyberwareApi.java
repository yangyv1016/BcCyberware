package cn.bilicraft.bccyberware.api;

import cn.bilicraft.bccyberware.item.CyberwareIdentity;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * BcCyberware 对其他服务端插件提供的稳定入口。
 * <p>
 * 可通过 {@code Bukkit.getServicesManager().load(BcCyberwareApi.class)} 获取实例。
 * 所有方法都应在 Paper 主线程调用；返回的物品与 Map 均为副本，
 * 修改它们不会绕过 BcCyberware 的受控安装流程。
 */
public interface BcCyberwareApi {
    /** 在玩家数据已就绪时打开受控义体菜单。 */
    boolean openMenu(Player player);

    /** 按完整命名空间 ID 创建部件。原生器官必须传入 originalOwner。 */
    Optional<ItemStack> createPart(String definitionId, OfflinePlayer originalOwner);

    /** 读取物品的 PDC 身份；外观模型不参与身份判定。 */
    Optional<CyberwareIdentity> inspect(ItemStack item);

    /** 获取已加载玩家的“槽位 ID -> 安装物品”快照。 */
    Optional<Map<String, ItemStack>> installedParts(UUID playerId);

    /** 获取已加载玩家的容量快照。 */
    Optional<CapacityView> capacity(Player player);

    /** 设置玩家的永久容量加成，并请求异步保存。 */
    boolean setPermanentCapacity(UUID playerId, double value);

    /** 在玩家当前的永久容量加成上累加数值。 */
    boolean addPermanentCapacity(UUID playerId, double delta);
}
