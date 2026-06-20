# VelioraSuite

VelioraSuite adalah plugin modular untuk server Minecraft **Veliora Gardens**.

Target:
- Paper/Purpur 1.21.x
- Java 21
- Maven
- Vault, LuckPerms, PlaceholderAPI sebagai soft dependency

## Module utama

- VelioraTeam: team berbayar, prefix chat `【TEAM】`, admin/member, upgrade slot, team chat `/vteam msg`
- VelioraGuide: panduan server berbasis halaman, `/vguide 1`, `/vrules 1`, `/vproduct 1`
- VelioraSecurity: block `/pl`, `/plugins`, `/op`, `/lp`, tab complete, dan command sensitif
- Veliora Login Security: `/register`, `/login`, `/logout`, `/changepass`, `/vls`
- VelioraReport: `/report`, `/bugreport`, `/vreport list/info/close/banip`
- VelioraAnnouncement: broadcast otomatis setiap beberapa menit
- VelioraKits: GUI kit lewat `/kit` atau `/kits`, permission premium via LuckPerms
- VelioraFishing: fishing custom tanpa AuraSkills, ikan vanilla, common, epic, legend, mythic, secret
- ClearLag, Anti, Quest, Skills, Trader, Boss, Rewards, Chat sebagai pondasi module lanjutan

## Build JAR

Pakai GitHub Actions:

1. Upload semua file ke repo GitHub.
2. Buka tab **Actions**.
3. Jalankan workflow **Build VelioraSuite**.
4. Download artifact `VelioraSuite`.
5. Ambil `VelioraSuite.jar`.
6. Upload ke folder `plugins/` server.
7. Restart server.

## Catatan command bentrok

VelioraTeam **tidak memakai `/team`** karena command itu bawaan Minecraft/vanilla.
Gunakan:

```text
/vteam create <nama>
/vteam msg <pesan>
/veliorateam
```

VelioraKits memakai `/kit` dan `/kits`. Jika EssentialsX masih memakai kit, matikan command kit di config Essentials.

## Config

Semua config module ada di:

```text
plugins/VelioraSuite/modules/
```

Setiap file YAML sudah diberi penjelasan di bagian atas agar bisa diedit mandiri.
