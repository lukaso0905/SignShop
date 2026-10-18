package de.server.signshop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ShopData implements InventoryHolder {

    private final int id;
    private final Location signLocation;
    private UUID ownerUuid;
    private String ownerName;
    private ItemStack itemTemplate;
    private int amount;
    private double buyPrice;
    private double sellPrice;
    private final Inventory inventory;

    public ShopData(int id, Location signLocation, UUID ownerUuid, String ownerName, ItemStack itemTemplate,
                    int amount, double buyPrice, double sellPrice, ItemStack[] contents) {
        this.id = id;
        this.signLocation = signLocation.toBlockLocation();
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.itemTemplate = itemTemplate != null ? itemTemplate.asOne() : null;
        this.amount = Math.max(1, Math.min(64, amount));
        this.buyPrice = Math.max(0, buyPrice);
        this.sellPrice = Math.max(0, sellPrice);
        this.inventory = Bukkit.createInventory(this, 54, "§8Shop #" + id + " §7- §9" + ownerName);

        if (contents != null) {
            ItemStack[] adjusted = new ItemStack[54];
            for (int i = 0; i < Math.min(contents.length, 54); i++) {
                adjusted[i] = contents[i];
            }
            this.inventory.setContents(adjusted);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public boolean hasCustomNbt() {
        if (itemTemplate == null || !itemTemplate.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = itemTemplate.getItemMeta();
        if (meta.hasDisplayName() || meta.hasLore() || meta.hasEnchants() || meta.hasCustomModelData() || meta.hasAttributeModifiers()) {
            return true;
        }
        if (meta instanceof EnchantmentStorageMeta storage && storage.hasStoredEnchants()) {
            return true;
        }
        if (meta instanceof Damageable damageable && damageable.hasDamage()) {
            return true;
        }
        return !meta.getPersistentDataContainer().isEmpty();
    }

    public int getMatchingStockCount() {
        if (itemTemplate == null) return 0;
        int count = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.isSimilar(itemTemplate)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    public boolean hasEnoughStock() {
        return itemTemplate != null && getMatchingStockCount() >= amount;
    }

    public boolean hasEnoughSpace() {
        if (itemTemplate == null) return false;
        int maxStack = itemTemplate.getMaxStackSize();
        int freeSpace = 0;

        for (ItemStack stack : inventory.getContents()) {
            if (stack == null || stack.getType().isAir()) {
                freeSpace += maxStack;
            } else if (stack.isSimilar(itemTemplate) && stack.getAmount() < maxStack) {
                freeSpace += (maxStack - stack.getAmount());
            }
            if (freeSpace >= amount) {
                return true;
            }
        }
        return false;
    }

    public int getId() {
        return id;
    }

    public Location getSignLocation() {
        return signLocation;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwner(UUID ownerUuid, String ownerName) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public ItemStack getItemTemplate() {
        return itemTemplate != null ? itemTemplate.clone() : null;
    }

    public void setItemTemplate(ItemStack itemTemplate) {
        this.itemTemplate = itemTemplate != null ? itemTemplate.asOne() : null;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(1, Math.min(64, amount));
    }

    public double getBuyPrice() {
        return buyPrice;
    }

    public void setBuyPrice(double buyPrice) {
        this.buyPrice = Math.max(0, buyPrice);
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public void setSellPrice(double sellPrice) {
        this.sellPrice = Math.max(0, sellPrice);
    }
}
