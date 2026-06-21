# VelioraSuite

VelioraSuite adalah plugin modular untuk server Minecraft Veliora Gardens.

## Status

Clean rebuild tahap ketiga: Core Plugin + VelioraGuide + VelioraAnnouncement.

## Fitur Core

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

Cara edit:

1. Buka `guide.yml`.
2. Tambahkan atau ubah halaman di `sections.guide.pages`, `sections.rules.pages`, atau `sections.product.pages`.
3. Jalankan `/vguide reload` atau `/vs reload`.

## VelioraAnnouncement

Command:

- `/vannounce status`
- `/vannounce reload`
- `/vannounce send <id>`

File config:

```text
plugins/VelioraSuite/modules/announcement.yml
```

Cara edit:

1. Buka `announcement.yml`.
2. Atur `settings.interval-seconds` untuk jeda broadcast.
3. Atur `settings.mode` menjadi `RANDOM` atau `SEQUENTIAL`.
4. Tambahkan pesan baru di `announcements`.
5. Jalankan `/vannounce reload` atau `/vs reload`.

## Target

- Paper / Purpur 1.21.8
- Java 21
- Maven

## Catatan

Semua contoh memakai nama **XandMe**. Nama asli owner tidak dipakai di source, config, README, atau guide.

## Author

XandMe
