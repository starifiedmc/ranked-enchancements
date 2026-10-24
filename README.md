# Ranked Enhancements

A Paper plugin with the PvP mechanics from [Eaglercraft Ranked](https://eaglercraftranked.com), for any server.
Most of it works for every player on any client. Players on the Eaglercraft Ranked client also get no-latency
crystals and anchors.

Works on Paper 1.21.x and 26.2. Java 21 or newer. Download the jar from
[Releases](https://github.com/starifiedmc/ranked-enchancements/releases).

## Features

Each feature can be turned on or off in `config.yml`, either everywhere or only in the worlds you list.
The config file explains each one.

| Feature | What it does |
| --- | --- |
| `wind-charge-catch` | A wind charge thrown while falling fast and looking down breaks the fall with a small hop |
| `fixed-projectiles` | Pearls, wind charges, potions, arrows, tridents etc. fly exactly where you aim (no random spread) |
| `easy-stun-slam` | A mace hit within 0.4 s of your axe disabling a shield still smashes and ignores the hit cooldown |
| `shield-stun` | Your next hit after disabling a shield lands as a full hit instead of being eaten by the hit cooldown |
| `anti-pearl-glitch` | Pearls can't put you inside blocks or through doors, trapdoors, panes, slabs, walls or ceilings |
| `fast-crystals` | End crystals are placed and broken as soon as the click arrives instead of on the next tick (needs PacketEvents) |
| `no-latency-features` | Eaglercraft Ranked clients only, see below |

`/rankedenhancements` (alias `/renh`) shows what's on. `/rankedenhancements reload` applies config changes
without a restart. Permission: `rankedenhancements.admin` (ops by default).

## No-latency features

When an Eaglercraft Ranked player joins, their client says hello on the `rankedenhancements:main` plugin channel.
The server answers with one flag: whether no-latency features are on in that player's world. That's the whole
protocol.

With the flag on, the client shows crystal and anchor placements and explosions right away instead of
waiting for the server, the same way it already does for block breaking. The server still decides
everything with vanilla rules. If it disagrees, the client's guess is replaced by what the server sends. The
plugin also sends the blocks an explosion destroyed to these players immediately, instead of with the next
tick's updates.

The player sees a short "Ranked Enhanced" notice when they join a server running the plugin.

## Anticheats

Nothing here changes what clients are allowed to send. Hits, placements and movement still arrive as normal
Minecraft packets and go through the server's normal checks, so anticheats see the same traffic as always.
The plugin never accepts a hit, a placement or a position from a plugin message.

## Safety

The plugin message is two bytes each way, size-checked and rate-limited on both sides. A server can only switch
the flag on or off; it can't change the client's text, run anything, or make the client send anything. A
modified copy of this plugin can't do more than that either, because all the behaviour lives in the client.

## Building

```
./fetch-libs.sh   # compile-only API jars into libs/
./build.sh        # build/RankedEnhancements-<version>.jar
```

Needs JDK 25 to build (the jar itself runs on Java 21).

## Credits and license

`fast-crystals` is based on [FasterCrystals](https://github.com/mcpvp-club/FasterCrystals) by Jyguy (GPL-3.0).
Ranked Enhancements is released under the GNU General Public License v3.0, see [LICENSE](LICENSE).
