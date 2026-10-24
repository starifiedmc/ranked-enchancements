// SPDX-License-Identifier: GPL-3.0-or-later
package com.eaglercraftranked.enhancements;

public enum Feature {

	WIND_CHARGE_CATCH("wind-charge-catch"),
	FIXED_PROJECTILES("fixed-projectiles"),
	PEARL_CATCH("pearl-catch"),
	EASY_STUN_SLAM("easy-stun-slam"),
	SHIELD_STUN("shield-stun"),
	ANTI_PEARL_GLITCH("anti-pearl-glitch"),
	FAST_CRYSTALS("fast-crystals"),
	NO_LATENCY("no-latency-features");

	public final String key;

	Feature(String key) {
		this.key = key;
	}

}
