package com.intellyspark.conditionalidlekick;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public final class ConditionalIdleKick extends JavaPlugin implements Listener {

    enum KickCriteria {
        GENERAL,
        SERVER_FULL
    }

    private Essentials essentials;
    private final Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");

    private long maxIdleTime;
    private int kickPlayerCount;

    private double kickTps;
    private int kickNumber;
    private TextComponent kickMsg;

    private boolean kickFull;
    private TextComponent kickFullMsg;

    private boolean checkKickConditions() {
        return Bukkit.getOnlinePlayers().size() > kickPlayerCount || essentials.getTimer().getAverageTPS() < kickTps;
    }

    private void runKickSelector(KickCriteria kc) {
        long currentTime = System.currentTimeMillis();
        Map<Long, User> userIdleTimes = new TreeMap<>(Collections.reverseOrder());
        essentials.getOnlineUsers().forEach((user) -> {
            if (user.isAfk()) {
                if ((currentTime - user.getAfkSince()) > maxIdleTime) {
                    userIdleTimes.put(currentTime - user.getAfkSince(), user);
                }
            }
        });
        Iterator<Map.Entry<Long, User>> it = userIdleTimes.entrySet().iterator();
        int i = 0;
        while (it.hasNext() && i < kickNumber) {
            Map.Entry<Long, User> entry = it.next();
            if (kc == KickCriteria.SERVER_FULL) {
                entry.getValue().getBase().kick(kickFullMsg);
                getLogger().info("Kicking " + entry.getValue().getName() + " because server is full.");
            } else {
                assert plugin != null;
                Bukkit.getScheduler().runTask(plugin, () -> entry.getValue().getBase().kick(kickMsg));
                getLogger().info("Kicking " + entry.getValue().getName() + " because server is busy.");
            }
            i++;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        maxIdleTime = getConfig().getInt("max-idle-time");
        kickPlayerCount = getConfig().getInt("kick-player-count");
        int kickUpdatePeriod = getConfig().getInt("kick-update-period");

        kickTps = getConfig().getDouble("kick-tps");
        kickNumber = getConfig().getInt("kick-number");
        kickNumber = kickNumber < 0 ? Integer.MAX_VALUE : kickNumber;
        kickMsg = Component.text(Objects.requireNonNull(getConfig().getString("kick-message")))
                .color(TextColor.color(getConfig().getInt("kick-message-color")));
        getLogger().info("Conditional kick message is set to: " + kickMsg);

        kickFull = getConfig().getBoolean("kick-afk-players-when-server-is-full");
        kickFullMsg = Component.text(Objects.requireNonNull(getConfig().getString("kick-message-server-full")))
                .color(TextColor.color(getConfig().getInt("kick-message-server-full-color")));
        getLogger().info("Server full kick message is set to: " + kickFullMsg);

        if (plugin == null) {
            getLogger().severe("Must have EssentialsX to run ConditionalIdleKick plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        essentials = (Essentials) plugin;

        getLogger().info("ConditionalIdleKick is loaded: Checking every " + kickUpdatePeriod
                + " ticks to kick AFK players if idle time is more than " + maxIdleTime / 1000
                + " seconds, when player count more than " + kickPlayerCount + " or server TPS less than " + kickTps);

        Bukkit.getPluginManager().registerEvents(this, this);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (checkKickConditions())
                    runKickSelector(KickCriteria.GENERAL);
            }
        }.runTaskTimerAsynchronously(plugin, kickUpdatePeriod, kickUpdatePeriod);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent e) {
        if (checkKickConditions())
            runKickSelector(KickCriteria.GENERAL);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(AsyncPlayerPreLoginEvent e) {
        if (kickFull && Bukkit.getOnlinePlayers().size() != Bukkit.getMaxPlayers()) {
            return; // Kick when full is false or server is not full
        }
        runKickSelector(KickCriteria.SERVER_FULL);
        if (e.getLoginResult() == AsyncPlayerPreLoginEvent.Result.KICK_FULL
                && Bukkit.getOnlinePlayers().size() != Bukkit.getMaxPlayers()) {
            e.setLoginResult(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        }
    }
}
