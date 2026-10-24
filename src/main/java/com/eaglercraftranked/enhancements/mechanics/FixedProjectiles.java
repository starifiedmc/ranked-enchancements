// SPDX-License-Identifier: GPL-3.0-or-later
package com.eaglercraftranked.enhancements.mechanics;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import com.eaglercraftranked.enhancements.Feature;
import com.eaglercraftranked.enhancements.RankedEnhancements;

/**
 * Projectiles leave exactly along the aim (vanilla speed and momentum, no random spread), and a wind charge is
 * steered a little so it catches its owner's pearl.
 */
public final class FixedProjectiles implements Listener {

	private static final int CATCH_HORIZON = 20;
	private static final int CATCH_HELP_TICKS = 80;
	private static final int CATCH_LOOK_PAST = 40;
	private static final double CATCH_STEP_OF_SPEED = 0.15;
	private static final double CATCH_ASSIST = 1.0, CATCH_ANGLE = 4.0, CATCH_MAX_REACH = 3.0, CATCH_MAX_STEP = 0.25;
	private static final int CATCH_WINDOW = 50, CATCH_CHARGE_FIRST = 20;
	private static final double PEARL_GRAVITY = 0.03, PEARL_DRAG = 0.99f;
	private static final double ENTRY_FIRST = 0.15, ENTRY_LAST = 0.85, ENTRY_INSIDE = 0.8;

	private final RankedEnhancements plugin;
	private final Map<UUID, List<Thrown>> recentPearls = new HashMap<>(), recentCharges = new HashMap<>();
	private final List<Catch> catches = new ArrayList<>();
	private BukkitTask task;

	private record Thrown(Projectile entity, int tick) {
	}

	private record Plan(int k, double dx, double dy, double dz, double total) {
	}

	private static final class Catch {
		final UUID owner;
		final EnderPearl pearl;
		final AbstractWindCharge charge;
		final int until;
		final double sx, sy, sz;
		double ox, oy, oz;
		int target = -1;

		Catch(UUID owner, EnderPearl pearl, AbstractWindCharge charge, int until, BoundingBox thrownBox) {
			this.owner = owner;
			this.pearl = pearl;
			this.charge = charge;
			this.until = until;
			sx = thrownBox.getCenterX();
			sy = thrownBox.getCenterY();
			sz = thrownBox.getCenterZ();
		}
	}

	public FixedProjectiles(RankedEnhancements plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		recentPearls.remove(e.getPlayer().getUniqueId());
		recentCharges.remove(e.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onLaunch(ProjectileLaunchEvent e) {
		Projectile proj = e.getEntity();
		if (!(proj.getShooter() instanceof Player p) || proj instanceof AbstractArrow && !(proj instanceof Trident)
				|| proj instanceof Firework) {
			return;
		}
		if (!plugin.features().on(Feature.FIXED_PROJECTILES, p.getWorld())) {
			return;
		}
		float pow, yOffset = 0.0f;
		if (proj instanceof EnderPearl || proj instanceof Snowball || proj instanceof Egg || proj instanceof WindCharge) {
			pow = 1.5f;
		} else if (proj instanceof ThrownPotion) {
			pow = 0.5f;
			yOffset = -20.0f;
		} else if (proj instanceof ThrownExpBottle) {
			pow = 0.7f;
			yOffset = -20.0f;
		} else if (proj instanceof Trident) {
			pow = 2.5f;
		} else {
			return;
		}
		Location at = p.getLocation();
		Vector v = shotFromRotation(at.getPitch(), at.getYaw(), yOffset, pow);
		addKnownMovement(p, v);
		proj.setVelocity(v);
		if (proj instanceof EnderPearl || proj instanceof WindCharge) {
			pairCatch(p, proj);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onShootBow(EntityShootBowEvent e) {
		if (!(e.getEntity() instanceof Player p) || !(e.getProjectile() instanceof Projectile proj) || e.getBow() == null) {
			return;
		}
		if (!plugin.features().on(Feature.FIXED_PROJECTILES, p.getWorld())) {
			return;
		}
		Location at = p.getLocation();
		Material weapon = e.getBow().getType();
		if (weapon == Material.BOW) {
			Vector aim = shotFromRotation(at.getPitch(), at.getYaw(), 0.0f, e.getForce() * 3.0f);
			Vector momentum = new Vector();
			addKnownMovement(p, momentum);
			// leave modded multi-arrow spreads alone
			if (angle(proj.getVelocity().subtract(momentum), aim) < 4.0) {
				proj.setVelocity(aim.add(momentum));
			}
		} else if (weapon == Material.CROSSBOW) {
			float pow = proj instanceof Firework ? 1.6f : 3.15f;
			double[] view = viewVector(at.getPitch(), at.getYaw()), up = viewVector(at.getPitch() - 90.0f, at.getYaw());
			Vector now = proj.getVelocity();
			double cross = (view[1] * now.getZ() - view[2] * now.getY()) * up[0] + (view[2] * now.getX() - view[0] * now.getZ()) * up[1]
					+ (view[0] * now.getY() - view[1] * now.getX()) * up[2];
			double dot = view[0] * now.getX() + view[1] * now.getY() + view[2] * now.getZ();
			double deg = Math.toDegrees(Math.atan2(cross, dot));
			double snapped = Math.round(deg / 5.0) * 5.0;
			if (Math.abs(deg - snapped) > 2.5) {
				return;
			}
			proj.setVelocity(rotate(view, up, Math.toRadians(snapped)).multiply((double) pow));
		}
	}

	private static double angle(Vector a, Vector b) {
		double la = a.length(), lb = b.length();
		if (la < 1.0e-9 || lb < 1.0e-9) {
			return 0.0;
		}
		return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, a.dot(b) / (la * lb)))));
	}

