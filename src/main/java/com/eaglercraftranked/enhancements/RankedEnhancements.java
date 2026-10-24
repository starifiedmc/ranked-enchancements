// SPDX-License-Identifier: GPL-3.0-or-later
package com.eaglercraftranked.enhancements;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import com.eaglercraftranked.enhancements.crystals.FastCrystals;
import com.eaglercraftranked.enhancements.mechanics.AntiPearlGlitch;
import com.eaglercraftranked.enhancements.mechanics.FixedProjectiles;
import com.eaglercraftranked.enhancements.mechanics.ShieldStun;
import com.eaglercraftranked.enhancements.mechanics.WindChargeCatch;
import com.eaglercraftranked.enhancements.ranked.RankedClients;

public final class RankedEnhancements extends JavaPlugin implements TabExecutor {

	private final Features features = new Features();
	private RankedClients rankedClients;
	private FastCrystals fastCrystals;

	public Features features() {
		return features;
	}

	@Override
	public void onEnable() {
		saveDefaultConfig();
		reloadConfig();
		features.load(getConfig());

		var pm = getServer().getPluginManager();
		pm.registerEvents(new WindChargeCatch(this), this);
		pm.registerEvents(new FixedProjectiles(this), this);
		pm.registerEvents(new ShieldStun(this), this);
		pm.registerEvents(new AntiPearlGlitch(this), this);
		rankedClients = new RankedClients(this);
		rankedClients.enable();

		if (pm.isPluginEnabled("packetevents")) {
			fastCrystals = new FastCrystals(this);
			fastCrystals.enable();
		} else if (features.enabled(Feature.FAST_CRYSTALS)) {
			getLogger().warning("fast-crystals needs the PacketEvents plugin; it stays off until PacketEvents is installed.");
		}

		var cmd = getCommand("rankedenhancements");
		if (cmd != null) {
			cmd.setExecutor(this);
			cmd.setTabCompleter(this);
		}
	}

	@Override
	public void onDisable() {
		if (fastCrystals != null) {
			fastCrystals.disable();
		}
		if (rankedClients != null) {
			rankedClients.disable();
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		String sub = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "status";
		if (sub.equals("reload")) {
			reloadConfig();
			features.load(getConfig());
			rankedClients.sendAll();
			sender.sendMessage("Ranked Enhancements reloaded.");
		}
		sender.sendMessage("Ranked Enhancements " + getPluginMeta().getVersion());
		for (Feature f : Feature.values()) {
			String state = features.describe(f);
			if (f == Feature.FAST_CRYSTALS && fastCrystals == null && features.enabled(f)) {
				state += " (waiting for PacketEvents)";
			}
			sender.sendMessage(" " + f.key + ": " + state);
		}
		sender.sendMessage(" Eaglercraft Ranked players online: " + rankedClients.count() + "/" + Bukkit.getOnlinePlayers().size());
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		return args.length == 1 ? List.of("reload", "status") : List.of();
	}

}
