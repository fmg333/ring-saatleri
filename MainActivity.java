package tr.ring.saatleri;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * index.html'i uygulama içindeki WebView'da açar. Saatler APK'nın içinde gömülü olduğu için
 * internet gerekmez; internet varsa açılışta GitHub Pages'teki güncel sürüm indirilir.
 */
public class MainActivity extends Activity {

    static final String EXTRA_STOP = "stop";
    private static final String ASSET_PAGE = "file:///android_asset/www/index.html";

    private WebView web;
    private boolean pendingInstall;      // izin ekranından dönünce kuruluma devam edilsin mi

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Updater.syncWithApk(this);      // yeni APK kurulduysa eski indirmeleri temizle
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // son seçilen duraklar için localStorage
        s.setAllowFileAccess(true);     // file:///android_asset okuması
        if (Build.VERSION.SDK_INT >= 29 && Build.VERSION.SDK_INT < 33) {
            // Android 10-12: sayfanın kendi koyu teması kullanılsın
            int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            s.setForceDark(night == Configuration.UI_MODE_NIGHT_YES ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
        }
        web.setWebViewClient(new WebViewClient());
        setContentView(web);
        web.loadUrl(pageUrl(getIntent()));
        checkUpdates();
    }

    /** Sayfa her zaman aynı yoldan açılır; güncelleme inince orası tazelenir. */
    private String page() {
        try {
            return "file://" + Updater.ensurePage(this).getAbsolutePath();
        } catch (Exception e) {
            return ASSET_PAGE;
        }
    }

    /** Widget'tan gelindiyse sayfayı o durak seçili açar. */
    private String pageUrl(Intent intent) {
        String stop = intent != null ? intent.getStringExtra(EXTRA_STOP) : null;
        return stop == null ? page() : page() + "#from=" + Uri.encode(stop);
    }

    /** Açılışta güncel saatleri indirir; yenisi gelirse sayfayı tazeler. */
    private void checkUpdates() {
        Updater.checkAsync(this, new Updater.Listener() {
            public void onDataUpdated() {
                if (isFinishing()) {
                    return;
                }
                web.loadUrl(pageUrl(getIntent()));
                Toast.makeText(MainActivity.this, "Saatler güncellendi", Toast.LENGTH_SHORT).show();
            }

            public void onNewVersion(final String tag, final String apkUrl, final String pageUrl) {
                if (isFinishing()) {
                    return;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Yeni sürüm var")
                        .setMessage("Uygulamanın yeni sürümü yayınlanmış. İndirip kurayım mı?")
                        .setPositiveButton("İndir ve kur", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int which) {
                                download(apkUrl, pageUrl);
                            }
                        })
                        .setNegativeButton("Sonra", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int which) {
                                Updater.skipVersion(MainActivity.this, tag);
                            }
                        })
                        .show();
            }
        }, false);
    }

    /** APK'yı uygulama içinde indirir, bitince kurulumu başlatır. */
    private void download(final String apkUrl, final String pageUrl) {
        if (apkUrl == null || apkUrl.length() == 0) {
            open(pageUrl);                     // sürümde APK eki yoksa Releases sayfası
            return;
        }
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Yeni sürüm")
                .setMessage("İndiriliyor…")
                .setCancelable(false)
                .create();
        dlg.show();
        Updater.downloadApk(this, apkUrl, new Updater.ApkListener() {
            public void onProgress(int percent) {
                if (dlg.isShowing()) {
                    dlg.setMessage(percent < 0 ? "İndiriliyor…" : "İndiriliyor… %" + percent);
                }
            }

            public void onReady() {
                close(dlg);
                install(pageUrl);
            }

            public void onFailed() {
                close(dlg);
                Toast.makeText(MainActivity.this, "İndirilemedi, tarayıcıda açılıyor", Toast.LENGTH_SHORT).show();
                open(apkUrl);                  // tarayıcı indirmeyi sürdürsün
            }
        });
    }

    /** Kurulum ekranını açar; izin yoksa önce izni ister. */
    private void install(final String pageUrl) {
        if (isFinishing()) {
            return;
        }
        if (!Updater.canInstall(this)) {
            askInstallPermission();
            return;
        }
        pendingInstall = false;
        try {
            startActivity(Updater.installIntent(this));
        } catch (Exception e) {
            open(pageUrl);
        }
    }

    private void askInstallPermission() {
        new AlertDialog.Builder(this)
                .setTitle("Kurulum izni")
                .setMessage("Kurulumu başlatmak için bu uygulamaya \"bilinmeyen kaynaklardan yükleme\" "
                        + "izni vermen gerekiyor. Ayarları açayım mı?")
                .setPositiveButton("Ayarları aç", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        pendingInstall = true;     // izinden dönünce kuruluma devam
                        try {
                            startActivity(Updater.permissionIntent(MainActivity.this));
                        } catch (Exception e) {
                            pendingInstall = false;
                            Toast.makeText(MainActivity.this, "Ayarlar açılamadı", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void close(AlertDialog d) {
        try {
            d.dismiss();
        } catch (Exception e) {
            // ekran kapanmışsa önemli değil
        }
    }

    private void open(String url) {
        if (url == null || url.length() == 0) {
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Bağlantı açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        web.loadUrl(pageUrl(intent));
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        // Uygulama öne gelince saati hemen tazele
        web.evaluateJavascript("window.dispatchEvent(new Event('focus'))", null);

        // İzin ekranından izin verilerek dönüldüyse kuruluma kaldığı yerden devam et
        if (pendingInstall && Updater.canInstall(this) && Updater.apkReady(this)) {
            pendingInstall = false;
            install("");
        }
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
