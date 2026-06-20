# VelioraSuite

VelioraSuite adalah plugin modular untuk server Minecraft **Veliora Gardens**.
Project ini dibuat untuk menyatukan beberapa fitur penting server ke dalam satu plugin yang rapi dan bisa dikustom dari YAML.

> Semua contoh nama player di dokumentasi memakai **XandMe**. Jangan memakai nama asli di config, guide, README, atau pesan publik.

## Target

- Minecraft Paper/Purpur 1.21.x
- Java 21
- Maven
- Vault + LuckPerms + PlaceholderAPI support

## Module

- VelioraTeam
- VelioraGuide
- VelioraSecurity
- Veliora Login Security / VLS
- VelioraReport
- VelioraAnnouncement
- VelioraKits
- VelioraFishing
- ClearLag
- Anti
- Quest
- Skills
- Trader
- Boss
- Rewards
- Chat

## Update v4

- `/team` tidak dipakai. Gunakan `/vteam` atau `/veliorateam`.
- VelioraKits sekarang support beli kit pakai coin/Vault.
- Kit bisa gratis, permission-only, coin-only, atau permission + coin.
- GUI kit menampilkan harga, status, permission, dan cooldown.
- Guide bagian Kits sudah ditambah contoh dan cara pakai kit berbayar.
- Semua config module diberi komentar cara edit agar mudah mandiri.

## Command penting

```text
/vs
/vguide
/vrules
/vproduct

/vteam create <nama>
/vteam msg <pesan>
/vteam leave
/yes
/no

/register <password> <confirmPassword>
/login <password>
/logout
/changepass <oldPassword> <newPassword>
/vls reload
/vls setpass <player> <newPassword>

/report <player> <alasan>
/bugreport <pesan>
/vreport list
/vreport info <id>
/vreport close <id>

/kit
/kits
/vkit reload
/vkits give <player> <kit>
/vkits reset <player> <kit>

/vf top
/vf stats
/vf sell
/vf reload
```

## Permission premium kits

```text
veliorasuite.kits.premium.1
veliorasuite.kits.premium.2
veliorasuite.kits.premium.3
```

Contoh LuckPerms:

```text
/lp group premium permission set veliorasuite.kits.premium.1 true
/lp group premiumplus permission set veliorasuite.kits.premium.2 true
/lp group ultimate permission set veliorasuite.kits.premium.3 true
```

## Build JAR

GitHub Actions sudah disediakan di:

```text
.github/workflows/build.yml
```

Cara build:

1. Upload semua file ke repo GitHub dengan struktur folder yang benar.
2. Buka tab **Actions**.
3. Jalankan workflow **Build VelioraSuite**.
4. Download artifact `VelioraSuite.jar`.
5. Upload ke folder `plugins` server.
6. Restart server.

## Struktur repo yang benar

Root repo harus seperti ini:

```text
.github/
src/
.gitignore
README.md
pom.xml
```

Jangan upload file `.java` langsung ke root repo.
File Java harus berada di:

```text
src/main/java/id/velioragardens/veliorasuite/
```
