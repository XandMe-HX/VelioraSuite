# VelioraSuite

VelioraSuite adalah plugin modular untuk server Minecraft Veliora Gardens.

Author / owner name yang dipakai di source, config, guide, command, dan dokumentasi: **XandMe**.

## Status

Clean rebuild branch `clean-core`.

Tahap saat ini:

- Core clean sudah dipisah ke manager.
- VelioraGuide sudah aktif sebagai module.
- VelioraAnnouncement sudah aktif sebagai module.
- Module lain sudah mulai dibuat sebagai skeleton/planned module.
- Logic besar seperti Fishing, Trader, Boss, ClearLag, Chat, Security, Quest, dan LoginSecurity dikerjakan bertahap.

## Target

- Paper / Purpur 1.21.8
- Java 21
- Maven
- Package utama: `id.velioragardens.veliorasuite`

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

## Catatan Development

Jangan langsung menumpuk semua logic di `VelioraSuite.java` atau `Module.java`.

Setiap module besar nanti wajib dipisah menjadi manager, command, listener, data manager, model, dan class pendukung lain sesuai kebutuhan.
