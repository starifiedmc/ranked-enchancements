/*
 * Copyright (C) 2023-2026 Jyguy (FasterCrystals, https://github.com/mcpvp-club/FasterCrystals)
 * Modified for Ranked Enhancements by Eaglercraft Ranked, 2026.
 * Licensed under the GNU General Public License v3 or later; see LICENSE.
 */
package com.eaglercraftranked.enhancements.crystals;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.entity.Vehicle;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;

import io.github.retrooper.packetevents.util.folia.FoliaScheduler;

/** A swing at a crystal breaks it as soon as the swing arrives. */
final class AnimationListener extends SimplePacketListenerAbstract {

	private final FastCrystals fc;

	AnimationListener(FastCrystals fc) {
		this.fc = fc;
	}

	private static boolean weakened(Player player) {
		PotionEffect weakness = player.getPotionEffect(PotionEffectType.WEAKNESS);
		PotionEffect strength = player.getPotionEffect(PotionEffectType.STRENGTH);
		int w = weakness != null ? weakness.getAmplifier() + 1 : 0, s = strength != null ? strength.getAmplifier() + 1 : 0;
		return s * 3 - w * 4 < 0;
	}

	// what vanilla can hit with a ray (Entity.isPickable)
	private static boolean pickable(Entity e) {
		if (e instanceof LivingEntity le) {
			return !le.isDead() && !(e instanceof ArmorStand a && a.isMarker());
		}
		return e instanceof EnderCrystal || e instanceof Vehicle || e instanceof Hanging || e instanceof Interaction
				|| e instanceof Fireball || e instanceof ShulkerBullet;
	}

	@Override
	public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
		if (event.getPacketType() != PacketType.Play.Client.ANIMATION || event.isCancelled()) {
			return;
		}
		Player player = event.getPlayer();
		if (player == null || !fc.active(player) || weakened(player)) {
			return;
		}
		CrystalUser user = fc.users.get(player.getUniqueId());
		if (user == null) {
			return;
		}
		CrystalUser.Last last = user.lastPacket;
		Location eye = player.getEyeLocation();
		Vector direction = eye.getDirection();
		FoliaScheduler.getRegionScheduler().run(fc.plugin, eye, task -> {
			if (last == CrystalUser.Last.IGNORE || user.ignoreAnim) {
				return;
			}
			Location now = player.getEyeLocation();
			if (eye.getWorld() != now.getWorld() || now.distanceSquared(eye) > 100) {
				return;
			}
			double reach = FastCrystals.range(player, fc.entityRange);
			if (reach < 0.0) {
				return;
			}
			RayTraceResult result = eye.getWorld().rayTraceEntities(eye, direction, reach, 0.0, entity -> {
				if (!pickable(entity)) {
					return false;
				}
				if (!(entity instanceof Player p)) {
					return true;
				}
				return p.getGameMode() != GameMode.SPECTATOR && !player.getUniqueId().equals(p.getUniqueId()) && player.canSee(p);
			});
			if (result == null) {
				return;
			}
			Entity entity = result.getHitEntity();
			// skip a crystal spawned this tick (no double pops)
			if (entity == null || entity.getType() != EntityType.END_CRYSTAL || entity.getTicksLived() == 0) {
				return;
			}
			if (!entity.getBoundingBox().contains(eye.toVector())) {
				double blockReach = FastCrystals.range(player, fc.blockRange);
				if (blockReach < 0.0) {
					return;
				}
				RayTraceResult b = eye.getWorld().rayTraceBlocks(eye, direction, blockReach);
				if (b != null) {
					Block block = b.getHitBlock();
					Vector eyeV = eye.toVector();
					if (block != null) {
						if (eyeV.distanceSquared(b.getHitPosition()) <= eyeV.distanceSquared(result.getHitPosition())) {
							return;
						}
						// mid block-break: only the first left click counts
						if (last != CrystalUser.Last.START_DIGGING && last != CrystalUser.Last.ATTACK) {
							return;
						}
					}
				}
			}
			player.attack(entity);
		});
	}

}
