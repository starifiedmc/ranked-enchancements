/*
 * Copyright (C) 2023-2026 Jyguy (FasterCrystals, https://github.com/mcpvp-club/FasterCrystals)
 * Modified for Ranked Enhancements by Eaglercraft Ranked, 2026.
 * Licensed under the GNU General Public License v3 or later; see LICENSE.
 */
package com.eaglercraftranked.enhancements.crystals;

import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

/** Remembers each player's last relevant packet, so swings from dropping items are not taken as attacks. */
final class LastPacketListener extends SimplePacketListenerAbstract {

	private final FastCrystals fc;

	LastPacketListener(FastCrystals fc) {
		super(PacketListenerPriority.MONITOR);
		this.fc = fc;
	}

	@Override
	public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
		Player player = event.getPlayer();
		if (player == null) {
			return;
		}
		CrystalUser user = fc.users.get(player.getUniqueId());
		if (user == null) {
			return;
		}
		CrystalUser.Last packet = classify(event);
		// a drop from the inventory arrives as ANIMATION then CLICK_WINDOW
		if (user.lastPacket == CrystalUser.Last.ANIMATION) {
			user.ignoreAnim = packet == CrystalUser.Last.INV_DROP || packet == CrystalUser.Last.CREATIVE_INV_ACTION;
		}
		user.lastPacket = packet;
	}

	private static CrystalUser.Last classify(PacketPlayReceiveEvent event) {
		var type = event.getPacketType();
		if (type == PacketType.Play.Client.ANIMATION) {
			return CrystalUser.Last.ANIMATION;
		}
		if (type == PacketType.Play.Client.PLAYER_DIGGING) {
			DiggingAction a = new WrapperPlayClientPlayerDigging(event).getAction();
			if (a == DiggingAction.DROP_ITEM || a == DiggingAction.DROP_ITEM_STACK) {
				return CrystalUser.Last.IGNORE;
			}
			return a == DiggingAction.START_DIGGING ? CrystalUser.Last.START_DIGGING : CrystalUser.Last.MISC;
		}
		if (type == PacketType.Play.Client.CLICK_WINDOW) {
			WrapperPlayClientClickWindow w = new WrapperPlayClientClickWindow(event);
			if (w.getWindowClickType() == WrapperPlayClientClickWindow.WindowClickType.THROW
					|| w.getWindowClickType() == WrapperPlayClientClickWindow.WindowClickType.PICKUP && w.getSlot() == -999) {
				return CrystalUser.Last.INV_DROP;
			}
			return CrystalUser.Last.MISC;
		}
		if (type == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
			return CrystalUser.Last.CREATIVE_INV_ACTION;
		}
		if (type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT || type == PacketType.Play.Client.USE_ITEM) {
			return CrystalUser.Last.IGNORE;
		}
		if (type == PacketType.Play.Client.INTERACT_ENTITY) {
			return new WrapperPlayClientInteractEntity(event).getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK
					? CrystalUser.Last.ATTACK : CrystalUser.Last.MISC;
		}
		return type == PacketType.Play.Client.ATTACK ? CrystalUser.Last.ATTACK : CrystalUser.Last.MISC;
	}

}
