package tr.ring.saatleri;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** index.html'i uygulama içindeki WebView'da açar; internet gerekmez. */
public class MainActivity extends Activity {
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
            // Android 10-12: sayfanın kendi koyu temasını kullan
            int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            s.setForceDark(night == Configuration.UI_MODE_NIGHT_YES ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
        }
        web.setWebViewClient(new WebViewClient());
        setContentView(web);
        web.loadUrl("file:///android_asset/www/index.html");
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
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
