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

            public void onNewVersion(final String tag, final String pageUrl) {
                if (isFinishing()) {
                    return;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Yeni sürüm var")
                        .setMessage("Uygulamanın yeni sürümü yayınlanmış. İndirme sayfasını açayım mı?")
                        .setPositiveButton("Aç", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int which) {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)));
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
