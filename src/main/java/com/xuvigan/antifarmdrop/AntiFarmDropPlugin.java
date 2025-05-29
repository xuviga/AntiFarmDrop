package com.xuvigan.antifarmdrop;

import com.xuvigan.antifarmdrop.api.DropBlockedEvent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class AntiFarmDropPlugin extends JavaPlugin implements Listener {

    private static AntiFarmDropPlugin instance;
    public static AntiFarmDropPlugin getInstance() {
        return instance;
    }

    private final Map<UUID, Double> fallStartY = new HashMap<>();
    private final Map<UUID, Long> fallStartTime = new HashMap<>();
    private final Map<UUID, Long> lastPlayerHit = new HashMap<>();
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();
    private final Set<UUID> suspicious = new HashSet<>();

    private double minFallHeight;
    private long maxTrackTime;
    private Set<EntityType> excludedEntities;
    private Set<EntityDamageEvent.DamageCause> blockedCauses;
    private boolean logBlocked;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
        new FallTracker().runTaskTimer(this, 2L, 2L);
        getLogger().info("✅ AntiFarmDrop запущен.");
    }

    @Override
    public void onDisable() {
        instance = null;
        suspicious.clear();
        fallStartY.clear();
        fallStartTime.clear();
        lastPlayerHit.clear();
        lastAttacker.clear();
        getLogger().info("❌ AntiFarmDrop отключен.");
    }

    private void loadSettings() {
        FileConfiguration cfg = getConfig();
        minFallHeight = cfg.getDouble("min-fall-height", 2.0);
        maxTrackTime = cfg.getLong("max-track-time", 15000L);
        logBlocked = cfg.getBoolean("log-blocked-drops", true);
        blockedCauses = parseEnumList(cfg.getStringList("blocked-damage-causes"), EntityDamageEvent.DamageCause.class);
        excludedEntities = parseEnumList(cfg.getStringList("excluded-entities"), EntityType.class);
    }

    private <T extends Enum<T>> Set<T> parseEnumList(List<String> list, Class<T> clazz) {
        Set<T> result = EnumSet.noneOf(clazz);
        for (String val : list) {
            try {
                result.add(Enum.valueOf(clazz, val.toUpperCase()));
            } catch (IllegalArgumentException e) {
                getLogger().warning("⚠️ Неизвестный тип: " + val);
            }
        }
        return result;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity entity)) return;
        if (excludedEntities.contains(entity.getType())) return;
        if (e instanceof EntityDamageByEntityEvent) return;
        if (!blockedCauses.contains(e.getCause())) return;

        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        double remaining = entity.getHealth() - e.getFinalDamage();
        if (remaining > 0 && remaining <= attr.getValue() * 0.25) {
            suspicious.add(entity.getUniqueId());
        }
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity entity)) return;
        if (excludedEntities.contains(entity.getType())) return;
        if (e.getDamager() instanceof Player player) {
            UUID id = entity.getUniqueId();
            lastPlayerHit.put(id, System.currentTimeMillis());
            lastAttacker.put(id, player.getUniqueId());
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity entity = e.getEntity();
        UUID id = entity.getUniqueId();

        Double yStart = fallStartY.remove(id);
        Long fallTime = fallStartTime.remove(id);
        Long hitTime = lastPlayerHit.remove(id);
        UUID attackerId = lastAttacker.remove(id);
        boolean isSuspicious = suspicious.remove(id);

        double fallHeight = yStart != null ? yStart - entity.getLocation().getY() : 0;
        boolean fellFar = fallHeight >= minFallHeight;

        boolean allow = true;
        if (fellFar || isSuspicious) {
            allow = hitTime != null && fallTime != null && hitTime < fallTime;
        }

        if (!allow) {
            DropBlockedEvent.BlockReason reason = fellFar ? DropBlockedEvent.BlockReason.FALL : DropBlockedEvent.BlockReason.SUSPICIOUS_DAMAGE;
            DropBlockedEvent event = new DropBlockedEvent(entity, reason, attackerId != null ? Bukkit.getOfflinePlayer(attackerId) : null);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                e.getDrops().clear();
                e.setDroppedExp(0);
                if (logBlocked) {
                    getLogger().warning("⛔ Дроп заблокирован: " + entity.getType() +
                            " (высота: " + String.format("%.2f", fallHeight) +
                            ", упал: " + fellFar + ", подозрительный: " + isSuspicious + ")");
                }
            }
        }
    }

    private class FallTracker extends BukkitRunnable {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            for (World world : Bukkit.getWorlds()) {
                for (LivingEntity entity : world.getLivingEntities()) {
                    if (!(entity instanceof Mob) || entity.isDead() || excludedEntities.contains(entity.getType())) continue;
                    UUID id = entity.getUniqueId();
                    if (entity.getFallDistance() > 0 && !fallStartY.containsKey(id)) {
                        fallStartY.put(id, entity.getLocation().getY());
                        fallStartTime.put(id, now);
                    }
                }
            }
            Bukkit.getScheduler().runTaskAsynchronously(AntiFarmDropPlugin.this, () -> {
                long expiry = System.currentTimeMillis() - maxTrackTime;
                fallStartTime.entrySet().removeIf(e -> e.getValue() < expiry);
                fallStartY.keySet().removeIf(id -> !fallStartTime.containsKey(id));
                lastPlayerHit.entrySet().removeIf(e -> e.getValue() < expiry);
                lastAttacker.keySet().removeIf(id -> !lastPlayerHit.containsKey(id));
            });
        }
    }

    public static boolean isDropAllowed(Entity entity) {
        return !getInstance().suspicious.contains(entity.getUniqueId());
    }
}