	// Mth.sin / Mth.cos lookup tables, so directions match the game exactly
	private static float sin(double radians) {
		return (float) Math.sin(((int) ((long) (radians * 10430.378350470453) & 65535L)) / 10430.378350470453);
	}

	private static float cos(double radians) {
		return (float) Math.sin(((int) ((long) (radians * 10430.378350470453 + 16384.0) & 65535L)) / 10430.378350470453);
	}

	private static Vector shotFromRotation(float xRot, float yRot, float yOffset, float pow) {
		float rad = (float) (Math.PI / 180.0);
		float xd = -sin(yRot * rad) * cos(xRot * rad);
		float yd = -sin((xRot + yOffset) * rad);
		float zd = cos(yRot * rad) * cos(xRot * rad);
		double len = Math.sqrt((double) xd * xd + (double) yd * yd + (double) zd * zd);
		if (len < 1.0e-5) {
			return new Vector();
		}
		return new Vector(xd / len * pow, yd / len * pow, zd / len * pow);
	}

	private static double[] viewVector(float xRot, float yRot) {
		float x = xRot * (float) (Math.PI / 180.0), y = -yRot * (float) (Math.PI / 180.0);
		float yc = cos(y), ys = sin(y), xc = cos(x), xs = sin(x);
		return new double[] { ys * xc, -xs, yc * xc };
	}

	private static Vector rotate(double[] v, double[] axis, double radians) {
		double c = Math.cos(radians), s = Math.sin(radians);
		double kx = axis[0], ky = axis[1], kz = axis[2];
		double dot = kx * v[0] + ky * v[1] + kz * v[2];
		double cx = ky * v[2] - kz * v[1], cy = kz * v[0] - kx * v[2], cz = kx * v[1] - ky * v[0];
		return new Vector(v[0] * c + cx * s + kx * dot * (1 - c), v[1] * c + cy * s + ky * dot * (1 - c),
				v[2] * c + cz * s + kz * dot * (1 - c));
	}

	private static Method handleMethod, knownMovementMethod;
	private static Field vecX, vecY, vecZ;
	private static boolean movementUnknown;

	// what vanilla adds from the thrower's movement (ServerPlayer.getKnownMovement, newer servers only)
	@SuppressWarnings("deprecation")
	private void addKnownMovement(Player p, Vector v) {
		if (movementUnknown) {
			return;
		}
		try {
			if (handleMethod == null) {
				Method h = p.getClass().getMethod("getHandle");
				Object handle = h.invoke(p);
				Method km = handle.getClass().getMethod("getKnownMovement");
				Object vec = km.invoke(handle);
				vecX = vec.getClass().getField("x");
				vecY = vec.getClass().getField("y");
				vecZ = vec.getClass().getField("z");
				knownMovementMethod = km;
				handleMethod = h;
			}
			Object vec = knownMovementMethod.invoke(handleMethod.invoke(p));
			v.setX(v.getX() + vecX.getDouble(vec));
			if (!p.isOnGround()) {
				v.setY(v.getY() + vecY.getDouble(vec));
			}
			v.setZ(v.getZ() + vecZ.getDouble(vec));
		} catch (ReflectiveOperationException | RuntimeException ex) {
			movementUnknown = true;
			plugin.getLogger().info("fixed-projectiles: this server version does not expose player movement; projectiles leave without the thrower's momentum.");
		}
	}

