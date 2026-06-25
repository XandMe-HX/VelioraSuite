# VelioraSuite

VelioraSuite adalah plugin modular untuk server Minecraft Veliora Gardens.

Author / owner name yang dipakai di source, config, guide, command, dan dokumentasi: **XandMe**.

## Status Module

Module aktif phase 1:

- VelioraGuide
- VelioraAnnouncement
- VelioraLoginSecurity
- VelioraKits
- VelioraReport
- VelioraTeam
- VelioraChat
- VelioraSecurity
- VelioraSkills

Catatan:

- Clear lag memakai plugin external, bukan VelioraSuite.
- VelioraQuest belum dibuat di VelioraSkills. VelioraSkills hanya menyediakan mana system dan API internal.

## Target

- Paper / Purpur 1.21.8
- Java 21 recommended
- Maven
- Package utama: `id.velioragardens.veliorasuite`
- Target compile tetap Java 21.

## Command Core

- `/vs`
- `/vs reload`
- `/vs modules`
- `/vs version`
- `/vs debug`

## VelioraSkills

VelioraSkills mengatur mana player untuk Veliora Gardens.
Mana ini disiapkan agar bisa dipakai oleh VelioraQuest nanti.

Command:

- `/vskills help`
- `/vskills status`
- `/vskills reload`
- `/vskills mana`
- `/vskills mana <player>`
- `/vskills mana set <player> <amount>`
- `/vskills mana add <player> <amount>`
- `/vskills mana remove <player> <amount>`
- `/vskills mana reset <player>`

Alias:

- `/vski`
- `/vmana`
- `/mana`

Tidak ada command `/skills`, supaya tidak bentrok dengan AuraSkills.

File config/data:

```text
plugins/VelioraSuite/modules/skills.yml
plugins/VelioraSuite/data/skills.yml
```

Mana default:

```text
10/10 Mana
```

Daily reset default:

```text
00:00 waktu server
```

Actionbar bisa menampilkan:

```text
health | money | ping | mana
```

PlaceholderAPI identifier:

```text
veliorasuite
```

Placeholder mana:

```text
%veliorasuite_mana%
%veliorasuite_mana_max%
%veliorasuite_mana_bar%
%veliorasuite_mana_percent%
```

Contoh TAB belowname:

```text
%veliorasuite_mana% Mana
```

atau:

```text
&b☯ &f%veliorasuite_mana%/%veliorasuite_mana_max%
```

Placeholder lama tetap dipakai:

```text
%veliorasuite_team_name%
%veliorasuite_team_tag%
%veliorasuite_player_name%
```

## Clear Lag

Clear lag tidak ditangani oleh VelioraSuite.
Server Veliora Gardens memakai plugin external khusus clear lag.

## Catatan Development

Jangan langsung menumpuk semua logic di `VelioraSuite.java` atau `Module.java`.

Setiap module besar dipisah menjadi manager, command, listener/task, data manager, model, dan class pendukung lain sesuai kebutuhan.

Jangan menaikkan target compile dari Java 21 tanpa alasan kuat. Semua module baru wajib aman untuk Java 21 dan Paper/Purpur 1.21.8.
