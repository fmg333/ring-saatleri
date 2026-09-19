package tr.ring.saatleri;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** index.html'i uygulama içindeki WebView'da açar; internet gerekmez. */
public class MainActivity extends Activity {

    static final String EXTRA_STOP = "stop";
    private static final String PAGE = "file:///android_asset/www/index.html";

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
    }

    /** Widget'tan gelindiyse sayfayı o durak seçili açar. */
    private String pageUrl(Intent intent) {
        String stop = intent != null ? intent.getStringExtra(EXTRA_STOP) : null;
        return stop == null ? PAGE : PAGE + "#from=" + Uri.encode(stop);
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
