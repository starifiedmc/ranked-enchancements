// SPDX-License-Identifier: GPL-3.0-or-later
package com.eaglercraftranked.enhancements.mechanics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.eaglercraftranked.enhancements.Feature;
import com.eaglercraftranked.enhancements.RankedEnhancements;

public final class WindChargeCatch implements Listener {

	private static final double MIN_FALL_VELOCITY = -0.25;
	private static final double MIN_PITCH_DOWN = 55.0;
	private static final double MIN_GROUND_DISTANCE = 10.0;
	private static final long COOLDOWN_MS = 400L;
	private static final double VERTICAL_BRAKE = 0.25, MIN_UPWARD = 0.12, MAX_UPWARD = 0.45, HORIZONTAL = 0.85;

	private final RankedEnhancements plugin;
	private final Map<UUID, Long> cooldown = new HashMap<>();

	public WindChargeCatch(RankedEnhancements plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		cooldown.remove(e.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onLaunch(PlayerLaunchProjectileEvent e) {
		if (!(e.getProjectile() instanceof WindCharge charge) || e.getItemStack().getType() != Material.WIND_CHARGE) {
			return;
		}
		Player p = e.getPlayer();
		if (!plugin.features().on(Feature.WIND_CHARGE_CATCH, p.getWorld()) || !canCatch(p)) {
			return;
		}
		Vector v = p.getVelocity();
		double y = Math.min(Math.max(v.getY() * VERTICAL_BRAKE, MIN_UPWARD), MAX_UPWARD);
		p.setVelocity(new Vector(v.getX() * HORIZONTAL, y, v.getZ() * HORIZONTAL));
		p.setFallDistance(0.0f);
		p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 0.67f);
		cooldown.put(p.getUniqueId(), System.currentTimeMillis() + COOLDOWN_MS);
		// the charge never flies, so it adds no knockback of its own
		e.setShouldConsume(false);
		e.setCancelled(true);
		charge.remove();
		consumeOne(p, e.getItemStack());
	}

	private boolean canCatch(Player p) {
		if (standingOnBlock(p) || withinGroundDistance(p, MIN_GROUND_DISTANCE)) {
			return false;
		}
		double vy = p.getVelocity().getY();
		if (vy >= 0.0 || vy > MIN_FALL_VELOCITY || p.getLocation().getPitch() < MIN_PITCH_DOWN) {
			return false;
		}
		Long until = cooldown.get(p.getUniqueId());
		return until == null || until <= System.currentTimeMillis();
	}

	private static void consumeOne(Player p, ItemStack launched) {
		if (p.getGameMode() == GameMode.CREATIVE) {
			return;
		}
		ItemStack main = p.getInventory().getItemInMainHand(), off = p.getInventory().getItemInOffHand();
		ItemStack from = main == launched || main.isSimilar(launched) ? main : off == launched || off.isSimilar(launched) ? off : null;
		if (from != null && !from.getType().isAir()) {
			from.setAmount(Math.max(0, from.getAmount() - 1));
		}
	}

	private static boolean standingOnBlock(Player p) {
		World w = p.getWorld();
		BoundingBox b = p.getBoundingBox();
		BoundingBox feet = new BoundingBox(b.getMinX() + 0.02, b.getMinY() - 0.08, b.getMinZ() + 0.02, b.getMaxX() - 0.02,
				b.getMinY() + 0.02, b.getMaxZ() - 0.02);
		for (int x = floor(feet.getMinX()); x <= floor(feet.getMaxX()); ++x) {
			for (int y = floor(feet.getMinY()); y <= floor(feet.getMaxY()); ++y) {
				for (int z = floor(feet.getMinZ()); z <= floor(feet.getMaxZ()); ++z) {
					Block block = w.getBlockAt(x, y, z);
					if (!block.isPassable() && block.getBoundingBox().overlaps(feet)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean withinGroundDistance(Player p, double distance) {
		World w = p.getWorld();
		BoundingBox b = p.getBoundingBox();
		double minX = b.getMinX() + 0.02, maxX = b.getMaxX() - 0.02, minZ = b.getMinZ() + 0.02, maxZ = b.getMaxZ() - 0.02;
		int bottom = Math.max(w.getMinHeight(), floor(b.getMinY() - distance - 1.0));
		for (int y = floor(b.getMinY()); y >= bottom; --y) {
			for (int x = floor(minX); x <= floor(maxX); ++x) {
				for (int z = floor(minZ); z <= floor(maxZ); ++z) {
					Block block = w.getBlockAt(x, y, z);
					if (block.isPassable()) {
						continue;
					}
					BoundingBox bb = block.getBoundingBox();
					if (bb.getMaxX() > minX && bb.getMinX() < maxX && bb.getMaxZ() > minZ && bb.getMinZ() < maxZ
							&& b.getMinY() - bb.getMaxY() <= distance) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static int floor(double v) {
		return (int) Math.floor(v);
	}

}
