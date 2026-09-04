# Relic and team fixes 1.6.11

- Reapply anvil result and free repair cost next tick, after vanilla rejects
  rod + relic as an incompatible recipe. Validate the same view and both input
  stacks before publishing, so closing/changing inputs cannot resurrect items.
- Relic output needs one empty inventory slot. A full inventory consumes nothing;
  unsupported output click types cannot drop or duplicate the rod.
- Team leave/kick/disband resets personal adventure EXP and custom rank, including
  offline members. Startup reconciles existing orphan profiles; rank reads and
  joining also check membership. Profession XP and completed history are retained.
- Missing/disabled team module and unsuccessful team data loading do not authorize
  tier resets. Team YAML now uses strict loading rather than silently treating
  invalid YAML as an empty team list.

Install: back up plugin data, replace JAR and restart the server.
Live acceptance checks (not performed by automated tests):
1. Survival non-OP Java and Floodgate Bedrock, XP level 0: combine a Veliora rod
   and each relic type. Take result: exactly one rod and relic consumed, no XP fee.
2. Full inventory, rapid input swaps, close before next tick, shift-click, number
   keys: no lost stone, duplicate rod, or stale output.
3. Leave/kick/disband including offline players: personal tier returns to F;
   existing team members keep progress. Broken team YAML must not reset tiers.

Anvil upstream reference:
https://github.com/PaperMC/Paper/blob/main/paper-server/patches/sources/net/minecraft/world/inventory/AnvilMenu.java.patch
