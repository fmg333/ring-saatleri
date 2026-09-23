# Time of the Rings

G1 – 1100 arası üç ringin seferleri. Tek repo, iki çıktı:

- **iPhone (ve isteyen Android):** GitHub Pages adresi → Safari'de aç → *Ana Ekrana Ekle*. Çevrimdışı çalışır.
- **Android:** GitHub Actions her push'ta APK üretir, *Releases* sayfasından indirilir. Ana ekran widget'ı da var.

## Kurulum (bir kez)

1. github.com → **New repository** → ad: `ring-saatleri`, **Public** → Create.
2. GitHub Desktop → *File → Add local repository* → klasörü seç → *create a repository* → *Commit to main* → *Publish repository* (private kutusu işaretsiz).
3. **Settings → Pages** → *Deploy from a branch* → `main`, `/ (root)` → Save. Adres: `https://<kullanici>.github.io/ring-saatleri/`
4. **Actions** sekmesinde "APK üret" çalışır (~4 dk); bitince **Releases** altında `ring-saatleri.apk`.

## Telefona kurma

**iPhone:** Safari'de Pages adresini aç → Paylaş → *Ana Ekrana Ekle*.

**Android (APK):** `github.com/<kullanici>/ring-saatleri/releases/latest` → APK'yı indir → aç → "Bu kaynaktan yüklemeye izin ver" → Yükle. Sonraki sürümler üstüne kurulur.

**Android (adres):** Chrome'da Pages adresi → menü (⋮) → *Ana ekrana ekle*. Widget istiyorsan APK gerekir.

## Ana ekran widget'ı (Android)

Ana ekranda boş bir yere uzun bas → *Widget'lar* → **Time of the Rings** → widget'ı sürükle → açılan listeden durağı seç.

- Seçilen duraktan yaklaşan seferleri ring numarası, yön, saat ve kalan dakikayla gösterir; boyuna kaç satır sığıyorsa (en fazla 7) o kadarını.
- Dakika başı kendini yeniler (ekran kapalıyken sistem birkaç dakika erteleyebilir).
- Üstüne dokununca uygulama o durak seçili olarak açılır.
- Boyutu değiştirilebilir; birden fazla widget koyup farklı duraklar seçebilirsin.
- Günün seferleri bittiyse yarının ilk seferlerini "yarın 08:00" diye gösterir.

## Saatler değişince

1. Gidiş-dönüş ringler için `ringsaatleri.xlsx`, tek yön servisler için `tekyon.csv` dosyasını güncelle.
2. `python ringsaatleri_to_json.py ringsaatleri.xlsx index.html`
   (hem `index.html` içine gömer hem `ring-data.json` dosyasını tazeler; widget bunu kullanır)
3. `sw.js` içindeki `ring-v2` → `ring-v3` (Pages'ten kuranların önbelleği yenilensin).
4. GitHub Desktop → *Commit to main* → *Push origin*.

Pages birkaç dakikada güncellenir, yeni APK Releases'e düşer.

## İkon

Tüm ikonlar `logo.png` dosyasından üretilir: `python icons.py` (gereken: `pip install pillow`).
Üretilenler: web/PWA ikonları, Android'in eski ve adaptive ikonları, Android 13 temalı ikonu,
widget önizlemesi ve uygulama başlığındaki küçük amblem (`index.html` içine gömülür).

- Logoyu değiştirmek istersen yenisini `logo.png` olarak koy ve script'i tekrar çalıştır.
  Kadraj kayarsa `icons.py` başındaki `BADGE` kutusunu, zemin rengini `BG1/BG2` ile ayarla.
- Koddan çizilen eski ikon tasarımı `icons_drawn.py` içinde duruyor; `python icons_drawn.py`
  ile geri dönebilirsin (o dosya başlık amblemini güncellemez).
- Logo Tolkien evrenine gönderme yapıyor; uygulamayı kendine ve ekibine kurman sorun değil,
  ama mağazaya yükleyip dağıtmak için uygun bir logo değil.

## Dosyalar

| Dosya | Ne işe yarar |
|---|---|
| `index.html` | Uygulamanın tamamı; saatler içine gömülü |
| `ring-data.json` | Aynı saatlerin ayrı kopyası; Android widget'ı bunu okur |
| `manifest.webmanifest`, `sw.js`, ikonlar | Ana ekrana ekleme ve çevrimdışı çalışma |
| `ringsaatleri_to_json.py`, `ringsaatleri.xlsx` | Saat tablosu ve onu HTML'e gömen script (`SAME_STOP` ile aynı-yer durakları) |
| `logo.png`, `icons.py` | Logo ve ondan bütün ikonları üreten script |
| `icons_drawn.py` | Koddan çizilen alternatif ikon tasarımı |
| `android/` | APK: WebView sarmalayıcı + widget (Java, dış bağımlılık yok) |
| `android/app/ring.keystore` | APK imza anahtarı; güncellemelerin üstüne kurulabilmesi için sabit. Şifre `ringsaatleri`. |
| `.github/workflows/android.yml` | APK'yı derleyip Releases'e koyan iş akışı |

## Nasıl karar veriyor

- **Yön:** Her sefer gidiş-dönüş içerdiğinden, bir duraktan sonra gelen ilk uç durak (G1 veya 1100) o binişin yönü.
- **Gün tipi:** Cumartesi/Pazar hafta sonu tablosu; uygulamada üstteki anahtarla elle değiştirilebilir.
- **Yolculuk:** Aynı seferde önce kalkış, sonra varış durağı aranır. Daha erken kalkıp daha geç varanlar gizlenir; liste varış saatine göre.
- **Aktarma:** Doğrudan ring yoksa tek aktarmalı seçenekler (aktarmada en az 1, en fazla 60 dk bekleme).
- **1020 = 1050:** Ring 2 ve 3'te 1020'nin arkasına aynı saatle 1050 eklenir (script'teki `SAME_STOP`).
- **G9 = eski 1100:** Tablodaki 1100 durağı G9 olarak adlandırılır (`RENAME`), G-1/G-4/G-9 yazımları G1/G4/G9'a sadeleşir.
- **Ek servisler (`tekyon.csv`):** `binis` sütunu belirler.
  `ilk` → sadece kalkış durağından binilir, diğerlerinde iniş var, varış saati verilmez (gri **TEK** rozeti).
  `hepsi` → her duraktan binilir, son durakta inilir; ara saatler ring verisindeki durak arası sürelerden hesaplanır
  (fazla mesai ringleri, gri **FM** rozeti). Ringlerde hiç geçmeyen duraklar için süreler script'teki `LEG_OVERRIDE` tablosundan gelir.
- **Yarım seferler (`HALF_TRIPS`):** Rotanın ortasından başlayan ring seferleri (ör. G9'dan kalkan 08:05 ve 08:17 Ring 2'leri).
  Ara durak saatleri o ringin normal sefer süresinden türetilir; önceki duraklar o sefer için boş bırakılır.
- **Açılış durakları:** Son seçtiğin iki durak hatırlanır. Hiç seçim yoksa (ilk açılış ya da tarayıcı kaydı sildiyse) 1050 → 1100 gelir; `index.html` içindeki `DEFAULT_FROM` / `DEFAULT_TO` ile değiştirilir.
