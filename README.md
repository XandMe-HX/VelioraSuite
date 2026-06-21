# VelioraSuite v1.6.0

VelioraSuite adalah plugin modular untuk server **Veliora Gardens**.
Semua contoh memakai nama **XandMe**. Nama asli owner tidak dipakai di source, config, README, atau guide.

## Status v1.6.0

Versi ini adalah revisi dari hasil test server langsung:

- Team prefix chat diperkuat.
- VelioraTeam punya `/vteam disband` untuk owner.
- VelioraFishing ditambah minigame actionbar dan GUI jual ikan.
- VelioraClearLag menghitung jumlah item stack dan punya mob limiter.
- VelioraQuest dibuat GUI dan target quest lebih jelas.
- VelioraSkills dibuat lebih nyambung ke quest + actionbar + PlaceholderAPI.
- VelioraTrader dibuat lebih mirip plugin VelioraTrader: lokasi spawn, NPC ringan, despawn, GUI merchant.
- VelioraBoss dibuat lebih mirip VelioraBossSystem: rarity, bossbar, skill, true damage, last hit, top damage reward.
- VelioraGuide bisa mendorong chat lama ke atas saat pindah page.

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

## Command penting

```text
/vs
/vclearlag
/vanti
/vquest
/skills atau /sk
/vtrader atau /vtr
/vf
/vboss atau /vbs
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

## Catatan chat team

Jika team prefix belum muncul di EssentialsChat, tambahkan PlaceholderAPI ini ke format chat Essentials:

```text
%veliorasuite_team_prefix%
```

Contoh format:

```text
%veliorasuite_team_prefix%{PREFIX}{DISPLAYNAME} &8: &f{MESSAGE}
```

## Catatan safe test

Tetap test di server test dulu sebelum menggantikan plugin lama. Jangan langsung hapus ClearLag lama, EssentialsChat, AuraSkills, atau plugin penting lain sebelum fitur pengganti benar-benar cocok di server kamu.
