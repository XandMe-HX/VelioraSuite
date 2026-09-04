# Perbaikan 1.6.9

- Tulisan DALAM PERTARUNGAN berasal dari CombatTagListener, bukan FTB. Tidak dihapus.
- Race change cost dimigrasikan sekali menjadi 20000. Backup race.pre-20k-*.yml dibuat sebelum migrasi; setelahnya harga tetap dapat diedit.
- Angel tidak lagi mendapat Regeneration dari gerakan pada siang hari. Regen dari potion/enchant/plugin lain tidak dihapus.
- Menu utama Kits: Starter di 20, Build di 22, Food di 24. Premium pada tombol 7 membuka submenu seluruh kit premium. Claim masih wajib permission premium per level; admin tetap bypass.
- Clock di slot 45 (kiri bawah). Lima item Build preview di slot 11–15.
- Premium I–V: 5000,7500,10000,12500,15000. Aturan pertama gratis dan cooldown yang sudah ada dipertahankan.
- Food: 64 COOKED_CHICKEN dan 64 CARROT.
- Kits config versi 4 memigrasikan harga premium/food sekali, membuat backup kits.pre-v4-*.yml, tidak mereset data claim.
- Rod quest bukan quest board terpisah: syarat yang sudah ada adalah total successful catches dan unlock tier sebelumnya, plus biaya yang dikonfigurasi. GUI sekarang menampilkan kedua target/progres dan biaya; admin bypass ditandai, tidak menyembunyikan syarat.

Stop server, ganti JAR, start kembali untuk migrasi. Jangan hot reload JAR.
Build/test otomatis bukan tes gameplay; verifikasi permission premium dengan akun non-OP dan transaksi Vault pada server staging.
