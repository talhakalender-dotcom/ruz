# Ruz

*Rûz*: gün. Aklına gelen her şeyi tek satırla yakalayıp **Gantt şemasında** planladığın kişisel planlama uygulaması.
Şimdilik Android (Galaxy Z Fold 7 için tasarlandı). Kod Kotlin Multiplatform olduğu için ileride iOS da gelecek.

**Tarayıcıda dene:** https://talhakalender-dotcom.github.io/ruz/ (örnek verilerle; sayfa yenilenince başa döner, mikrofon ve yapay zekâ yok)

## Aşamalar

1. **İskelet** ✓: Fold uyumlu düzen (açıkken iki panel, kapalıyken sekmeler), veritabanı, tema, Nano test ekranı
2. **Yakalama ve kategoriler** ✓: tarih/saat/`#kategori` ayrıştırma, sesle yazma, kategori ekleme/silme/renk
3. Gantt I: çizim, yakınlaştırma, taşıma, uçtan uzatma, kilometre taşı
4. Gantt II: üstüne bırakınca bağlama, ardılları kaydırma, Gelen kutusundan sürükleme
5. Hatırlatıcılar ve "?" ile biten sorulara telefondaki yapay zekânın (Gemini Nano) cevabı
6. Sonra: tekrar eden işler, harcamalar, alışkanlıklar, widget, iOS, bulut ve web

## Yapı

- `core/`: arayüzden bağımsız saf mantık (Türkçe tarih ayrıştırıcı). Testleri JVM'de ve Node.js'te koşar.
- `shared/`: ortak kod. Veri (SQLDelight), mantık ve arayüz (Compose Multiplatform). Android'e özel kısımlar `androidMain` içinde (veritabanı sürücüsü, Gemini Nano, paylaşma).
- `androidApp/`: Android kabuğu (Activity, simge, imza).
- `webApp/`: tarayıcı deneme sürümü (Kotlin/Wasm), bellekte örnek verilerle çalışır.

## Derleme

Her push'ta GitHub Actions testleri çalıştırır ve APK'yı derler. `main` dalında APK **Releases → son** altına, web sürümü GitHub Pages'e konur.
Diğer dallarda APK, Actions çalıştırmasının "Artifacts" bölümünden indirilir.

İmza için repo'nun gizli ayarlarında `RUZ_KEYSTORE_B64` ve `RUZ_KEYSTORE_PASSWORD` tanımlı olmalı.
Anahtar dosyası repoda durmaz; yedeği ayrıca saklanır.
