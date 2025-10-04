package com.github.usontong.glower;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Glower extends JavaPlugin {
    private final Map<UUID, Map<UUID, BukkitTask>> activeTasks = new ConcurrentHashMap<>();
    private static final Map<String, ChatColor> COLOR_MAP = new HashMap<>();

    //初始化颜色
    static {
        COLOR_MAP.put("black", ChatColor.BLACK);
        COLOR_MAP.put("dark_blue", ChatColor.DARK_BLUE);
        COLOR_MAP.put("dark_green", ChatColor.DARK_GREEN);
        COLOR_MAP.put("dark_aqua", ChatColor.DARK_AQUA);
        COLOR_MAP.put("dark_red", ChatColor.DARK_RED);
        COLOR_MAP.put("dark_purple", ChatColor.DARK_PURPLE);
        COLOR_MAP.put("gold", ChatColor.GOLD);
        COLOR_MAP.put("gray", ChatColor.GRAY);
        COLOR_MAP.put("dark_gray", ChatColor.DARK_GRAY);
        COLOR_MAP.put("blue", ChatColor.BLUE);
        COLOR_MAP.put("green", ChatColor.GREEN);
        COLOR_MAP.put("aqua", ChatColor.AQUA);
        COLOR_MAP.put("red", ChatColor.RED);
        COLOR_MAP.put("light_purple", ChatColor.LIGHT_PURPLE);
        COLOR_MAP.put("yellow", ChatColor.YELLOW);
        COLOR_MAP.put("white", ChatColor.WHITE);
    }

    @Override
    public void onEnable() {
        if (!getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            getLogger().info("请安装ProtocolLib!");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // 取消所有任务
        activeTasks.values().forEach(targetMap ->
                targetMap.values().forEach(BukkitTask::cancel));
        activeTasks.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.isOp()) {
            return true;
        }

        if (args.length < 1) {
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "effect" -> handleEffectCommand(sender, args);
            case "cancel" -> handleCancelCommand(sender, args);
            case "cancelall" -> handleCancelAllCommand(sender, args);
            default -> true;
        };
    }

    //effect
    private boolean handleEffectCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            return true;
        }

        Player observer = getServer().getPlayer(args[1]);
        Player target = getServer().getPlayer(args[2]);
        ChatColor color = parseColor(args[3]);
        int ticks = parseTicks(args.length > 4 ? args[4] : null);

        if (validatePlayers(observer, target) && color != null) {
            setGlowing(observer, target, true, color);
            if (ticks > 0) {
                scheduleCancel(observer, target, ticks);
            }
        }
        return true;
    }

    //cancel
    private boolean handleCancelCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return true;
        }

        Player observer = getServer().getPlayer(args[1]);
        Player target = getServer().getPlayer(args[2]);

        if (validatePlayers(observer, target)) {
            cancelExistingTask(observer.getUniqueId(), target.getUniqueId());
            setGlowing(observer, target, false, null);
        }
        return true;
    }

    //cancelall
    private boolean handleCancelAllCommand(CommandSender sender, String[] args) {
        Player observer = (sender instanceof Player) ? (Player)sender : null;
        if (args.length >= 2) {
            observer = getServer().getPlayer(args[1]);
        }

        if (observer == null) {
            return true;
        }

        for (Player target : Bukkit.getOnlinePlayers()) {
            setGlowing(observer, target, false, null);
        }
        return true;
    }

    private void scheduleCancel(Player observer, Player target, int ticks) {
        UUID observerId = observer.getUniqueId();
        UUID targetId = target.getUniqueId();

        // 先取消同一观察者对同一目标的旧任务
        cancelExistingTask(observerId, targetId);

        final BukkitTask[] taskHolder = new BukkitTask[1];

        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            setGlowing(observer, target, false, null);
            removeTask(observerId, targetId, taskHolder[0]);
        }, ticks);

        taskHolder[0] = task;
        addTask(observerId, targetId, task);
    }

    //取消现有任务
    private void cancelExistingTask(UUID observerId, UUID targetId) {
        Map<UUID, BukkitTask> targetTasks = activeTasks.get(observerId);
        if (targetTasks != null) {
            BukkitTask oldTask = targetTasks.remove(targetId);
            if (oldTask != null) {
                oldTask.cancel();
            }
            if (targetTasks.isEmpty()) {
                activeTasks.remove(observerId);
            }
        }
    }

    //添加任务
    private void addTask(UUID observerId, UUID targetId, BukkitTask task) {
        activeTasks.computeIfAbsent(observerId, k -> new ConcurrentHashMap<>())
                .put(targetId, task);
    }

    //解析颜色
    private ChatColor parseColor(String colorStr) {
        if (colorStr == null) {
            return null;
        }
        return COLOR_MAP.get(colorStr.toLowerCase());
    }

    private int parseTicks(String ticksStr) {
        if (ticksStr == null || ticksStr.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(ticksStr));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    //移除任务
    private void removeTask(UUID observerId, UUID targetId, BukkitTask task) {
        Map<UUID, BukkitTask> targetTasks = activeTasks.get(observerId);
        if (targetTasks != null) {
            // 确保只移除指定的任务
            BukkitTask storedTask = targetTasks.get(targetId);
            if (storedTask != null && storedTask.equals(task)) {
                targetTasks.remove(targetId);
                if (targetTasks.isEmpty()) {
                    activeTasks.remove(observerId);
                }
            }
        }
    }

    //验证玩家是否存在
    private boolean validatePlayers(Player observer, Player target) {
        return observer != null && target != null && observer.isOnline() && target.isOnline();
    }

    //设置发光
    private boolean setGlowing(Player observer, Player target, boolean glowing, ChatColor color) {
        try {
            if (observer == null || target == null || !observer.isOnline() || !target.isValid()) {
                return false;
            }

            PacketContainer packet = ProtocolLibrary.getProtocolManager()
                    .createPacket(PacketType.Play.Server.ENTITY_METADATA);

            packet.getIntegers().write(0, target.getEntityId());

            WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.get(Byte.class);
            byte entityBitMask = (byte) (glowing ? 0x40 : 0);

            List<WrappedDataValue> wrappedDataValueList = Collections.singletonList(
                    new WrappedDataValue(0, serializer, entityBitMask)
            );

            packet.getDataValueCollectionModifier().write(0, wrappedDataValueList);
            ProtocolLibrary.getProtocolManager().sendServerPacket(observer, packet);

            // 设置颜色（通过团队系统实现）
            if (glowing && color != null) {
                String teamName = "glower_" + observer.getUniqueId().toString().substring(0, 8);
                observer.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
                observer.getScoreboard().registerNewTeam(teamName);
                observer.getScoreboard().getTeam(teamName).setColor(color);
                observer.getScoreboard().getTeam(teamName).addEntry(target.getName());
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }
}
