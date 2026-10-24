/*
 * Copyright (C) 2023-2026 Jyguy (FasterCrystals, https://github.com/mcpvp-club/FasterCrystals)
 * Modified for Ranked Enhancements by Eaglercraft Ranked, 2026.
 * Licensed under the GNU General Public License v3 or later; see LICENSE.
 */
package com.eaglercraftranked.enhancements.crystals;

final class CrystalUser {

	enum Last {
		ANIMATION, START_DIGGING, ATTACK, INV_DROP, CREATIVE_INV_ACTION, IGNORE, MISC
	}

	volatile Last lastPacket = Last.MISC;
	volatile boolean ignoreAnim;

}
