// SPDX-License-Identifier: GPL-3.0-or-later
package com.eaglercraftranked.enhancements.ranked;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.eaglercraftranked.enhancements.Feature;
import com.eaglercraftranked.enhancements.RankedEnhancements;

/**
 * Eaglercraft Ranked clients. Protocol on {@value #CHANNEL}, nothing else is read or sent:
 * client hello = {0x52, version}; server state = {version, flags} (bit 0: no-latency features on in the player's world).
 */
public final class RankedClients implements Listener, PluginMessageListener {

	public static final String CHANNEL = "rankedenhancements:main";
	private static final int VERSION = 1;
	private static final byte HELLO = 0x52;
	private static final int MAX_HELLOS_PER_MINUTE = 6;
	private static final double FLUSH_RADIUS = 64.0;

	private final RankedEnhancements plugin;
	private final Set<UUID> clients = ConcurrentHashMap.newKeySet();
	private final Map<UUID, long[]> hellos = new ConcurrentHashMap<>();

	public RankedClients(RankedEnhancements plugin) {
		this.plugin = plugin;
	}

	public void enable() {
		Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
		Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}

	public void disable() {
		Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL);
		Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
		HandlerList.unregisterAll(this);
		clients.clear();
		hellos.clear();
	}

	public int count() {
		return clients.size();
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!CHANNEL.equals(channel) || message == null || message.length != 2 || message[0] != HELLO || message[1] < 1) {
			return;
		}
		long now = System.currentTimeMillis();
		long[] window = hellos.computeIfAbsent(player.getUniqueId(), k -> new long[] { now, 0 });
		if (now - window[0] > 60_000L) {
			window[0] = now;
			window[1] = 0;
		}
		if (++window[1] > MAX_HELLOS_PER_MINUTE) {
			return;
		}
		clients.add(player.getUniqueId());
		send(player);
	}

	private boolean noLatency(Player p) {
		return clients.contains(p.getUniqueId()) && plugin.features().on(Feature.NO_LATENCY, p.getWorld());
	}

	void send(Player p) {
		if (!clients.contains(p.getUniqueId()) || !p.isOnline()) {
			return;
		}
		byte flags = (byte) (plugin.features().on(Feature.NO_LATENCY, p.getWorld()) ? 1 : 0);
		p.sendPluginMessage(plugin, CHANNEL, new byte[] { VERSION, flags });
	}

	public void sendAll() {
		for (Player p : Bukkit.getOnlinePlayers()) {
			send(p);
		}
	}

	@EventHandler
	public void onWorld(PlayerChangedWorldEvent e) {
		send(e.getPlayer());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		clients.remove(e.getPlayer().getUniqueId());
		hellos.remove(e.getPlayer().getUniqueId());
	}

	// ---- explosion blocks sent to Ranked clients right away instead of with the next tick's chunk updates

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent e) {
		if (e.getEntity() instanceof EnderCrystal && !clients.isEmpty()) {
			flushSoon(e.getLocation(), e.blockList());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent e) {
		BlockState s = e.getExplodedBlockState();
		if (s != null && s.getType() == Material.RESPAWN_ANCHOR && !clients.isEmpty()) {
			List<Block> blocks = new ArrayList<>(e.blockList());
			blocks.add(e.getBlock());
			flushSoon(e.getBlock().getLocation(), blocks);
		}
	}

	private void flushSoon(Location at, List<Block> blocks) {
		if (blocks.isEmpty() || at.getWorld() == null || !plugin.features().on(Feature.NO_LATENCY, at.getWorld())) {
			return;
		}
		List<Block> list = new ArrayList<>(blocks);
		Location center = at.clone();
		Soon.run(plugin, () -> flush(center, list));
	}

	private void flush(Location center, List<Block> blocks) {
		List<BlockState> states = null;
		double r2 = FLUSH_RADIUS * FLUSH_RADIUS;
		for (Player p : center.getWorld().getPlayers()) {
			if (!noLatency(p) || p.getLocation().distanceSquared(center) > r2) {
				continue;
			}
			if (states == null) {
				states = new ArrayList<>(blocks.size());
				for (Block b : blocks) {
					states.add(b.getState(false));
				}
			}
			p.sendBlockChanges(states);
		}
	}

	/** Runs a task right after the current packet or tick work (the server's own task queue), else next tick. */
	static final class Soon {
		private static Object server;
		private static Method direct, wrap, tell;
		private static boolean fallback;

		static void run(RankedEnhancements plugin, Runnable r) {
			Runnable task = () -> {
				if (plugin.isEnabled()) {
					r.run();
				}
			};
			if (!fallback) {
				try {
					if (server == null) {
						Object craft = Bukkit.getServer();
						Object s = craft.getClass().getMethod("getServer").invoke(craft);
						try {
							direct = s.getClass().getMethod("scheduleOnMain", Runnable.class);
						} catch (NoSuchMethodException old) {
							wrap = s.getClass().getMethod("wrapRunnable", Runnable.class);
							tell = findTell(s.getClass());
						}
						server = s;
					}
					if (direct != null) {
						direct.invoke(server, task);
					} else {
						tell.invoke(server, wrap.invoke(server, task));
					}
					return;
				} catch (ReflectiveOperationException | RuntimeException ex) {
					fallback = true;
				}
			}
			Bukkit.getScheduler().runTask(plugin, task);
		}

		private static Method findTell(Class<?> c) throws NoSuchMethodException {
			for (Class<?> k = c; k != null; k = k.getSuperclass()) {
				for (Method m : k.getDeclaredMethods()) {
					if ((m.getName().equals("tell") || m.getName().equals("schedule")) && m.getParameterCount() == 1) {
						m.setAccessible(true);
						return m;
					}
				}
			}
			throw new NoSuchMethodException("tell");
		}
	}

}
