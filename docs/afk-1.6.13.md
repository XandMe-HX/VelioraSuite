# Progress 3: AFK

- Removed all TAB hide/show calls. AFK never replaces the regular nametag.
- Standalone TextDisplay follows the player's actual scaled height, without
  passenger attachment. Updates only moving AFK markers every two ticks with
  interpolation, rather than reattaching a passenger every second.
- Essentials AfkStatusChangeEvent is mirrored next tick after cancellation/state
  handling. A one-second reconciliation remains for older Essentials.
- Essentials owns inactivity timing and movement rules; this update does not
  silently change its auto-afk timeout. Without Essentials, warp auto-seconds is used.
- Initialize and clear AFK reward timer (previous missing initial timestamp meant
  rewards could wait forever), and clear manual mode on leaving AFK.

Restart after replacing the JAR. Live Java/Bedrock checks still required:
TAB visible before/during/after AFK, Essentials movement/chat/command transitions,
manual toggle, riding/sitting/swimming/scaled races, world teleport and logout.
Check Essentials auto-afk if the desired idle threshold differs from its config.

Upstream event:
https://github.com/EssentialsX/Essentials/blob/2.x/Essentials/src/main/java/net/ess3/api/events/AfkStatusChangeEvent.java
