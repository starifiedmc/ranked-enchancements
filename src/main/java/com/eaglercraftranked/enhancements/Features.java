// SPDX-License-Identifier: GPL-3.0-or-later
package com.eaglercraftranked.enhancements;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class Features {

	private record Entry(boolean enabled, boolean onlyInWorlds, Set<String> worlds) {
	}

	private volatile Map<Feature, Entry> entries = new EnumMap<>(Feature.class);

	void load(FileConfiguration config) {
		Map<Feature, Entry> next = new EnumMap<>(Feature.class);
		for (Feature f : Feature.values()) {
			ConfigurationSection s = config.getConfigurationSection(f.key);
			if (s == null) {
				next.put(f, new Entry(false, false, Set.of()));
				continue;
			}
			Set<String> worlds = new HashSet<>();
			for (String w : s.getStringList("worlds")) {
				worlds.add(w.toLowerCase(Locale.ROOT));
			}
			next.put(f, new Entry(s.getBoolean("enabled", false), s.getBoolean("only-in-worlds", false), Set.copyOf(worlds)));
		}
		entries = next;
	}

	public boolean enabled(Feature f) {
		Entry e = entries.get(f);
		return e != null && e.enabled;
	}

	public boolean on(Feature f, World world) {
		Entry e = entries.get(f);
		if (e == null || !e.enabled) {
			return false;
		}
		return !e.onlyInWorlds || world != null && e.worlds.contains(world.getName().toLowerCase(Locale.ROOT));
	}

	String describe(Feature f) {
		Entry e = entries.get(f);
		if (e == null || !e.enabled) {
			return "off";
		}
		return e.onlyInWorlds ? "on in " + (e.worlds.isEmpty() ? "no worlds" : String.join(", ", e.worlds)) : "on everywhere";
	}

}
