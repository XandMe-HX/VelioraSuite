# VelioraSuite v1.5.0

VelioraSuite adalah plugin modular untuk server **Veliora Gardens**.
Semua contoh memakai nama **XandMe**. Nama asli owner tidak dipakai di source, config, README, atau guide.

## Status v1.5.0

Versi ini dibuat sebagai **full all-in-one build** dengan module utama:

- VelioraClearLag
- VelioraAnti
- VelioraQuest
- VelioraSkills
- VelioraTrader
- VelioraFishing
- VelioraBoss
- VelioraRewards
- VelioraChat
- VelioraTeam
- VelioraGuide
- VelioraSecurity
- Veliora Login Security / VLS
- VelioraReport
- VelioraAnnouncement
- VelioraKits

## Build

```bash
mvn clean package
```

Hasil jar:

```text
target/VelioraSuite.jar
```

Atau gunakan GitHub Actions di tab **Actions**.

## Cara pasang

1. Build `VelioraSuite.jar`.
2. Upload ke folder `plugins/` server.
3. Restart server.
4. Cek `/plugins` dan `/vs modules`.
5. Edit config di `plugins/VelioraSuite/modules/`.
6. Gunakan `/vs reload` atau command reload module masing-masing.

## Catatan aman

- Jangan hapus plugin economy/Vault/LuckPerms sebelum fitur VelioraSuite selesai dites di server test.
- Untuk Essentials, matikan `/kit` dan `/kits` kalau ingin VelioraKits yang mengambil command tersebut.
- Untuk chat, biarkan `modules/chat.yml -> format.enabled: false` kalau masih pakai EssentialsChat.
- ClearLag punya whitelist dan proteksi entity penting.
- LoginSecurity menyimpan password dalam hash SHA-256 + salt, bukan password mentah.

## Command utama

```text
/vs
/vclearlag
/vanti
/vquest
/skills atau /sk
/vtrader
/vf
/vboss
/daily
/vchat
/vteam
/vguide
/vrules
/vproduct
/vsecurity
/register
/login
/logout
/changepass
/vls
/report
/bugreport
/vreport
/vannounce
/kit atau /kits
/vkit
/vkits
```
