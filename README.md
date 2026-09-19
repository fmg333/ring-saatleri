# Ring saatleri

G1 – 1100 arası üç ringin seferleri. Tek repo, iki çıktı:

- **iPhone (ve isteyen Android):** GitHub Pages adresi → Safari'de aç → *Ana Ekrana Ekle*. Çevrimdışı çalışır.
- **Android:** GitHub Actions her push'ta APK üretir, *Releases* sayfasından indirilir. İnternet gerekmez.

## Kurulum (bir kez)

1. github.com → **New repository** → ad: `ring-saatleri`, **Public** (ücretsiz hesapta Pages için şart) → Create.
2. Bu klasörü repo'ya gönder. `.github` ve `.nojekyll` gizli dosyalar; web'den yüklerken atlanabilir, git ile gönder:
   ```
   cd ring-saatleri
   git init -b main
   git add .
   git commit -m "Ring saatleri"
   git remote add origin https://github.com/<kullanici>/ring-saatleri.git
   git push -u origin main
   ```
3. **Settings → Pages** → Source: *Deploy from a branch* → Branch: `main`, klasör `/ (root)` → Save.
   1-2 dakika sonra adres: `https://<kullanici>.github.io/ring-saatleri/`
4. **Actions** sekmesinde "APK üret" iş akışı otomatik başlar (yeşil tik ~3-4 dk). Biterse **Releases** altında `ring-saatleri.apk` görünür.

## Telefona kurma

**iPhone:** Safari'de Pages adresini aç → Paylaş → *Ana Ekrana Ekle*. (Chrome değil, Safari; ana ekrana ekleme oradan yapılıyor.)

**Android (APK):** Telefonda `github.com/<kullanici>/ring-saatleri/releases/latest` → `ring-saatleri.apk` indir → aç → "Bu kaynaktan yüklemeye izin ver" → Yükle. Sonraki sürümler üstüne kurulur, kaldırmak gerekmez.

**Android (adres):** Chrome'da Pages adresi → menü (⋮) → *Ana ekrana ekle*. APK ile aynı şeyi yapar; iki yöntemden birini seçmek yeterli.

## Saatler değişince

1. `ringsaatleri.xlsx` dosyasını aynı düzende güncelle.
2. `python ringsaatleri_to_json.py ringsaatleri.xlsx index.html`
3. `sw.js` içindeki `ring-v1` → `ring-v2` (Pages'ten kuranların önbelleği yenilensin).
4. `git add . && git commit -m "Saatler güncellendi" && git push`

Pages birkaç dakikada güncellenir, yeni APK Releases'e düşer.

## Dosyalar

| Dosya | Ne işe yarar |
|---|---|
| `index.html` | Uygulamanın tamamı; saatler içine gömülü |
| `manifest.webmanifest`, `sw.js`, ikonlar | Ana ekrana ekleme ve çevrimdışı çalışma |
| `ringsaatleri_to_json.py`, `ringsaatleri.xlsx` | Saat tablosu ve onu HTML'e gömen script (`SAME_STOP` ile aynı-yer durakları) |
| `android/` | APK için WebView sarmalayıcı (Java, bağımlılık yok) |
| `android/app/ring.keystore` | APK imza anahtarı; güncellemelerin üstüne kurulabilmesi için sabit. Şifre `ringsaatleri`. |
| `.github/workflows/android.yml` | APK'yı derleyip Releases'e koyan iş akışı |

## Nasıl karar veriyor

- **Yön:** Her sefer gidiş-dönüş içerdiğinden, bir duraktan sonra gelen ilk uç durak (G1 veya 1100) o binişin yönü.
- **Gün tipi:** Cumartesi/Pazar hafta sonu tablosu; üstteki anahtarla elle değiştirilebilir.
- **Yolculuk:** Aynı seferde önce kalkış, sonra varış durağı aranır. Daha erken kalkıp daha geç varanlar gizlenir; liste varış saatine göre.
- **Aktarma:** Doğrudan ring yoksa tek aktarmalı seçenekler (aktarmada en az 1, en fazla 60 dk bekleme).
- **1020 = 1050:** Ring 2 ve 3'te 1020'nin arkasına aynı saatle 1050 eklenir (script'teki `SAME_STOP`).
