package com.tizem.antilag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AntiLagPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private BukkitTask task;
    private int secondsLeft;
    private int interval;
    private Set<Integer> warnings = new HashSet<>();
    private Set<Material> excluded = new HashSet<>();
    private List<String> worlds = new ArrayList<>();
    private String prefix;
    private String arrow;
    private TextColor prefixColor;
    private TextColor accentColor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("antilag").setExecutor(this);
        getCommand("antilag").setTabCompleter(this);
        load();
        start();
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
    }

    private void load() {
        reloadConfig();
        interval = Math.max(10, getConfig().getInt("interval", 300));
        warnings.clear();
        warnings.addAll(getConfig().getIntegerList("warnings"));
        excluded.clear();
        for (String s : getConfig().getStringList("exclude-materials")) {
            Material m = Material.matchMaterial(s);
            if (m != null) excluded.add(m);
        }
        worlds = getConfig().getStringList("worlds");
        prefix = getConfig().getString("prefix", "ANTILAG");
        arrow = getConfig().getString("arrow", "▸");
        prefixColor = parse(getConfig().getString("prefix-color", "#FF1F4B"));
        accentColor = parse(getConfig().getString("accent-color", "#FF1F4B"));
    }

    private TextColor parse(String hex) {
        TextColor c = TextColor.fromHexString(hex);
        return c != null ? c : NamedTextColor.RED;
    }

    private void start() {
        if (task != null) task.cancel();
        secondsLeft = interval;
        task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            secondsLeft--;
            if (secondsLeft <= 0) {
                int removed = clear();
                broadcast(Component.text("Cleared ", NamedTextColor.WHITE)
                        .append(Component.text(removed + " items", accentColor))
                        .append(Component.text("!", NamedTextColor.WHITE)));
                secondsLeft = interval;
            } else if (warnings.contains(secondsLeft)) {
                String unit = secondsLeft == 1 ? " second" : " seconds";
                broadcast(Component.text("Items will clear in ", NamedTextColor.WHITE)
                        .append(Component.text(secondsLeft + unit, accentColor))
                        .append(Component.text("!", NamedTextColor.WHITE)));
            }
        }, 20L, 20L);
    }

    private int clear() {
        int count = 0;
        for (World w : Bukkit.getWorlds()) {
            if (!worlds.isEmpty() && !worlds.contains(w.getName())) continue;
            for (Entity e : w.getEntitiesByClass(Item.class)) {
                Item item = (Item) e;
                if (excluded.contains(item.getItemStack().getType())) continue;
                item.remove();
                count++;
            }
        }
        return count;
    }

    private Component line(Component body) {
        return Component.text(prefix, prefixColor, TextDecoration.BOLD)
                .append(Component.text(" " + arrow + " ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(body.decoration(TextDecoration.BOLD, false));
    }

    private void broadcast(Component body) {
        Bukkit.broadcast(line(body));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(line(Component.text("/antilag <clear|reload>", NamedTextColor.WHITE)));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "clear" -> {
                int removed = clear();
                broadcast(Component.text("Cleared ", NamedTextColor.WHITE)
                        .append(Component.text(removed + " items", accentColor))
                        .append(Component.text("!", NamedTextColor.WHITE)));
                secondsLeft = interval;
            }
            case "reload" -> {
                load();
                start();
                sender.sendMessage(line(Component.text("Config ", NamedTextColor.WHITE)
                        .append(Component.text("reloaded", accentColor))
                        .append(Component.text("!", NamedTextColor.WHITE))));
            }
            default -> sender.sendMessage(line(Component.text("/antilag <clear|reload>", NamedTextColor.WHITE)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return List.of("clear", "reload");
        return List.of();
    }
}
