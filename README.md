# VelioraSuite

VelioraSuite adalah plugin modular untuk server Minecraft Veliora Gardens.

## OreMask eksperimental (1.6.6)

Penyamaran ore melalui packet buatan Suite, bukan mengaktifkan Anti-Xray Paper.
**Default nonaktif; belum diuji live Java/Bedrock maupun performanya.** Memerlukan
PacketEvents 2.13.0. World target: `world`, `world_nether`. Tidak melakukan ban.
Gunakan `/voremask` untuk status admin. Baca [aktivasi, batas perlindungan dan checklist uji](docs/ore-mask.md)
sebelum menyalakannya. Jangan gunakan bersamaan dengan obfuscator Paper.
Adaptasi desain Paper mencakup pemeriksaan tetangga antar-chunk, tabel indeks block-state,
dan batas tinggi pemrosesan. Ini bukan salinan utuh mesin internal Paper.

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

## Interactive Chat

VelioraSuite mempunyai interactive chat bawaan yang ringan, sehingga server
tidak perlu memasang plugin InteractiveChat untuk kebutuhan utama.

- `[/spawn]`, `[/home base]`, dan command yang diizinkan menjadi tombol klik.
- `[item]` membagikan item di tangan dengan hover Minecraft asli.
- `[inv]` atau `[inventory]` menampilkan isi tas pemain saat di-hover.
- `[ender]` atau `[enderchest]` menampilkan isi Ender Chest pemain saat di-hover.
- Nama pengirim dapat diklik untuk menulis `/msg Nama`; hover nama menampilkan
  HP, dunia, lokasi, dan timnya.
- `[@Nama]` menyarankan `/tpa Nama`.

Pengaturan berada di `plugins/VelioraSuite/modules/chat.yml`. Share inventory
memiliki cooldown sendiri agar chat tidak spam dan tidak membebani server.
Tampilan inventory adalah hover teks interaktif agar tetap aman untuk Java dan
Bedrock; plugin tidak membuka inventory asli pemain kepada orang lain.

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

## Guild Petualang

Module `adventure` menyatukan Team, Fishing, Boss/Dungeon, dan Quest menjadi lima misi guild harian. Misi hanya dapat diterima dan dikerjakan saat minimal dua anggota team online. Koordinat tujuan, hadiah, tingkat kesulitan, 50 template misi, level, dan rank dapat diatur di:

```text
plugins/VelioraSuite/modules/adventure.yml
```

Command pemain:

```text
/vgpetualang
/vgteam
```

Command admin:

```text
/vgpetualang reload
/vgpetualang setrank <player> <F|E|D|C|B|A|S|SS|SSS>
/vgpetualang setrank <player> <nama rank khusus>
```

Contoh rank khusus owner:

```text
/vgpetualang setrank XandMe &4RAJA IBLIS
```

Placeholder TAB/PlaceholderAPI:

```text
%veliorasuite_adventure_rank%
%veliorasuite_adventure_rank_plain%
%veliorasuite_adventure_rank_next%
%veliorasuite_adventure_exp%
%veliorasuite_adventure_exp_next%
%veliorasuite_adventure_exp_remaining%
%veliorasuite_adventure_level%
%veliorasuite_adventure_level_exp%
%veliorasuite_adventure_level_exp_required%
%veliorasuite_adventure_quests_completed%
%veliorasuite_guild_level%
%veliorasuite_guild_exp%
%veliorasuite_guild_quests_completed%
%veliorasuite_integration_war%
%veliorasuite_integration_gacha%
%veliorasuite_integration_ftb%
%veliorasuite_integrations_online%
```

Contoh TAB belowname:

```text
&aRank &f%veliorasuite_adventure_rank% &8| &aLv. &f%veliorasuite_adventure_level%
```

Module `notifications` menampilkan peringatan layar saat masuk Nether/The End yang tidak memakai keepInventory dan notifikasi layar saat pemain disebut menggunakan `@Nama`.

Status koneksi plugin dapat diperiksa tanpa menjadikan plugin lain sebagai dependency wajib:

```text
/veliorasuite integrations
```

VelioraWar, VelioraGacha, dan VelioraFTB tetap berupa JAR serta folder data terpisah. Jika salah satunya tidak aktif, VelioraSuite tetap berjalan dan tombol menu memakai pesan fallback aman.

## Clear Lag

Clear lag tidak ditangani oleh VelioraSuite.
Server Veliora Gardens memakai plugin external khusus clear lag.

## Catatan Development

Jangan langsung menumpuk semua logic di `VelioraSuite.java` atau `Module.java`.

Setiap module besar dipisah menjadi manager, command, listener/task, data manager, model, dan class pendukung lain sesuai kebutuhan.

Jangan menaikkan target compile dari Java 21 tanpa alasan kuat. Semua module baru wajib aman untuk Java 21 dan Paper/Purpur 1.21.8.
