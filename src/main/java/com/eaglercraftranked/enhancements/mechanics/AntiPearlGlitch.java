// SPDX-License-Identifier: GPL-3.0-or-later
package com.eaglercraftranked.enhancements.mechanics;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import com.eaglercraftranked.enhancements.Feature;
import com.eaglercraftranked.enhancements.RankedEnhancements;

/**
 * Pearl landings must fit the player's body and be reachable in a straight line from where the pearl hit.
 * Otherwise the nearest such spot is used, or the teleport is cancelled.
 */
public final class AntiPearlGlitch implements Listener {

	private static final double EPS = 1.0E-4;
	private static final double SIDE = 0.6, SIDE_STEP = 0.15;
	private static final double DOWN = 1.9, UP = 1.0, Y_STEP = 1.0 / 16.0;

	private final RankedEnhancements plugin;

	public AntiPearlGlitch(RankedEnhancements plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPearl(PlayerTeleportEvent e) {
		if (e.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
			return;
		}
		Location to = e.getTo();
		World w = to.getWorld();
		Player p = e.getPlayer();
		if (w == null || p.getGameMode() == GameMode.SPECTATOR || !plugin.features().on(Feature.ANTI_PEARL_GLITCH, w)) {
			return;
		}
		BoundingBox own = p.getBoundingBox();
		double half = own.getWidthX() / 2.0, h = own.getHeight();
		Vector hit = to.toVector();
		List<BoundingBox> solids = solids(w, hit, half, h);
		Vector best = find(solids, hit, half, h);
		if (best == null) {
			e.setCancelled(true);
			return;
		}
		if (best.distanceSquared(hit) > EPS * EPS) {
			Location fixed = to.clone();
			fixed.setX(best.getX());
			fixed.setY(best.getY());
			fixed.setZ(best.getZ());
			e.setTo(fixed);
		}
	}

	private static Vector find(List<BoundingBox> solids, Vector hit, double half, double h) {
		if (fits(solids, hit.getX(), hit.getY(), hit.getZ(), half, h)) {
			return hit;
		}
		List<double[]> found = new ArrayList<>();
		int n = (int) Math.round(SIDE / SIDE_STEP);
		for (int ix = -n; ix <= n; ix++) {
			for (int iz = -n; iz <= n; iz++) {
				double x = hit.getX() + ix * SIDE_STEP, z = hit.getZ() + iz * SIDE_STEP;
				double side = Math.hypot(x - hit.getX(), z - hit.getZ());
				for (int dir = -1; dir <= 1; dir += 2) {
					double y = fitY(solids, x, hit.getY(), z, half, h, dir);
					if (!Double.isNaN(y)) {
						found.add(new double[] { side + Math.abs(y - hit.getY()), x, y, z });
					}
				}
			}
		}
		found.sort((a, b) -> Double.compare(a[0], b[0]));
		for (double[] c : found) {
			if (clear(solids, hit, new Vector(c[1], c[2] + 0.1, c[3]))
					&& clear(solids, hit, new Vector(c[1], c[2] + h - 0.1, c[3]))) {
				return new Vector(c[1], c[2], c[3]);
			}
		}
		return null;
	}

	/** The fitting height nearest to {@code y} at this x/z going down (-1) or up (1), or NaN. */
	private static double fitY(List<BoundingBox> solids, double x, double y, double z, double half, double h, int dir) {
		int steps = (int) Math.ceil((dir < 0 ? DOWN : UP) / Y_STEP);
		for (int i = dir < 0 ? 0 : 1; i <= steps; i++) {
			double at = y + dir * i * Y_STEP;
			if (fits(solids, x, at, z, half, h)) {
				return at;
			}
		}
		return Double.NaN;
	}

	private static boolean fits(List<BoundingBox> solids, double x, double y, double z, double half, double h) {
		double x0 = x - half + EPS, x1 = x + half - EPS, y0 = y + EPS, y1 = y + h - EPS, z0 = z - half + EPS, z1 = z + half - EPS;
		for (BoundingBox b : solids) {
			if (x1 > b.getMinX() && x0 < b.getMaxX() && y1 > b.getMinY() && y0 < b.getMaxY() && z1 > b.getMinZ() && z0 < b.getMaxZ()) {
				return false;
			}
		}
		return true;
	}

	/** No solid box between the two points (boxes shrunk a little so touching a face is fine). */
	private static boolean clear(List<BoundingBox> solids, Vector a, Vector b) {
		double dx = b.getX() - a.getX(), dy = b.getY() - a.getY(), dz = b.getZ() - a.getZ();
		for (BoundingBox s : solids) {
			double t0 = 0.0, t1 = 1.0;
			double[] o = { a.getX(), a.getY(), a.getZ() }, d = { dx, dy, dz };
			double[] lo = { s.getMinX() + EPS, s.getMinY() + EPS, s.getMinZ() + EPS };
			double[] hi = { s.getMaxX() - EPS, s.getMaxY() - EPS, s.getMaxZ() - EPS };
			boolean miss = false;
			for (int i = 0; i < 3 && !miss; i++) {
				if (Math.abs(d[i]) < 1.0E-9) {
					miss = o[i] <= lo[i] || o[i] >= hi[i];
				} else {
					double u = (lo[i] - o[i]) / d[i], v = (hi[i] - o[i]) / d[i];
					t0 = Math.max(t0, Math.min(u, v));
					t1 = Math.min(t1, Math.max(u, v));
					miss = t0 >= t1;
				}
			}
			if (!miss) {
				return false;
			}
		}
		return true;
	}

	private static List<BoundingBox> solids(World w, Vector at, double half, double h) {
		int x0 = (int) Math.floor(at.getX() - SIDE - half - 1), x1 = (int) Math.floor(at.getX() + SIDE + half + 1);
		int z0 = (int) Math.floor(at.getZ() - SIDE - half - 1), z1 = (int) Math.floor(at.getZ() + SIDE + half + 1);
		int y0 = Math.max(w.getMinHeight(), (int) Math.floor(at.getY() - DOWN) - 1);
		int y1 = Math.min(w.getMaxHeight() - 1, (int) Math.floor(at.getY() + UP + h) + 1);
		List<BoundingBox> out = new ArrayList<>();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = y0; y <= y1; y++) {
					Block b = w.getBlockAt(x, y, z);
					if (b.getType().isAir()) {
						continue;
					}
					for (BoundingBox box : b.getCollisionShape().getBoundingBoxes()) {
						out.add(box.clone().shift(x, y, z));
					}
				}
			}
		}
		return out;
	}

}