	// ---- pearl catch

	private void pairCatch(Player p, Projectile thrown) {
		int now = Bukkit.getCurrentTick(), keep = Math.max(CATCH_WINDOW, CATCH_CHARGE_FIRST);
		UUID u = p.getUniqueId();
		boolean isPearl = thrown instanceof EnderPearl;
		List<Thrown> same = recent(isPearl ? recentPearls : recentCharges, u, now, keep);
		for (Thrown t : recent(isPearl ? recentCharges : recentPearls, u, now, keep)) {
			if (now - t.tick > (isPearl ? CATCH_CHARGE_FIRST : CATCH_WINDOW)) {
				continue;
			}
			EnderPearl pearl = (EnderPearl) (isPearl ? thrown : t.entity);
			AbstractWindCharge charge = (AbstractWindCharge) (isPearl ? t.entity : thrown);
			catches.add(new Catch(u, pearl, charge, now + CATCH_HELP_TICKS, charge.getBoundingBox()));
		}
		same.add(new Thrown(thrown, now));
		if (!catches.isEmpty() && task == null) {
			task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
		}
	}

	private static List<Thrown> recent(Map<UUID, List<Thrown>> map, UUID u, int now, int keep) {
		List<Thrown> l = map.computeIfAbsent(u, k -> new ArrayList<>());
		l.removeIf(t -> !t.entity.isValid() || now - t.tick > keep);
		return l;
	}

	private void tick() {
		int now = Bukkit.getCurrentTick();
		for (Iterator<Catch> it = catches.iterator(); it.hasNext();) {
			Catch c = it.next();
			if (now > c.until || !c.pearl.isValid() || !c.charge.isValid() || c.pearl.getWorld() != c.charge.getWorld()
					|| !ownedBy(c.pearl, c.owner) || !ownedBy(c.charge, c.owner)
					|| !plugin.features().on(Feature.FIXED_PROJECTILES, c.pearl.getWorld())) {
				it.remove();
				continue;
			}
			steer(c, now);
		}
		if (catches.isEmpty() && task != null) {
			task.cancel();
			task = null;
		}
	}

	private static boolean ownedBy(Projectile pr, UUID owner) {
		return pr.getShooter() instanceof Player p && p.getUniqueId().equals(owner);
	}

