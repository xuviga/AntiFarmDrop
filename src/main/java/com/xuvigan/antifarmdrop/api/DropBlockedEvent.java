package com.xuvigan.antifarmdrop.api;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DropBlockedEvent extends Event implements Cancellable {

    public enum BlockReason {
        FALL, SUSPICIOUS_DAMAGE
    }

    private static final HandlerList handlers = new HandlerList();

    private final LivingEntity entity;
    private final BlockReason reason;
    private final OfflinePlayer attacker;
    private boolean cancelled;

    public DropBlockedEvent(LivingEntity entity, BlockReason reason, OfflinePlayer attacker) {
        this.entity = entity;
        this.reason = reason;
        this.attacker = attacker;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public BlockReason getReason() {
        return reason;
    }

    public OfflinePlayer getAttacker() {
        return attacker;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
