package de.server.signshop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.HangingSign;
import org.bukkit.block.data.type.WallHangingSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ShopListener implements Listener {

    private final SignShopPlugin plugin;

    public ShopListener(SignShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getInspectModePlayers().remove(event.getPlayer().getUniqueId());
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        String firstLine = event.getLine(0);
        if (firstLine == null || !firstLine.trim().equalsIgnoreCase("Shop")) {
            return;
        }

        Player player = event.getPlayer();
        Block signBlock = event.getBlock();

        if (signBlock.getBlockData() instanceof HangingSign || signBlock.getBlockData() instanceof WallHangingSign) {
            player.sendMessage("§cEin SignShop kann nicht auf einem Hängeschild erstellt werden!");
            event.setCancelled(true);
            return;
        }

        Block supportBlock = plugin.getAttachedBlock(signBlock);
        if (!plugin.isSafeSupportBlock(supportBlock)) {
            player.sendMessage("§cDu kannst keinen Shop an einem unsicheren Block (z. B. Sand, Kies, Bambus) befestigen!");
            event.setCancelled(true);
            signBlock.breakNaturally();
            return;
        }

        int shopId = plugin.getNextShopId();
        ShopData shop = new ShopData(
                shopId,
                signBlock.getLocation(),
                player.getUniqueId(),
                player.getName(),
                null,
                1,
                0.0,
                0.0,
                new ItemStack[54]
        );

        plugin.registerShop(shop);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.updateSign(shop));

        player.sendMessage("§aSignShop §b#" + shopId + " §aerfolgreich erstellt! Schaue das Schild an und nutze:");
        player.sendMessage("§7- §e/shop set item §7(Item in der Hand festlegen)");
        player.sendMessage("§7- §e/shop set a <1-64> §7(Anzahl festlegen)");
        player.sendMessage("§7- §e/shop set b <Preis> §7(Kaufpreis festlegen)");
        player.sendMessage("§7- §e/shop set s <Preis> §7(Verkaufspreis festlegen)");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        ShopData shop = plugin.getShop(clicked.getLocation());
        if (shop == null) return;

        Player player = event.getPlayer();
        Action action = event.getAction();

        if (plugin.getInspectModePlayers().remove(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.sendShopInfo(player, shop);
            return;
        }

        boolean isOwner = shop.getOwnerUuid().equals(player.getUniqueId());
        boolean isAdmin = player.hasPermission("signshop.admin");

        if (action == Action.LEFT_CLICK_BLOCK && player.isSneaking() && (isOwner || isAdmin)) {
            return;
        }

        event.setCancelled(true);

        if (!isOwner && isAdmin && action == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
            player.openInventory(shop.getInventory());
            player.sendMessage("§7[Admin] Öffne Lager von Shop §b#" + shop.getId() + " §7(Besitzer: §e" + shop.getOwnerName() + "§7).");
            return;
        }

        if (isOwner) {
            if (action == Action.RIGHT_CLICK_BLOCK) {
                player.openInventory(shop.getInventory());
            } else if (action == Action.LEFT_CLICK_BLOCK) {
                player.sendMessage("§7[Shop §b#" + shop.getId() + "§7] Halte §eShift (Sneaken)§7 gedrückt, um deinen Shop abzubauen.");
            }
            return;
        }

        if (shop.getItemTemplate() == null) {
            player.sendMessage("§cDieser Shop (#" + shop.getId() + ") ist noch nicht vollständig eingerichtet.");
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            handleCustomerBuy(player, shop);
        } else if (action == Action.LEFT_CLICK_BLOCK) {
            handleCustomerSell(player, shop);
        }
    }

    private void handleCustomerBuy(Player buyer, ShopData shop) {
        if (shop.getBuyPrice() <= 0) {
            buyer.sendMessage("§cIn diesem Shop kann man nichts kaufen.");
            return;
        }

        if (!shop.hasEnoughStock()) {
            buyer.sendMessage("§cDieser Shop ist aktuell ausverkauft!");
            plugin.updateSign(shop);
            return;
        }

        Economy econ = plugin.getEconomy();
        if (econ == null) {
            buyer.sendMessage("§cWährungssystem (Vault) ist nicht verfügbar.");
            return;
        }

        double price = shop.getBuyPrice();
        if (!econ.has(buyer, price)) {
            buyer.sendMessage("§cDu hast nicht genügend Geld! Benötigt: §e" + price);
            return;
        }

        ItemStack toGive = shop.getItemTemplate().asQuantity(shop.getAmount());
        if (!hasSpaceInPlayerInventory(buyer.getInventory(), toGive)) {
            buyer.sendMessage("§cDein Inventar ist voll!");
            return;
        }

        OfflinePlayer owner = Bukkit.getOfflinePlayer(shop.getOwnerUuid());
        econ.withdrawPlayer(buyer, price);
        econ.depositPlayer(owner, price);

        removeItemsMatching(shop.getInventory(), shop.getItemTemplate(), shop.getAmount());
        buyer.getInventory().addItem(toGive);

        plugin.updateSign(shop);
        plugin.saveShops();

        buyer.sendMessage("§aDu hast §e" + shop.getAmount() + "x " + plugin.formatMaterialName(toGive.getType()) + " §afür §e" + price + " §agekauft!");
    }

    private void handleCustomerSell(Player seller, ShopData shop) {
        if (shop.getSellPrice() <= 0) {
            seller.sendMessage("§cDieser Shop kauft keine Items an.");
            return;
        }

        if (!shop.hasEnoughSpace()) {
            seller.sendMessage("§cDas Lager dieses Shops ist komplett voll!");
            plugin.updateSign(shop);
            return;
        }

        if (!hasItemsMatching(seller.getInventory(), shop.getItemTemplate(), shop.getAmount())) {
            seller.sendMessage("§cDu hast nicht genügend passende Items (inkl. exakter Eigenschaften/NBT) im Inventar!");
            return;
        }

        Economy econ = plugin.getEconomy();
        if (econ == null) {
            seller.sendMessage("§cWährungssystem (Vault) ist nicht verfügbar.");
            return;
        }

        double price = shop.getSellPrice();
        OfflinePlayer owner = Bukkit.getOfflinePlayer(shop.getOwnerUuid());
        if (!econ.has(owner, price)) {
            seller.sendMessage("§cDer Shop-Besitzer hat aktuell nicht genug Geld für den Ankauf!");
            return;
        }

        econ.withdrawPlayer(owner, price);
        econ.depositPlayer(seller, price);

        removeItemsMatching(seller.getInventory(), shop.getItemTemplate(), shop.getAmount());
        shop.getInventory().addItem(shop.getItemTemplate().asQuantity(shop.getAmount()));

        plugin.updateSign(shop);
        plugin.saveShops();

        seller.sendMessage("§aDu hast §e" + shop.getAmount() + "x " + plugin.formatMaterialName(shop.getItemTemplate().getType()) + " §afür §e" + price + " §averkauft!");
    }

    private boolean hasItemsMatching(Inventory inv, ItemStack template, int required) {
        int count = 0;
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack != null && stack.isSimilar(template)) {
                count += stack.getAmount();
                if (count >= required) return true;
            }
        }
        return false;
    }

    private void removeItemsMatching(Inventory inv, ItemStack template, int toRemove) {
        int remaining = toRemove;
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && stack.isSimilar(template)) {
                if (stack.getAmount() <= remaining) {
                    remaining -= stack.getAmount();
                    inv.setItem(i, null);
                } else {
                    stack.setAmount(stack.getAmount() - remaining);
                    remaining = 0;
                }
                if (remaining <= 0) break;
            }
        }
    }

    private boolean hasSpaceInPlayerInventory(Inventory playerInv, ItemStack itemToAdd) {
        int remaining = itemToAdd.getAmount();
        int maxStack = itemToAdd.getMaxStackSize();
        for (ItemStack stack : playerInv.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                remaining -= maxStack;
            } else if (stack.isSimilar(itemToAdd) && stack.getAmount() < maxStack) {
                remaining -= (maxStack - stack.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof ShopData shop) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.updateSign(shop));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShopData shop) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.updateSign(shop));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ShopData shop) {
            plugin.updateSign(shop);
            plugin.saveShops();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        ShopData shop = plugin.getShop(block.getLocation());
        if (shop != null) {
            boolean isOwner = shop.getOwnerUuid().equals(player.getUniqueId());
            boolean isAdmin = player.hasPermission("signshop.admin");

            if (!isOwner && !isAdmin) {
                player.sendMessage("§cDu kannst den Shop (#" + shop.getId() + ") eines anderen Spielers nicht abbauen!");
                event.setCancelled(true);
                return;
            }

            if (!player.isSneaking()) {
                player.sendMessage("§cBitte halte §eShift (Sneaken) §cgedrückt, um den Shop (#" + shop.getId() + ") abzubauen!");
                event.setCancelled(true);
                return;
            }

            Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
            for (ItemStack item : shop.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    block.getWorld().dropItemNaturally(dropLoc, item);
                }
            }
            shop.getInventory().clear();
            plugin.removeShop(shop);
            player.sendMessage("§aSignShop §b#" + shop.getId() + " §awurde abgebaut und der Kisteninhalt wurde gedroppt.");
            return;
        }

        if (plugin.hasAttachedShop(block)) {
            player.sendMessage("§cDieser Block ist durch einen daran befestigten SignShop geschützt!");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.getShop(block.getLocation()) != null || plugin.hasAttachedShop(block));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.getShop(block.getLocation()) != null || plugin.hasAttachedShop(block));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (plugin.getShop(block.getLocation()) != null || plugin.hasAttachedShop(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (plugin.getShop(block.getLocation()) != null || plugin.hasAttachedShop(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (plugin.getShop(event.getBlock().getLocation()) != null || plugin.hasAttachedShop(event.getBlock())) {
            event.setCancelled(true);
        }
    }
}
