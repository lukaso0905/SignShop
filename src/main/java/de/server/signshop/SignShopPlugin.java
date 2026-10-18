package de.server.signshop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.HangingSign;
import org.bukkit.block.data.type.WallHangingSign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

public class SignShopPlugin extends JavaPlugin {

    private final Map<Location, ShopData> shopsByLocation = new HashMap<>();
    private final Map<Integer, ShopData> shopsById = new TreeMap<>();
    private final Set<UUID> inspectModePlayers = new HashSet<>();
    private int nextShopId = 1;

    private File shopsFile;
    private FileConfiguration shopsConfig;
    private Economy economy;
    private final DecimalFormat priceFormat = new DecimalFormat("0.##");

    @Override
    public void onEnable() {
        setupEconomy();
        initConfig();
        loadShops();

        Bukkit.getPluginManager().registerEvents(new ShopListener(this), this);
        ShopCommand shopCommand = new ShopCommand(this);
        Objects.requireNonNull(getCommand("shop")).setExecutor(shopCommand);
        Objects.requireNonNull(getCommand("shop")).setTabCompleter(shopCommand);

        getLogger().info("SignShop geladen! (" + shopsById.size() + " Shops aktiv)");
    }

    @Override
    public void onDisable() {
        saveShops();
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault wurde nicht gefunden! Geldtransaktionen sind deaktiviert.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
        }
    }

    public Economy getEconomy() {
        return economy;
    }

    public Set<UUID> getInspectModePlayers() {
        return inspectModePlayers;
    }

    private void initConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        shopsFile = new File(getDataFolder(), "shops.yml");
        if (!shopsFile.exists()) {
            try {
                shopsFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Konnte shops.yml nicht erstellen: " + e.getMessage());
            }
        }
        shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);
    }

    public int getNextShopId() {
        while (shopsById.containsKey(nextShopId)) {
            nextShopId++;
        }
        return nextShopId++;
    }

    public void loadShops() {
        shopsByLocation.clear();
        shopsById.clear();
        nextShopId = shopsConfig.getInt("nextShopId", 1);

        ConfigurationSection section = shopsConfig.getConfigurationSection("shops");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) continue;

            String worldName = s.getString("world");
            World world = worldName != null ? Bukkit.getWorld(worldName) : null;
            if (world == null) continue;

            int id = s.getInt("id", -1);
            if (id <= 0) {
                id = getNextShopId();
            } else if (id >= nextShopId) {
                nextShopId = id + 1;
            }

            int x = s.getInt("x");
            int y = s.getInt("y");
            int z = s.getInt("z");
            Location loc = new Location(world, x, y, z).toBlockLocation();

            UUID ownerUuid = UUID.fromString(Objects.requireNonNull(s.getString("ownerUuid")));
            String ownerName = s.getString("ownerName", "Unbekannt");
            ItemStack item = s.getItemStack("item");
            int amount = s.getInt("amount", 1);
            double buyPrice = s.getDouble("buyPrice", 0.0);
            double sellPrice = s.getDouble("sellPrice", 0.0);

            List<?> rawList = s.getList("inventory");
            ItemStack[] contents = new ItemStack[54];
            if (rawList != null) {
                for (int i = 0; i < Math.min(rawList.size(), 54); i++) {
                    Object obj = rawList.get(i);
                    if (obj instanceof ItemStack stack) {
                        contents[i] = stack;
                    }
                }
            }

            ShopData shop = new ShopData(id, loc, ownerUuid, ownerName, item, amount, buyPrice, sellPrice, contents);
            shopsByLocation.put(loc, shop);
            shopsById.put(id, shop);
            updateSign(shop);
        }
    }

    public void saveShops() {
        shopsConfig.set("nextShopId", nextShopId);
        shopsConfig.set("shops", null);

        for (ShopData shop : shopsById.values()) {
            String path = "shops.id_" + shop.getId();
            Location loc = shop.getSignLocation();
            shopsConfig.set(path + ".id", shop.getId());
            shopsConfig.set(path + ".world", loc.getWorld().getName());
            shopsConfig.set(path + ".x", loc.getBlockX());
            shopsConfig.set(path + ".y", loc.getBlockY());
            shopsConfig.set(path + ".z", loc.getBlockZ());
            shopsConfig.set(path + ".ownerUuid", shop.getOwnerUuid().toString());
            shopsConfig.set(path + ".ownerName", shop.getOwnerName());
            shopsConfig.set(path + ".item", shop.getItemTemplate());
            shopsConfig.set(path + ".amount", shop.getAmount());
            shopsConfig.set(path + ".buyPrice", shop.getBuyPrice());
            shopsConfig.set(path + ".sellPrice", shop.getSellPrice());
            shopsConfig.set(path + ".inventory", Arrays.asList(shop.getInventory().getContents()));
        }
        try {
            shopsConfig.save(shopsFile);
        } catch (IOException e) {
            getLogger().severe("Fehler beim Speichern der shops.yml: " + e.getMessage());
        }
    }

    public ShopData getShop(Location loc) {
        return shopsByLocation.get(loc.toBlockLocation());
    }

    public ShopData getShopById(int id) {
        return shopsById.get(id);
    }

    public Collection<ShopData> getAllShops() {
        return Collections.unmodifiableCollection(shopsById.values());
    }

    public void registerShop(ShopData shop) {
        shopsByLocation.put(shop.getSignLocation(), shop);
        shopsById.put(shop.getId(), shop);
        updateSign(shop);
        saveShops();
    }

    public void removeShop(ShopData shop) {
        shopsByLocation.remove(shop.getSignLocation());
        shopsById.remove(shop.getId());
        saveShops();
    }

    @SuppressWarnings("deprecation")
    public void updateSign(ShopData shop) {
        Block block = shop.getSignLocation().getBlock();
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }

        sign.setWaxed(true);

        String line1 = "§9" + shop.getOwnerName();
        String line2 = "§0" + shop.getAmount();

        boolean canBuy = shop.getBuyPrice() > 0 && shop.hasEnoughStock();
        boolean canSell = shop.getSellPrice() > 0 && shop.hasEnoughSpace();

        String buyColor = canBuy ? "§a" : "§c";
        String sellColor = canSell ? "§a" : "§c";

        String buyText = buyColor + "B " + priceFormat.format(shop.getBuyPrice());
        String sellText = sellColor + priceFormat.format(shop.getSellPrice()) + " S";
        String line3 = buyText + " §7: " + sellText;

        String line4;
        ItemStack item = shop.getItemTemplate();
        if (item == null || item.getType().isAir()) {
            line4 = "§7[Kein Item]";
        } else {
            String rawName = formatMaterialName(item.getType());
            line4 = (shop.hasCustomNbt() ? "§o" : "") + rawName;
        }

        SignSide front = sign.getSide(Side.FRONT);
        front.setLine(0, line1);
        front.setLine(1, line2);
        front.setLine(2, line3);
        front.setLine(3, line4);

        SignSide back = sign.getSide(Side.BACK);
        back.setLine(0, line1);
        back.setLine(1, line2);
        back.setLine(2, line3);
        back.setLine(3, line4);

        sign.update(true, false);
    }

    @SuppressWarnings("deprecation")
    public void sendShopInfo(CommandSender viewer, ShopData shop) {
        Location loc = shop.getSignLocation();
        ItemStack item = shop.getItemTemplate();

        viewer.sendMessage("§8§m----------------------------------------");
        viewer.sendMessage("§9§lSignShop Info §8[§bID: #" + shop.getId() + "§8]");
        viewer.sendMessage("§7Besitzer: §f" + shop.getOwnerName() + " §8(" + shop.getOwnerUuid() + ")");
        viewer.sendMessage("§7Position: §f" + loc.getWorld().getName() + " §7@ §e"
                + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());

        if (item == null || item.getType().isAir()) {
            viewer.sendMessage("§7Item: §cNoch kein Item festgelegt");
            viewer.sendMessage("§8§m----------------------------------------");
            return;
        }

        int stock = shop.getMatchingStockCount();
        viewer.sendMessage("§7Item-Typ: §e" + formatMaterialName(item.getType()) + " §8(" + item.getType().getKey().getKey() + ")");
        viewer.sendMessage("§7Menge pro Klick: §f" + shop.getAmount() + " §8| §7Auf Lager: §b" + stock + " Stück");
        viewer.sendMessage("§7Kaufen (Buy): " + (shop.hasEnoughStock() && shop.getBuyPrice() > 0 ? "§a" : "§c") + shop.getBuyPrice()
                + " §8| §7Verkaufen (Sell): " + (shop.hasEnoughSpace() && shop.getSellPrice() > 0 ? "§a" : "§c") + shop.getSellPrice());

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                viewer.sendMessage("§7Item-Name: §r" + meta.getDisplayName());
            }

            if (item.getType().getMaxDurability() > 0 && meta instanceof Damageable damageable) {
                int maxDur = item.getType().getMaxDurability();
                int curDur = maxDur - damageable.getDamage();
                viewer.sendMessage("§7Haltbarkeit: §b" + curDur + " §7/ §b" + maxDur);
            }

            if (meta.hasEnchants()) {
                viewer.sendMessage("§7Verzauberungen:");
                for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                    viewer.sendMessage("  §d- " + entry.getKey().getKey().getKey() + " " + entry.getValue());
                }
            }

            if (meta instanceof EnchantmentStorageMeta storage && storage.hasStoredEnchants()) {
                viewer.sendMessage("§7Buch-Verzauberungen:");
                for (Map.Entry<Enchantment, Integer> entry : storage.getStoredEnchants().entrySet()) {
                    viewer.sendMessage("  §d- " + entry.getKey().getKey().getKey() + " " + entry.getValue());
                }
            }

            if (meta.hasLore() && meta.getLore() != null) {
                viewer.sendMessage("§7Lore:");
                for (String line : meta.getLore()) {
                    viewer.sendMessage("  §f" + line);
                }
            }
        }
        viewer.sendMessage("§8§m----------------------------------------");
    }

    public String formatMaterialName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    public Block getAttachedBlock(Block signBlock) {
        BlockData data = signBlock.getBlockData();
        if (data instanceof WallSign wallSign) {
            return signBlock.getRelative(wallSign.getFacing().getOppositeFace());
        } else if (data instanceof WallHangingSign wallHanging) {
            return signBlock.getRelative(wallHanging.getFacing().getOppositeFace());
        } else if (data instanceof HangingSign) {
            return signBlock.getRelative(BlockFace.UP);
        } else if (data instanceof org.bukkit.block.data.type.Sign) {
            return signBlock.getRelative(BlockFace.DOWN);
        } else if (data instanceof Directional dir) {
            return signBlock.getRelative(dir.getFacing().getOppositeFace());
        }
        return signBlock.getRelative(BlockFace.DOWN);
    }

    public boolean isSafeSupportBlock(Block block) {
        if (block == null) return false;
        Material type = block.getType();

        if (!type.isSolid() || type.hasGravity()) {
            return false;
        }

        if (Tag.LEAVES.isTagged(type)
                || Tag.SHULKER_BOXES.isTagged(type)
                || Tag.DOORS.isTagged(type)
                || Tag.TRAPDOORS.isTagged(type)
                || Tag.BEDS.isTagged(type)
                || Tag.SIGNS.isTagged(type)
                || Tag.ALL_HANGING_SIGNS.isTagged(type)
                || Tag.ICE.isTagged(type)) {
            return false;
        }

        return switch (type) {
            case SAND, RED_SAND, GRAVEL, SUSPICIOUS_SAND, SUSPICIOUS_GRAVEL,
                 BAMBOO, BAMBOO_SAPLING, CACTUS, SUGAR_CANE, CHORUS_PLANT, CHORUS_FLOWER,
                 TNT, SCAFFOLDING, FARMLAND, DIRT_PATH, MELON, PUMPKIN, CARVED_PUMPKIN,
                 PISTON, STICKY_PISTON, PISTON_HEAD, MOVING_PISTON,
                 SLIME_BLOCK, HONEY_BLOCK, POINTED_DRIPSTONE,
                 POWDER_SNOW, SPONGE, WET_SPONGE -> false;
            default -> true;
        };
    }

    public boolean hasAttachedShop(Block supportBlock) {
        for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN}) {
            Block relative = supportBlock.getRelative(face);
            ShopData shop = getShop(relative.getLocation());
            if (shop != null) {
                Block attached = getAttachedBlock(relative);
                if (attached != null && attached.equals(supportBlock)) {
                    return true;
                }
            }
        }
        return false;
    }
}
