// SPDX-License-Identifier: GPL-3.0-or-later
package com.eaglercraftranked.enhancements.mechanics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import com.eaglercraftranked.enhancements.Feature;
import com.eaglercraftranked.enhancements.RankedEnhancements;

import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

/** shield-stun and easy-stun-slam: both start when an axe disables a shield. */
public final class ShieldStun implements Listener {

	private static final long SLAM_WINDOW_MS = 400L;
	// the part of the blocked hit's hurt cooldown that would still swallow a full hit
	private static final int STUN_TICKS = 10;

	private record Disabled(UUID by, int tick) {
	}

	private record Slam(UUID victim, long at, float fallDistance) {
	}

	private final RankedEnhancements plugin;
	private final Map<UUID, Disabled> disabled = new HashMap<>();
	private final Map<UUID, Slam> slams = new HashMap<>();

	public ShieldStun(RankedEnhancements plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		UUID u = e.getPlayer().getUniqueId();
		disabled.remove(u);
		slams.remove(u);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onShieldDisable(PlayerShieldDisableEvent e) {
		if (!(e.getDamager() instanceof Player attacker)) {
			return;
		}
		Player victim = e.getPlayer();
		if (plugin.features().on(Feature.EASY_STUN_SLAM, victim.getWorld())) {
			slams.put(attacker.getUniqueId(), new Slam(victim.getUniqueId(), System.currentTimeMillis(), attacker.getFallDistance()));
		}
		if (plugin.features().on(Feature.SHIELD_STUN, victim.getWorld())) {
			disabled.put(victim.getUniqueId(), new Disabled(attacker.getUniqueId(), Bukkit.getCurrentTick()));
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPreAttack(PrePlayerAttackEntityEvent e) {
		if (!(e.getAttacked() instanceof LivingEntity victim)) {
			return;
		}
		Player attacker = e.getPlayer();
		if (!disabled.isEmpty()) {
			Disabled d = disabled.get(victim.getUniqueId());
			int now = Bukkit.getCurrentTick();
			if (d != null && (now - d.tick > STUN_TICKS || d.by.equals(attacker.getUniqueId()))) {
				disabled.remove(victim.getUniqueId());
				if (now - d.tick <= STUN_TICKS) {
					victim.setNoDamageTicks(0);
				}
			}
		}
		Slam s = slams.get(attacker.getUniqueId());
		if (s == null || System.currentTimeMillis() - s.at > SLAM_WINDOW_MS || !victim.getUniqueId().equals(s.victim)
				|| attacker.getInventory().getItemInMainHand().getType() != Material.MACE) {
			return;
		}
		slams.remove(attacker.getUniqueId());
		attacker.setFallDistance(Math.max(attacker.getFallDistance(), s.fallDistance));
		victim.setNoDamageTicks(0);
	}

}
