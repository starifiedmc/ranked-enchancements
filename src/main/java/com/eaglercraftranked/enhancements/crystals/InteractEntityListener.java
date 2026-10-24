/*
 * Copyright (C) 2023-2026 Jyguy (FasterCrystals, https://github.com/mcpvp-club/FasterCrystals)
 * Modified for Ranked Enhancements by Eaglercraft Ranked, 2026.
 * Licensed under the GNU General Public License v3 or later; see LICENSE.
 */
package com.eaglercraftranked.enhancements.crystals;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import io.github.retrooper.packetevents.util.folia.FoliaScheduler;

/** Placing a crystal where one is being broken places it at once. */
final class InteractEntityListener extends SimplePacketListenerAbstract {

	private static final Set<Material> BASES = Set.of(Material.OBSIDIAN, Material.BEDROCK);

	private final FastCrystals fc;

	InteractEntityListener(FastCrystals fc) {
		this.fc = fc;
	}

	@Override
	public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
		if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
			return;
		}
		WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
		if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) {
			return;
		}
		Player player = event.getPlayer();
		if (player == null || !fc.active(player)) {
			return;
		}
		EnderCrystal crystal = fc.crystals.get(packet.getEntityId());
		if (crystal == null) {
			return;
		}
		Location eye = player.getEyeLocation();
		Vector direction = eye.getDirection();
		boolean main = packet.getHand() == InteractionHand.MAIN_HAND;
		FoliaScheduler.getRegionScheduler().run(fc.plugin, eye, task -> {
			ItemStack item = main ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
			if (item.getType() != Material.END_CRYSTAL) {
				return;
			}
			double reach = FastCrystals.range(player, fc.blockRange);
			if (reach < 0.0) {
				return;
			}
			RayTraceResult result = eye.getWorld().rayTraceBlocks(eye, direction, reach);
			Block block = result == null ? null : result.getHitBlock();
			if (block == null || !BASES.contains(block.getType())) {
				return;
			}
			Location base = crystal.getLocation().subtract(0.5, 1.0, 0.5);
			if (!block.getLocation().equals(base)) {
				return;
			}
			fc.spawnCrystal(crystal.getLocation(), player, item, main ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND);
		});
	}

}
