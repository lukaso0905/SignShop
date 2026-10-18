package de.server.signshop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final SignShopPlugin plugin;

    public ShopCommand(SignShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("signshop.admin")) {
                sender.sendMessage("§cDazu hast du keine Berechtigung (signshop.admin).");
                return true;
            }
            handleAdminCommand(sender, args);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDieser Befehl kann nur von einem Spieler ausgeführt werden.");
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("set")) {
            sendHelp(player);
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        Block targetBlock = player.getTargetBlockExact(5);
        ShopData targetShop = targetBlock != null ? plugin.getShop(targetBlock.getLocation()) : null;

        if (sub.equals("i") || sub.equals("info")) {
            if (targetShop != null) {
                plugin.sendShopInfo(player, targetShop);
            } else {
                plugin.getInspectModePlayers().add(player.getUniqueId());
                player.sendMessage("§e§lInfo-Modus aktiviert: §7Klicke jetzt auf ein beliebiges Shop-Schild, um dessen §bShop-ID §7und Details abzurufen!");
            }
            return true;
        }

        if (targetShop == null) {
            player.sendMessage("§cBitte schaue direkt auf ein Shop-Schild (max. 5 Blöcke Entfernung).");
            return true;
        }

        if (!targetShop.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("signshop.admin")) {
            player.sendMessage("§cNur der Besitzer dieses Shops (" + targetShop.getOwnerName() + ") kann Einstellungen ändern!");
            return true;
        }

        applyShopSetting(player, targetShop, sub, args, 2);
        return true;
    }

    private void applyShopSetting(CommandSender sender, ShopData shop, String sub, String[] args, int valueIndex) {
        switch (sub) {
            case "item" -> {
                if (args.length > valueIndex) {
                    Material mat = Material.matchMaterial(args[valueIndex]);
                    if (mat == null || !mat.isItem() || mat.isAir()) {
                        sender.sendMessage("§cUngültige Item-ID: §e" + args[valueIndex]);
                        return;
                    }
                    shop.setItemTemplate(new ItemStack(mat, 1));
                    sender.sendMessage("§a[Shop #" + shop.getId() + "] Item wurde auf §e" + plugin.formatMaterialName(mat) + " §agesetzt.");
                } else {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("§cAus der Konsole muss eine Item-ID angegeben werden.");
                        return;
                    }
                    ItemStack handItem = player.getInventory().getItemInMainHand();
                    if (handItem.getType().isAir()) {
                        player.sendMessage("§cBitte halte ein Item in der Hand oder gib eine Item-ID an.");
                        return;
                    }
                    shop.setItemTemplate(handItem.asOne());
                    player.sendMessage("§a[Shop #" + shop.getId() + "] Item wurde auf das Item in deiner Hand (§e"
                            + plugin.formatMaterialName(handItem.getType()) + "§a) inkl. aller NBT-Tags gesetzt!");
                }
                plugin.updateSign(shop);
                plugin.saveShops();
            }

            case "a", "amount" -> {
                if (args.length <= valueIndex) {
                    sender.sendMessage("§cBitte gib eine Anzahl (1-64) an.");
                    return;
                }
                try {
                    int amount = Integer.parseInt(args[valueIndex]);
                    if (amount < 1 || amount > 64) {
                        sender.sendMessage("§cBitte gib eine Zahl zwischen 1 und 64 an.");
                        return;
                    }
                    shop.setAmount(amount);
                    plugin.updateSign(shop);
                    plugin.saveShops();
                    sender.sendMessage("§a[Shop #" + shop.getId() + "] Handelsmenge auf §e" + amount + " §agesetzt.");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cUngültige Zahl: " + args[valueIndex]);
                }
            }

            case "b", "buy" -> {
                if (args.length <= valueIndex) {
                    sender.sendMessage("§cBitte gib einen Kaufpreis an.");
                    return;
                }
                try {
                    double price = Double.parseDouble(args[valueIndex].replace(",", "."));
                    if (price < 0) {
                        sender.sendMessage("§cDer Preis darf nicht negativ sein.");
                        return;
                    }
                    shop.setBuyPrice(price);
                    plugin.updateSign(shop);
                    plugin.saveShops();
                    sender.sendMessage("§a[Shop #" + shop.getId() + "] Kaufpreis (Buy) auf §e" + price + " §agesetzt.");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cUngültiger Preis: " + args[valueIndex]);
                }
            }

            case "s", "sell" -> {
                if (args.length <= valueIndex) {
                    sender.sendMessage("§cBitte gib einen Verkaufspreis an.");
                    return;
                }
                try {
                    double price = Double.parseDouble(args[valueIndex].replace(",", "."));
                    if (price < 0) {
                        sender.sendMessage("§cDer Preis darf nicht negativ sein.");
                        return;
                    }
                    shop.setSellPrice(price);
                    plugin.updateSign(shop);
                    plugin.saveShops();
                    sender.sendMessage("§a[Shop #" + shop.getId() + "] Verkaufspreis (Sell) auf §e" + price + " §agesetzt.");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cUngültiger Preis: " + args[valueIndex]);
                }
            }

            case "owner" -> {
                if (!sender.hasPermission("signshop.admin")) {
                    sender.sendMessage("§cNur Admins können den Besitzer eines Shops ändern.");
                    return;
                }
                if (args.length <= valueIndex) {
                    sender.sendMessage("§cBitte gib den neuen Spielernamen an.");
                    return;
                }
                String newOwnerName = args[valueIndex];
                OfflinePlayer newOwner = Bukkit.getOfflinePlayer(newOwnerName);
                shop.setOwner(newOwner.getUniqueId(), newOwner.getName() != null ? newOwner.getName() : newOwnerName);
                plugin.updateSign(shop);
                plugin.saveShops();
                sender.sendMessage("§a[Shop #" + shop.getId() + "] Neuer Besitzer ist nun §e" + shop.getOwnerName() + "§a.");
            }

            default -> sendHelp(sender);
        }
    }

    private void handleAdminCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendAdminHelp(sender);
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);

        if (action.equals("list")) {
            String filterOwner = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : null;
            sender.sendMessage("§8§m----------------------------------------");
            sender.sendMessage("§9§lRegistrierte SignShops:");
            int count = 0;
            for (ShopData shop : plugin.getAllShops()) {
                if (filterOwner != null && !shop.getOwnerName().toLowerCase(Locale.ROOT).contains(filterOwner)) {
                    continue;
                }
                Location loc = shop.getSignLocation();
                String itemName = shop.getItemTemplate() != null
                        ? plugin.formatMaterialName(shop.getItemTemplate().getType())
                        : "Kein Item";
                sender.sendMessage("§b#" + shop.getId() + " §8| §9" + shop.getOwnerName()
                        + " §8| §e" + shop.getAmount() + "x " + itemName
                        + " §8| §7" + loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
                count++;
            }
            if (count == 0) {
                sender.sendMessage("§7Keine passenden Shops gefunden.");
            }
            sender.sendMessage("§8§m----------------------------------------");
            return;
        }

        if (args.length < 3) {
            sendAdminHelp(sender);
            return;
        }

        int shopId;
        try {
            shopId = Integer.parseInt(args[2].replace("#", ""));
        } catch (NumberFormatException e) {
            sender.sendMessage("§cUngültige Shop-ID: §e" + args[2]);
            return;
        }

        ShopData shop = plugin.getShopById(shopId);
        if (shop == null) {
            sender.sendMessage("§cEs existiert kein Shop mit der ID §e#" + shopId + "§c.");
            return;
        }

        switch (action) {
            case "info", "i" -> plugin.sendShopInfo(sender, shop);

            case "open", "inv", "chest" -> {
                if (!(sender instanceof Player admin)) {
                    sender.sendMessage("§cNur Spieler können das Kisteninventar öffnen.");
                    return;
                }
                admin.openInventory(shop.getInventory());
                admin.sendMessage("§a[Admin] Remote-Zugriff auf Doppelkiste von Shop §b#" + shop.getId() + " §7(Besitzer: §f" + shop.getOwnerName() + "§7).");
            }

            case "tp", "teleport" -> {
                if (!(sender instanceof Player admin)) {
                    sender.sendMessage("§cNur Spieler können sich teleportieren.");
                    return;
                }
                Location targetLoc = shop.getSignLocation().clone().add(0.5, 0.0, 0.5);
                admin.teleport(targetLoc);
                admin.sendMessage("§a[Admin] Teleportiert zu Shop §b#" + shop.getId() + " §7von §f" + shop.getOwnerName() + "§a.");
            }

            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cVerwendung: /shop admin set <ID> <item|amount|buy|sell|owner> [Wert]");
                    return;
                }
                String setting = args[3].toLowerCase(Locale.ROOT);
                applyShopSetting(sender, shop, setting, args, 4);
            }

            case "delete", "remove" -> {
                String mode = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "drop";
                Location signLoc = shop.getSignLocation();

                if (mode.equals("here") && sender instanceof Player admin) {
                    Location adminLoc = admin.getLocation();
                    for (ItemStack item : shop.getInventory().getContents()) {
                        if (item != null && !item.getType().isAir()) {
                            admin.getWorld().dropItemNaturally(adminLoc, item);
                        }
                    }
                    sender.sendMessage("§a[Admin] Kisteninhalt von Shop §b#" + shop.getId() + " §awurde bei dir gedroppt.");
                } else if (mode.equals("clear")) {
                    sender.sendMessage("§a[Admin] Kisteninhalt von Shop §b#" + shop.getId() + " §awurde gelöscht.");
                } else {
                    Location dropLoc = signLoc.clone().add(0.5, 0.5, 0.5);
                    for (ItemStack item : shop.getInventory().getContents()) {
                        if (item != null && !item.getType().isAir()) {
                            signLoc.getWorld().dropItemNaturally(dropLoc, item);
                        }
                    }
                    sender.sendMessage("§a[Admin] Kisteninhalt von Shop §b#" + shop.getId() + " §awurde an der Schild-Position gedroppt.");
                }

                shop.getInventory().clear();
                Block signBlock = signLoc.getBlock();
                if (signBlock.getState() instanceof org.bukkit.block.Sign) {
                    signBlock.breakNaturally();
                }

                plugin.removeShop(shop);
                sender.sendMessage("§a[Admin] Shop §b#" + shop.getId() + " §7(Besitzer: §f" + shop.getOwnerName() + "§7) wurde vollständig entfernt.");
            }

            default -> sendAdminHelp(sender);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§9§lSignShop Befehle:");
        sender.sendMessage("§e/shop set item [ID] §8- §7Setzt das Item (Hand-Item inkl. NBT oder per ID)");
        sender.sendMessage("§e/shop set a|amount <1-64> §8- §7Setzt die Stückzahl pro Klick");
        sender.sendMessage("§e/shop set b|buy <Preis> §8- §7Setzt den Kaufpreis (Kunde kauft)");
        sender.sendMessage("§e/shop set s|sell <Preis> §8- §7Setzt den Verkaufspreis (Kunde verkauft)");
        sender.sendMessage("§e/shop set i|info §8- §7Zeigt Shop-ID, Bestand & Item-Details (Anschauen oder Klicken)");
        if (sender.hasPermission("signshop.admin")) {
            sender.sendMessage("§c/shop admin ... §8- §7Admin-Befehle zur Remote-Verwaltung per Shop-ID");
        }
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage("§c§lSignShop Admin-Verwaltung (Remote per ID):");
        sender.sendMessage("§e/shop admin list [Spieler] §8- §7Listet alle Shops samt ID & Koordinaten auf");
        sender.sendMessage("§e/shop admin info <ID> §8- §7Zeigt alle Details & Lagerbestand von Shop #ID");
        sender.sendMessage("§e/shop admin open <ID> §8- §7Öffnet die Doppelkiste von Shop #ID aus der Ferne");
        sender.sendMessage("§e/shop admin tp <ID> §8- §7Teleportiert dich direkt zu Shop #ID");
        sender.sendMessage("§e/shop admin set <ID> <item|amount|buy|sell|owner> [Wert] §8- §7Ändert Einstellungen remote");
        sender.sendMessage("§e/shop admin delete <ID> [drop|here|clear] §8- §7Löscht Shop #ID aus der Ferne");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> root = new ArrayList<>();
            root.add("set");
            if (sender.hasPermission("signshop.admin")) {
                root.add("admin");
            }
            return filter(root, args[0]);
        }

        if (args[0].equalsIgnoreCase("set")) {
            if (args.length == 2) {
                return filter(List.of("item", "a", "amount", "b", "buy", "s", "sell", "i", "info"), args[1]);
            }
            if (args.length == 3) {
                String sub = args[1].toLowerCase(Locale.ROOT);
                if (sub.equals("a") || sub.equals("amount")) {
                    return filter(List.of("1", "8", "16", "32", "64"), args[2]);
                }
                if (sub.equals("b") || sub.equals("buy") || sub.equals("s") || sub.equals("sell")) {
                    return filter(List.of("10", "50", "100", "500", "1000"), args[2]);
                }
            }
            return List.of();
        }

        if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("signshop.admin")) {
            if (args.length == 2) {
                return filter(List.of("list", "info", "open", "tp", "set", "delete"), args[1]);
            }
            if (args.length == 3 && !args[1].equalsIgnoreCase("list")) {
                List<String> ids = new ArrayList<>();
                for (ShopData shop : plugin.getAllShops()) {
                    ids.add(String.valueOf(shop.getId()));
                }
                return filter(ids, args[2]);
            }
            if (args.length == 4) {
                if (args[1].equalsIgnoreCase("set")) {
                    return filter(List.of("item", "amount", "buy", "sell", "owner"), args[3]);
                }
                if (args[1].equalsIgnoreCase("delete") || args[1].equalsIgnoreCase("remove")) {
                    return filter(List.of("drop", "here", "clear"), args[3]);
                }
            }
        }

        return List.of();
    }

    private List<String> filter(List<String> list, String prefix) {
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                result.add(s);
            }
        }
        return result;
    }
}
