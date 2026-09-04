# Progress 2: OreWatch and OreMask

- Fresh installations enable the own PacketEvents mask. Existing explicit disabled
  configs stay respected: /voremask enable then restart enables it.
- Requires PacketEvents 2.13.0; Paper Anti-Xray must be disabled to avoid stacking.
  /voremask reports running sessions, actual world keys, mask operations, skipped
  chunks and errors. A missing dependency/disabled module is not active protection.
- Owner/admin bypass no longer suppresses read-only mining evidence; explicit
  /vxray exempt, creative/spectator and FTB secondary breaks still do.
- Break accounting runs at MONITOR with cancelled events ignored. Same coordinates
  are deduplicated within the retained hour. Counts are manual ore blocks, not
  inventory items/Fortune drops. All *_ORE types including quartz are counted.
- Cave evidence now needs a connected spacious air/water region, bounded to radius
  four and 256 processed cells per component, without loading chunks. Two exposed
  faces or a narrow mined tunnel are insufficient.
- Cave proportion compares visible rare blocks to rare blocks only; transitions
  involving cave-exposed ores or non-rare destinations are not suspicious paths.
  Cave-dominant mining without path evidence no longer escalates solely on totals.
- No auto-ban added. HIGH is a review signal, not proof of cheating.

Limits: mask remains experimental, only fully enclosed ores are hidden. Exposed
surface/cave ore is intentionally visible. Exhausted cache or packet errors can
leave chunks unprotected (reported in /voremask). Existing players must reconnect.
Placed ore exclusions are session-local; regenerated ore at repeated coordinates
is intentionally excluded for the deduplication window.

Live checks still required on Java/Bedrock: non-OP and owner, enclosed ores versus
large cave, digging/reveal, world/nether travel, 100 manual breaks with Fortune,
cancelled breaks, FTB secondary mining, cache use and server tick times.
