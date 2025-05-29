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

public class AntiFarmDropPlugin extends JavaPlugin implements Listener  {

    private final Map<UUID, Double> lastFallY = new HashMap<>();
    private final Map<UUID, Long> fallStartTime = new HashMap<>();
    private final Map<UUID, Long> lastPlayerAttackTime = new HashMap<>();
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();
    private final Set<UUID> suspicious = new HashSet<>();

    private double minFallHeight;
    private long maxTrackTime;
    private Set<EntityType> excludedTypes;
    private Set<EntityDamageEvent.DamageCause> blockedCauses;
    private boolean logBlocked;

    private static AntiFarmDropPlugin instance;

    public static AntiFarmDropPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadConfig();

        Bukkit.getPluginManager().registerEvents(this, this);
        new FallTracker().runTaskTimer(this, 1L, 2L);
        getLogger().info("✅ AntiFarmDrop активен.");
    }

    @Override
    public void onDisable() {
        instance = null;
        lastFallY.clear();
        fallStartTime.clear();
        lastPlayerAttackTime.clear();
        lastAttacker.clear();
        suspicious.clear();
        getLogger().info("❌ AntiFarmDrop отключен.");
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        minFallHeight = config.getDouble("min-fall-height", 2.0);
        maxTrackTime = config.getLong("max-track-time", 15000L);
        logBlocked = config.getBoolean("log-blocked-drops", false);
        blockedCauses = loadEnumSet(config.getStringList("blocked-damage-causes"), EntityDamageEvent.DamageCause.class, "DamageCause");
        excludedTypes = loadEnumSet(config.getStringList("excluded-entities"), EntityType.class, "EntityType");
    }

    private <E extends Enum<E>> Set<E> loadEnumSet(List<String> names, Class<E> enumClass, String label) {
        Set<E> result = EnumSet.noneOf(enumClass);
        for (String name : names) {
            try {
                result.add(Enum.valueOf(enumClass, name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                getLogger().warning("⚠️ Неизвестный " + label + ": " + name);
            }
        }
        return result;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (excludedTypes.contains(entity.getType())) return;
        if (event instanceof EntityDamageByEntityEvent) return;
        if (!blockedCauses.contains(event.getCause())) return;
        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        double maxHealth = attr.getValue();
        double healthAfter = entity.getHealth() - event.getFinalDamage();
        if (healthAfter > 0 && healthAfter <= maxHealth * 0.25) {
            suspicious.add(entity.getUniqueId());
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (excludedTypes.contains(target.getType())) return;
        Entity damager = event.getDamager();
        if (damager instanceof Player player) {
            UUID targetId = target.getUniqueId();
            lastPlayerAttackTime.put(targetId, System.currentTimeMillis());
            lastAttacker.put(targetId, player.getUniqueId());
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || !entity.isValid()) return;
        Location deathLocation = entity.getLocation();
        if (deathLocation == null || deathLocation.getWorld() == null) return;
        UUID entityId = entity.getUniqueId();
        boolean wasSuspicious = suspicious.remove(entityId);
        Long fallStart = fallStartTime.remove(entityId);
        Long lastAttack = lastPlayerAttackTime.remove(entityId);
        Double fallStartY = lastFallY.remove(entityId);
        UUID attackerId = lastAttacker.remove(entityId);
        double deathY = deathLocation.getY();
        double fallHeight = (fallStartY != null && deathY != 0) ? (fallStartY - deathY) : 0;
        boolean fellFar = fallHeight >= minFallHeight;
        boolean allowDrop = true;
        if (fellFar || wasSuspicious) {
            allowDrop = (fallStart != null && lastAttack != null && lastAttack < fallStart);
        }
        if (!allowDrop) {
            DropBlockedEvent.BlockReason reason = fellFar ?
                    DropBlockedEvent.BlockReason.FALL :
                    DropBlockedEvent.BlockReason.SUSPICIOUS_DAMAGE;
            OfflinePlayer attacker = (attackerId != null) ? Bukkit.getOfflinePlayer(attackerId) : null;
            DropBlockedEvent dropEvent = new DropBlockedEvent(entity, reason, attacker);
            Bukkit.getPluginManager().callEvent(dropEvent);
            if (dropEvent.isCancelled()) return;
            event.getDrops().clear();
            event.setDroppedExp(0);
            if (logBlocked) {
                getLogger().warning(String.format(
                        "⛔ Дроп заблокирован у %s (высота: %.2f, упал: %s, подозрительный: %s)",
                        entity.getType(), fallHeight, fellFar, wasSuspicious
                ));
                if (attackerId != null) {
                    String name = (attacker != null && attacker.getName() != null) ? attacker.getName() : "Неизвестно";
                    getLogger().info("📛 Последний атакующий игрок: " + name + " (" + attackerId + ")");
                }
            }
        }
    }

    private class FallTracker extends BukkitRunnable {
        @Override
        public void run() {
            final long now = System.currentTimeMillis();
            for (World world : Bukkit.getWorlds()) {
                if (world == null || !world.isChunkLoaded(world.getSpawnLocation().getBlockX() >> 4, world.getSpawnLocation().getBlockZ() >> 4)) continue;
                for (LivingEntity entity : world.getLivingEntities()) {
                    if (!shouldTrack(entity)) continue;
                    UUID uuid = entity.getUniqueId();
                    if (entity.getFallDistance() > 0 && !lastFallY.containsKey(uuid)) {
                        double y = safeGetY(entity);
                        if (y != -999) {
                            lastFallY.put(uuid, y);
                            fallStartTime.put(uuid, now);
                        } else if (logBlocked) {
                            getLogger().warning("⚠️ Не удалось получить Y-координату падения у " + entity.getType() + " (" + uuid + ")");
                        }
                    }
                }
            }
            Bukkit.getScheduler().runTaskAsynchronously(AntiFarmDropPlugin.this, this::cleanupExpiredEntries);
        }
        private boolean shouldTrack(LivingEntity entity) {
            return entity != null &&
                    entity.isValid() &&
                    !entity.isDead() &&
                    entity instanceof Mob &&
                    !excludedTypes.contains(entity.getType());
        }
        private double safeGetY(LivingEntity entity) {
            try {
                Location loc = entity.getLocation();
                if (loc.getWorld() == null) return -999;
                return loc.getY();
            } catch (Exception e) {
                return -999;
            }
        }
        private void cleanupExpiredEntries() {
            final long threshold = System.currentTimeMillis() - maxTrackTime;
            fallStartTime.entrySet().removeIf(entry -> entry.getValue() < threshold);
            lastFallY.keySet().removeIf(uuid -> !fallStartTime.containsKey(uuid));
            lastPlayerAttackTime.entrySet().removeIf(entry -> entry.getValue() < threshold);
            lastAttacker.keySet().removeIf(uuid -> !lastPlayerAttackTime.containsKey(uuid));
        }
    }

    public static boolean isDropAllowed(Entity entity) {
        return !AntiFarmDropPlugin.getInstance().suspicious.contains(entity.getUniqueId());
    }

}