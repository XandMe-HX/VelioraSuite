# VelioraSuite

VelioraSuite adalah plugin modular untuk server Minecraft Veliora Gardens.

Author / owner name yang dipakai di source, config, guide, command, dan dokumentasi: **XandMe**.

## Status

Clean rebuild branch `clean-core`.

Tahap saat ini:

- Core clean sudah dipisah ke manager.
- VelioraGuide sudah aktif sebagai module.
- VelioraAnnouncement sudah aktif sebagai module.
- VelioraKits sudah aktif sebagai module.
- VelioraReport sudah aktif sebagai module.
- VelioraTeam sudah aktif sebagai module.
- VelioraChat sudah aktif sebagai module.
- Logic besar seperti Fishing, Trader, Boss, ClearLag, Security, Quest, dan LoginSecurity dikerjakan bertahap.

## Target

- Paper / Purpur 1.21.8
- Java 21 recommended
- Maven
- Package utama: `id.velioragardens.veliorasuite`

## Compatibility

- Target server tetap **Paper/Purpur 1.21.8 + Java 21**.
- Target compile VelioraSuite tetap **Java 21**.
- `pom.xml` harus tetap memakai compiler release Java 21.
- Jangan menaikkan source/target/release dari Java 21 tanpa keputusan khusus.
- Java 21 adalah runtime utama yang direkomendasikan untuk server.
- Java 25 may work as runtime, tetapi bukan target utama project.
- Jangan memakai API Java 25 atau fitur bahasa Java 25.
- Plugin harus tetap compile Java 21 agar kompatibel dan stabil untuk Paper/Purpur 1.21.8.

## Struktur Core

```text
src/main/java/id/velioragardens/veliorasuite/
├── VelioraSuite.java
├── api/
│   ├── VelioraModule.java
│   └── PlannedModule.java
├── command/
│   ├── VelioraCommand.java
│   └── DisabledCommand.java
├── core/
│   ├── ConfigManager.java
│   ├── MessageManager.java
│   ├── ModuleManager.java
│   └── HookManager.java
└── module/
    ├── guide/
    ├── announcement/
    ├── loginsecurity/
    ├── team/
    ├── kits/
    ├── report/
    ├── chat/
    ├── fishing/
    ├── skills/
    ├── quest/
    ├── boss/
    └── security/
```

## File Runtime

```text
plugins/VelioraSuite/
├── config.yml
├── messages.yml
├── modules.yml
├── database/
│   └── veliorasuite.db
├── data/
└── modules/
    ├── guide.yml
    ├── announcement.yml
    ├── loginsecurity.yml
    ├── team.yml
    ├── kits.yml
    ├── report.yml
    ├── chat.yml
    ├── fishing.yml
    ├── skills.yml
    ├── quest.yml
    ├── boss.yml
    └── security.yml
```

## Core Command

- `/vs`
- `/vs reload`
- `/vs modules`
- `/vs version`
- `/vs debug`

## VelioraGuide

Command:

- `/vguide`
- `/vguide <page>`
- `/vrules`
- `/vrules <page>`
- `/vrank`
- `/vrank <page>`
- `/vproduct`
- `/vproduct <page>`
- `/vguide reload`

File config:

```text
plugins/VelioraSuite/modules/guide.yml
```

## VelioraAnnouncement

Command:

- `/vannounce status`
- `/vannounce reload`
- `/vannounce send <id>`

File config:

```text
plugins/VelioraSuite/modules/announcement.yml
```

## VelioraKits

Command:

- `/kits`
- `/kits list`
- `/kits claim <kit>`
- `/kits preview <kit>`
- `/kits buy <kit>`
- `/kits cooldown`
- `/kits reload`

File config:

```text
plugins/VelioraSuite/modules/kits.yml
```

## VelioraReport

Command:

- `/report <player> <alasan>`
- `/report bug <alasan>`
- `/reports list`
- `/reports view <id>`
- `/reports close <id> <catatan>`
- `/reports reopen <id>`
- `/reports reload`

File config/data:

```text
plugins/VelioraSuite/modules/report.yml
plugins/VelioraSuite/data/reports.yml
```

## VelioraTeam

Command:

- `/team create <nama>`
- `/team invite <player>`
- `/team accept`
- `/team leave`
- `/team leave confirm`
- `/team list`
- `/team chat <pesan>`
- `/team upgrade`
- `/team setowner <team> <player>`
- `/team delete <team>`
- `/team info <team>`
- `/team reload`

Aturan nama team baru:

```text
^[A-Z]{3,5}$
```

Nama team baru wajib huruf besar A-Z saja, minimal 3 huruf dan maksimal 5 huruf.
Tidak boleh huruf kecil, angka, spasi, simbol, emoji, atau tanda `-`.
Contoh valid: `SHDW`, `NOVA`, `VOID`, `SKY`, `TEAM`.
Contoh tidak valid: `Shadow`, `shadow`, `SHADOW`, `SHDW1`, `SH-DW`, `SH DW`, `🔥ABC`.

Catatan: team lama yang sudah ada tetap dibaca, tetapi create team baru wajib mengikuti aturan baru.

File config/data:

```text
plugins/VelioraSuite/modules/team.yml
plugins/VelioraSuite/data/teams.yml
```

## VelioraChat

Command:

- `/vchat help`
- `/vchat status`
- `/vchat reload`

File config:

```text
plugins/VelioraSuite/modules/chat.yml
```

PlaceholderAPI identifier:

```text
veliorasuite
```

Placeholder:

```text
%veliorasuite_team_name%
%veliorasuite_team_tag%
%veliorasuite_player_name%
```

## TAB Nametag Team Tag

VelioraSuite tidak menyimpan team tag ke Essentials userdata dan tidak mengubah LuckPerms prefix player.
Team tag disediakan lewat PlaceholderAPI supaya bisa dipakai oleh TAB plugin.

Format default team tag:

```text
&f【&b&l%team%&f】&f 
```

Gunakan placeholder ini di depan `tagprefix` rank di `TAB/groups.yml`:

```yaml
owner:
  tabprefix: '&6&l【 &6&lᴏᴡɴᴇʀ &6&l】&f '
  tagprefix: '%veliorasuite_team_tag%&6&l【 &6&lᴏᴡɴᴇʀ &6&l】&f '

warga:
  tabprefix: '&7&l【 &7&lᴡᴀʀɢᴀ &7&l】&f '
  tagprefix: '%veliorasuite_team_tag%&7&l【 &7&lᴡᴀʀɢᴀ &7&l】&f '
```

Hasil jika player punya team:

```text
【SHDW】 【 OWNER 】 XandMe
```

Hasil jika player tidak punya team:

```text
【 OWNER 】 XandMe
```

Test placeholder:

```text
/papi parse me %veliorasuite_team_tag%
```

## Catatan Development

Jangan langsung menumpuk semua logic di `VelioraSuite.java` atau `Module.java`.

Setiap module besar nanti wajib dipisah menjadi manager, command, listener, data manager, model, dan class pendukung lain sesuai kebutuhan.

Jangan menaikkan target compile dari Java 21 tanpa alasan kuat. Semua module baru wajib tetap aman untuk Java 21 dan Paper/Purpur 1.21.8.
