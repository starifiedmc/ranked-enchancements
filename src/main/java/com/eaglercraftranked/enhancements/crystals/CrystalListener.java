/*
 * Copyright (C) 2023-2026 Jyguy (FasterCrystals, https://github.com/mcpvp-club/FasterCrystals)
 * Modified for Ranked Enhancements by Eaglercraft Ranked, 2026.
 * Licensed under the GNU General Public License v3 or later; see LICENSE.
 */
package com.eaglercraftranked.enhancements.crystals;

import java.util.UUID;

import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;

import io.github.retrooper.packetevents.util.folia.FoliaScheduler;

final class CrystalListener implements Listener {

	// a removed crystal stays known a little longer, so a click right after it broke still counts
	private static final long FORGET_DELAY = 40L;

	private final FastCrystals fc;

	CrystalListener(FastCrystals fc) {
		this.fc = fc;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onAdd(EntityAddToWorldEvent e) {
		if (e.getEntityType() == EntityType.END_CRYSTAL) {
			fc.crystals.put(e.getEntity().getEntityId(), (EnderCrystal) e.getEntity());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRemove(EntityRemoveFromWorldEvent e) {
		if (e.getEntityType() != EntityType.END_CRYSTAL) {
			return;
		}
		int id = e.getEntity().getEntityId();
		FoliaScheduler.getGlobalRegionScheduler().runDelayed(fc.plugin, t -> fc.crystals.remove(id), FORGET_DELAY);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldUnload(WorldUnloadEvent e) {
		UUID world = e.getWorld().getUID();
		fc.crystals.values().removeIf(c -> {
			try {
				return c.getWorld().getUID().equals(world);
			} catch (RuntimeException ex) {
				return true;
			}
		});
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent e) {
		fc.users.put(e.getPlayer().getUniqueId(), new CrystalUser());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent e) {
		fc.users.remove(e.getPlayer().getUniqueId());
	}

}
