/*
 * Copyright (C) 2023-2026 Jyguy (FasterCrystals, https://github.com/mcpvp-club/FasterCrystals)
 * Modified for Ranked Enhancements by Eaglercraft Ranked, 2026.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.eaglercraftranked.enhancements.crystals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.eaglercraftranked.enhancements.Feature;
import com.eaglercraftranked.enhancements.RankedEnhancements;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;

public final class FastCrystals {

	private static final Set<Material> AIR_TYPES = Set.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);

	final RankedEnhancements plugin;
	final Map<Integer, EnderCrystal> crystals = new ConcurrentHashMap<>();
	final Map<UUID, CrystalUser> users = new ConcurrentHashMap<>();
	final Attribute entityRange = attribute("entity_interaction_range", "player.entity_interaction_range");
	final Attribute blockRange = attribute("block_interaction_range", "player.block_interaction_range");
	private CrystalListener bukkitListener;
	private List<SimplePacketListenerAbstract> packetListeners;

	public FastCrystals(RankedEnhancements plugin) {
		this.plugin = plugin;
	}

	public void enable() {
		if (entityRange == null || blockRange == null) {
			plugin.getLogger().warning("fast-crystals: interaction range attributes not found on this server version; staying off.");
			return;
		}
		bukkitListener = new CrystalListener(this);
		plugin.getServer().getPluginManager().registerEvents(bukkitListener, plugin);
		packetListeners = List.of(new AnimationListener(this), new InteractEntityListener(this), new LastPacketListener(this));
		for (SimplePacketListenerAbstract l : packetListeners) {
			PacketEvents.getAPI().getEventManager().registerListener(l);
		}
		for (Player p : plugin.getServer().getOnlinePlayers()) {
			users.putIfAbsent(p.getUniqueId(), new CrystalUser());
		}
	}

	public void disable() {
		if (packetListeners != null) {
			for (SimplePacketListenerAbstract l : packetListeners) {
				PacketEvents.getAPI().getEventManager().unregisterListener(l);
			}
			packetListeners = null;
		}
		if (bukkitListener != null) {
			HandlerList.unregisterAll(bukkitListener);
			bukkitListener = null;
		}
		crystals.clear();
		users.clear();
	}

	boolean active(Player player) {
		return player.getGameMode() != GameMode.SPECTATOR && plugin.features().on(Feature.FAST_CRYSTALS, player.getWorld());
	}

	static double range(Player player, Attribute attribute) {
		AttributeInstance a = player.getAttribute(attribute);
		return a == null ? -1.0 : a.getValue();
	}

	// attribute keys lost their "player." prefix in 1.21.2
	private static Attribute attribute(String... keys) {
		for (String k : keys) {
			Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(k));
			if (a != null) {
				return a;
			}
		}
		return null;
	}

	void spawnCrystal(Location loc, Player player, ItemStack item, EquipmentSlot hand) {
		Location spawnLoc = loc.clone();
		if (!AIR_TYPES.contains(spawnLoc.getBlock().getType())) {
			return;
		}
		spawnLoc.add(0.0, 1.0, 0.0);
		List<Entity> nearby = List.copyOf(spawnLoc.getWorld().getNearbyEntities(spawnLoc, 0.5, 1, 0.5,
				e -> !(e instanceof Player p) || (p.getGameMode() != GameMode.SPECTATOR && player.canSee(p))));
		if (!nearby.isEmpty()) {
			return;
		}
		spawnLoc.subtract(0.0, 1.0, 0.0);
		EnderCrystal crystal = spawnLoc.getWorld().spawn(spawnLoc, EnderCrystal.class, e -> e.setShowingBottom(false));
		// protection plugins get the same event a normal placement fires
		EntityPlaceEvent place = new EntityPlaceEvent(crystal, player, spawnLoc.getBlock().getRelative(BlockFace.DOWN), BlockFace.UP, hand);
		plugin.getServer().getPluginManager().callEvent(place);
		if (place.isCancelled()) {
			crystal.remove();
			return;
		}
		if (player.getGameMode() != GameMode.CREATIVE) {
			item.setAmount(item.getAmount() - 1);
		}
	}

}
