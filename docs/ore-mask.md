# Veliora OreMask — EXPERIMENTAL, opt-in

This is a VelioraSuite packet HIDE implementation, not a switch for Paper Anti-Xray.
It does not edit world blocks or issue bans. OreWatch/combat detection are separate.
Paper's [Anti-Xray design](https://docs.papermc.io/paper/anti-xray/) and
[controller](https://github.com/PaperMC/Paper/blob/main/paper-server/src/main/java/io/papermc/paper/antixray/ChunkPacketBlockControllerAntiXray.java)
were references; no Paper internal implementation was copied.
PacketEvents is an external provided dependency, not bundled into the Suite JAR.

## Status and safe activation

**Not production-certified. No live Paper/Geyser or performance benchmark has been run.**
The default is `enabled: false`. Do not replace working protection on a live server
with this build before the checklist below passes. Maven tests are logic tests, not
evidence of network compatibility or low CPU usage.

1. Use a test server and back up server/plugin configuration.
2. Install PacketEvents 2.13.0 and the new Suite JAR. Keep the security module enabled.
3. In `plugins/VelioraSuite/modules/ore-mask.yml`, set `enabled: true`.
4. Default world names are `world` and `world_nether`; `boss`, `lobby`, `war_world` are excluded.
   Actual dimension keys are resolved from loaded Bukkit worlds on the main thread.
5. Disable Paper Anti-Xray in both world defaults and any world-specific overrides.
   Suite does not edit these files for you. Do not stack two ore obfuscators.
6. Restart the server. Config changes are restart-only, not security reload.
7. `/voremask` shows recipients, cached columns, masked count, skipped chunks, and errors.
   This command requires `veliorasuite.security.admin`. Players already online when the
   module is enabled must reconnect to establish packet state.

## Behavior

- Before a chunk is sent, hidden ores become stone/deepslate; Nether ores and ancient
  debris become netherrack. Original block states are retained in a recipient-specific cache.
- Only six-sided, fully covered ores are masked. Known opaque natural terrain counts
  as cover. Air, liquids, glass, slabs and unknown blocks do not.
- Single and batch block updates update the snapshot first. Newly exposed neighboring
  ores are restored after the change packet, grouped by section.
- No periodic world scans, world generation, forced chunk loads or asynchronous Bukkit block reads.
- Recipient caches are freed on chunk unload, respawn/dimension change and disconnect.
  Normal module shutdown sends restoration updates, then clears caches.
- Cache pressure passes new chunks through unprotected instead of discarding active
  masks. The counter and a rate-limited warning expose this protection gap.
- This processes packets for all supported recipients, not a separate Geyser exemption.
  Actual Geyser/Floodgate and ViaVersion compatibility remains to be tested.

## Known protection/performance limits

- Horizontal chunk edge ores and top/bottom boundary ores are intentionally exposed.
  This implementation is **not equivalent in coverage to Paper**.
- Exposed cave ores remain visible to Xray. This mode cannot prevent seed-based ore prediction.
- Custom columns taller than 512 blocks and ore-dense columns above the cap pass through.
- Cache is per recipient, not shared. Memory scales with players and view distance,
  bounded by configured column/ore limits. Bitsets plus sparse ore entries are retained;
  decoded packet sections are temporary. Defaults are not a measured memory guarantee.
- Every accepted nonempty chunk section is inspected once per outgoing recipient packet.
  There is no asynchronous worker or hard per-tick CPU budget; rapid travel can cost CPU.
  Do not claim this is lighter than Paper without profiling.
- Other plugins rewriting/cancelling packets after this listener, fake-block plugins,
  custom protocols, or world changes not sent to the client need compatibility testing.
- A packet exception stops masking for that recipient, attempts restoration and logs
  a warning. Reconnect is required; any error is a release blocker for production.
- Bans and honeypot evidence are not produced by this feature.

## Required staging checklist

- Java + Bedrock non-OP: enclosed diamond/deepslate ores and Nether ancient debris are
  hidden; naturally exposed ores, underwater ores and lava-exposed ores remain normal.
- Mine each of six neighboring faces; the correct ore appears immediately.
- Test Y=-64/0/16, negative X/Z, section edges and chunk edges; no ghost blocks.
- TNT, beds in Nether, pistons, FTB, custom enchant block breaks, FAWE edits, cancelled
  claim breaks and redstone ore updates do not leave stone disguises behind.
- Move lobby → world → Nether → war → world; death/respawn, reconnect and chunk unload.
- Two players receive independent, correct views. No real block or drops are changed.
- Drive cache limits low, verify warnings/skipped count, then restore settings.
- Compare TPS/MSPT, heap, outgoing traffic and Bedrock FPS with the feature off/on during
  simultaneous travel and mining. Collect actual measurements before production use.

Rollback: set `enabled: false` and restart; reconnecting refreshes all client blocks.
No world conversion or ore regeneration is needed because world data was never changed.