	/**
	 * Follows both paths ahead in vanilla's tick order. If vanilla makes the catch alone, nothing is touched;
	 * otherwise the charge is moved an even share of the way to the closest meeting it can reach. The pearl is
	 * never moved, so the landing spot is the one vanilla gives.
	 */
	private void steer(Catch c, int now) {
		EnderPearl pearl = c.pearl;
		AbstractWindCharge charge = c.charge;
		if (pearl.isInWater() || charge.isInWater() || !pearl.isTicking() || !charge.isTicking()) {
			c.target = -1;
			return;
		}
		for (Catch o : catches) {
			if (o != c && o.target >= now && (o.pearl == pearl || o.charge == charge)) {
				return;
			}
		}
		Location from = pearl.getLocation();
		Vector pv = pearl.getVelocity();
		double px = from.getX(), py = from.getY(), pz = from.getZ(), vx = pv.getX(), vy = pv.getY(), vz = pv.getZ();
		double gravity = pearl.hasGravity() ? PEARL_GRAVITY : 0.0;
		Vector u = charge.getVelocity();
		double ux = u.getX(), uy = u.getY(), uz = u.getZ();
		BoundingBox box = charge.getBoundingBox();
		double cx = box.getCenterX(), cy = box.getCenterY(), cz = box.getCenterZ();
		double hx = box.getWidthX() * 0.5, hy = box.getHeight() * 0.5, hz = box.getWidthZ() * 0.5;
		boolean pearlFirst = pearl.getEntityId() < charge.getEntityId();
		int age = pearl.getTicksLived();
		int left = c.until - now + 1, last = Math.min(CATCH_HORIZON, left), look = left + CATCH_LOOK_PAST;
		double tan = Math.tan(Math.toRadians(CATCH_ANGLE));
		double step = Math.min(CATCH_MAX_STEP, CATCH_STEP_OF_SPEED * Math.sqrt(ux * ux + uy * uy + uz * uz));
		double moved = Math.sqrt(c.ox * c.ox + c.oy * c.oy + c.oz * c.oz);
		double[] path = new double[(last + 1) * 3];
		Plan best = null, kept = null;
		for (int k = 1; k <= look; ++k) {
			if (k <= last + 1) {
				path[(k - 1) * 3] = px;
				path[(k - 1) * 3 + 1] = py;
				path[(k - 1) * 3 + 2] = pz;
			}
			vx *= PEARL_DRAG;
			vy = (vy - gravity) * PEARL_DRAG;
			vz *= PEARL_DRAG;
			double mg = Math.max(0.0f, Math.min(0.3f, (age + k - 2) / 20.0f));
			int n = pearlFirst ? k - 1 : k;
			double qx = cx + ux * n, qy = cy + uy * n, qz = cz + uz * n;
			double ax = hx + mg, ay = hy + mg, az = hz + mg;
			if (clips(px, py, pz, vx, vy, vz, qx, qy, qz, ax, ay, az)) {
				c.target = -1;
				return;
			}
			if (k <= last) {
				double ex = qx - c.ox - c.sx, ey = qy - c.oy - c.sy, ez = qz - c.oz - c.sz;
				boolean committed = c.target == now + k;
				double reach = Math.min(CATCH_MAX_REACH, CATCH_ASSIST + tan * Math.sqrt(ex * ex + ey * ey + ez * ez))
						+ (committed ? 1.0e-3 : 0.0);
				double cap = step * k + (committed ? 1.0e-6 : 0.0);
				double vv = vx * vx + vy * vy + vz * vz;
				double t = vv < 1.0e-12 ? 0.0 : Math.max(0.0, Math.min(1.0, ((qx - px) * vx + (qy - py) * vy + (qz - pz) * vz) / vv));
				double gx = px + vx * t - qx, gy = py + vy * t - qy, gz = pz + vz * t - qz;
				if (Math.sqrt(gx * gx + gy * gy + gz * gz) - 1.7321 * Math.max(ax, Math.max(ay, az)) <= Math.min(cap, reach + moved)) {
					double[] q = place(px, py, pz, vx, vy, vz, qx, qy, qz, ax, ay, az);
					if (q != null) {
						double dx = q[0] - qx, dy = q[1] - qy, dz = q[2] - qz;
						double tx = c.ox + dx, ty = c.oy + dy, tz = c.oz + dz, total = Math.sqrt(tx * tx + ty * ty + tz * tz);
						if (q[3] <= cap && total <= reach) {
							Plan plan = new Plan(k, dx, dy, dz, total);
							if (committed) {
								kept = plan;
							}
							if (best == null || total < best.total - 1.0e-9) {
								best = plan;
							}
						}
					}
				}
			}
			px += vx;
			py += vy;
			pz += vz;
		}
		Plan go = kept != null ? kept : best;
		if (go == null) {
			c.target = -1;
			return;
		}
		World w = charge.getWorld();
		for (int k = 1; k <= go.k; ++k) {
			int a = (k - 1) * 3, b = k * 3;
			if (!free(w, path[a], path[a + 1], path[a + 2], path[b], path[b + 1], path[b + 2])) {
				c.target = -1;
				return;
			}
		}
		int n = pearlFirst ? go.k - 1 : go.k;
		if (!free(w, cx, cy, cz, cx + ux * n + go.dx, cy + uy * n + go.dy, cz + uz * n + go.dz)) {
			c.target = -1;
			return;
		}
		double mx = go.dx / go.k, my = go.dy / go.k, mz = go.dz / go.k;
		Vector keep = charge.getVelocity();
		if (!charge.teleport(charge.getLocation().add(mx, my, mz))) {
			c.target = -1;
			return;
		}
		charge.setVelocity(keep);
		c.ox += mx;
		c.oy += my;
		c.oz += mz;
		c.target = now + go.k;
	}

