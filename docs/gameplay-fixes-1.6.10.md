# 1.6.10

- Bentuk race: CHILD .75, ADULT 1.0, TALL 1.05; perubahan bentuk setiap 3 hari. Migrasi sekali dengan backup race.pre-forms-v2-*.yml; tidak mereset timestamp pemain.
- Reach tidak ditambah. Ukuran tinggi dikurangi agar posisi mata/hitbox lebih dekat vanilla.
- Demon/Dragonkin tidak lagi tahan panas sepanjang waktu. Pengurangan damage masing-masing 35%/30% aktif 5 detik sejak terkena api/lava, cooldown 5 menit tersimpan di PDC pemain. Bukan immunity. Fire resistance dari potion/plugin lain tidak dihapus.
- Warp tidak batal karena berjalan selama countdown. Waktu countdown dan pemeriksaan tujuan tetap berlaku.
- Kurung prefix formatter chat diberi warna dark gray di kedua sisi; isi pesan pemain tidak diubah.
- Rod berbayar memakai pengali harga 3.0, dapat diubah di settings.rods.price-multiplier (1 = harga dasar). Harga 0 tetap gratis. Harga yang ditampilkan dan dipotong berasal dari nilai yang sama.
- Admin profile: baris navigasi, inventory/status, moderasi ditata dalam grid; tombol kembali di tengah bawah. Konfirmasi tindakan tetap ada.
- Mace boss: tidak memakai kuota, cooldown charge, atau cap damage mace. Perhitungan final hit diteruskan ke HP virtual boss. Anti-reach tetap berlaku.
- Hit boss tidak lagi selalu dibatalkan; damage native kecil .001 mempertahankan collision projectile dan jalur hit mace, sementara damage penuh masuk HP virtual. HP native dipulihkan selama boss aktif. Ini bukan penggantian HP virtual dengan vanilla.
- Boss mendapat fallback serangan kontak ketika bounding box dekat dengan target dan line of sight tersedia, maksimum sekali per 1.5 detik (scheduler mengecek per detik). Damage tetap melalui event Bukkit, bukan memaksa melewati claim/freeze/god mode.

## Verifikasi
Build dan tes otomatis mencakup default config. Belum tes gameplay server Java/Bedrock.
Uji panah dan mace pada boss baru setelah restart, juga uji target baby/dewasa dalam survival tanpa god/freeze.
Jika damage tetap ditolak oleh RedProtect, Essentials god, arena protection atau plugin lain, diperlukan log/server test untuk mengidentifikasi listener yang membatalkan.
Stop server, backup, ganti JAR, start kembali. Hindari hot reload JAR.
