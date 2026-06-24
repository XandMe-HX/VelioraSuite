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
- Logic besar seperti Fishing, Trader, Boss, ClearLag, Chat, Security, Quest, dan LoginSecurity dikerjakan bertahap.

## Target

- Paper / Purpur 1.21.8
- Java 21
- Maven
- Package utama: `id.velioragardens.veliorasuite`

## Compatibility

- Target compile VelioraSuite tetap **Java 21**.
- `pom.xml` harus tetap memakai compiler release Java 21.
- Java 21 adalah runtime utama yang direkomendasikan untuk server.
- Java 25 mungkin bisa berjalan sebagai runtime, tetapi bukan target utama project.
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
- `/team reload`

File config/data:

```text
plugins/VelioraSuite/modules/team.yml
plugins/VelioraSuite/data/teams.yml
```

## Catatan Development

Jangan langsung menumpuk semua logic di `VelioraSuite.java` atau `Module.java`.

Setiap module besar nanti wajib dipisah menjadi manager, command, listener, data manager, model, dan class pendukung lain sesuai kebutuhan.

Jangan menaikkan target compile dari Java 21 tanpa alasan kuat. Semua module baru wajib tetap aman untuk Java 21 dan Paper/Purpur 1.21.8.