	private static boolean free(World w, double ax, double ay, double az, double bx, double by, double bz) {
		double dx = bx - ax, dy = by - ay, dz = bz - az, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0e-6) {
			return true;
		}
		RayTraceResult hit = w.rayTraceBlocks(new Location(w, ax, ay, az), new Vector(dx / len, dy / len, dz / len), len,
				FluidCollisionMode.NEVER, true);
		return hit == null || hit.getHitBlock() == null;
	}

	// the box centre closest to c that the pearl's move runs into through a face (ternary search per axis)
	private static double[] place(double px, double py, double pz, double mx, double my, double mz, double cx, double cy,
			double cz, double hx, double hy, double hz) {
		double[] p = { px, py, pz }, m = { mx, my, mz }, c = { cx, cy, cz }, h = { hx, hy, hz };
		double[] best = null;
		double bestD2 = Double.MAX_VALUE;
		for (int a = 0; a < 3; ++a) {
			if (Math.abs(m[a]) < 2.0e-3) {
				continue;
			}
			double lo = ENTRY_FIRST, hi = ENTRY_LAST;
			for (int it = 0; it < 26; ++it) {
				double s1 = lo + (hi - lo) / 3.0, s2 = hi - (hi - lo) / 3.0;
				if (placeCost(p, m, c, h, a, s1) <= placeCost(p, m, c, h, a, s2)) {
					hi = s2;
				} else {
					lo = s1;
				}
			}
			double s = (lo + hi) * 0.5, d2 = placeCost(p, m, c, h, a, s);
			if (d2 < bestD2) {
				bestD2 = d2;
				best = new double[4];
				for (int i = 0; i < 3; ++i) {
					best[i] = placeAt(p, m, c, h, a, s, i);
				}
			}
		}
		if (best != null) {
			best[3] = Math.sqrt(bestD2);
		}
		return best;
	}

	private static double placeAt(double[] p, double[] m, double[] c, double[] h, int a, double s, int i) {
		double on = p[i] + m[i] * s;
		return i == a ? on + Math.signum(m[a]) * h[i] : Math.max(on - h[i] * ENTRY_INSIDE, Math.min(on + h[i] * ENTRY_INSIDE, c[i]));
	}

	private static double placeCost(double[] p, double[] m, double[] c, double[] h, int a, double s) {
		double d2 = 0.0;
		for (int i = 0; i < 3; ++i) {
			double d = placeAt(p, m, c, h, a, s, i) - c[i];
			d2 += d * d;
		}
		return d2;
	}

	// AABB.clip the way vanilla tests a projectile's move
	private static boolean clips(double px, double py, double pz, double mx, double my, double mz, double cx, double cy,
			double cz, double hx, double hy, double hz) {
		double minX = cx - hx, maxX = cx + hx, minY = cy - hy, maxY = cy + hy, minZ = cz - hz, maxZ = cz + hz;
		final double eps = 1.0e-7;
		if (mx > eps && face(minX, px, mx, py, my, pz, mz, minY, maxY, minZ, maxZ)) {
			return true;
		}
		if (mx < -eps && face(maxX, px, mx, py, my, pz, mz, minY, maxY, minZ, maxZ)) {
			return true;
		}
		if (my > eps && face(minY, py, my, px, mx, pz, mz, minX, maxX, minZ, maxZ)) {
			return true;
		}
		if (my < -eps && face(maxY, py, my, px, mx, pz, mz, minX, maxX, minZ, maxZ)) {
			return true;
		}
		if (mz > eps && face(minZ, pz, mz, px, mx, py, my, minX, maxX, minY, maxY)) {
			return true;
		}
		return mz < -eps && face(maxZ, pz, mz, px, mx, py, my, minX, maxX, minY, maxY);
	}

	private static boolean face(double at, double p, double m, double pa, double ma, double pb, double mb, double minA,
			double maxA, double minB, double maxB) {
		double s = (at - p) / m;
		if (s <= 0.0 || s >= 1.0) {
			return false;
		}
		double a = pa + s * ma, b = pb + s * mb;
		return minA - 1.0e-7 < a && a < maxA + 1.0e-7 && minB - 1.0e-7 < b && b < maxB + 1.0e-7;
	}

}
