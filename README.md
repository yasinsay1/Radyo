# M3U Radyo (Android Auto)

M3U listesinden radyo çalan, Android Auto'da medya uygulaması olarak görünen Kotlin uygulaması.

## Nasıl çalışır
- Telefondaki uygulamadan M3U bağlantısı girin ya da dosya seçin; liste cihazda saklanır.
- Android Auto, `PlaybackService` (Media3 `MediaLibraryService`) üzerinden istasyonları listeler.
- `group-title` varsa istasyonlar klasörlere ayrılır; ayrıca "Tüm istasyonlar" klasörü vardır.
- Desteklenen: `#EXTINF` (tvg-logo, group-title), `#EXTGRP`, düz URL listeleri, http/https/HLS akışları.

## APK derleme
GitHub'a yükleyin, **Actions → Build APK → Run workflow**. Bitince `m3u-radyo-debug-apk` artifact'ini indirin.

## Android Auto'da görünmesi için
Uygulama Play Store dışından yüklendiği için:
1. Telefonda Android Auto ayarlarını açın, "Sürüm" satırına 10 kez dokunup geliştirici modunu açın.
2. Geliştirici ayarlarında **Bilinmeyen kaynaklar** seçeneğini işaretleyin.
3. Arabaya bağlanın; "M3U Radyo" medya uygulamaları arasında görünür.
