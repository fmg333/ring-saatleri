package tr.ring.saatleri;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Uygulama açılınca GitHub Pages'teki güncel saatleri indirir ve telefona yazar; böylece saat
 * değişikliği için yeni APK dağıtmak gerekmez. İnternet yoksa APK'nın içindeki kopya kullanılır.
 * Ayrıca Releases'te daha yeni bir APK varsa haber verir.
 */
class Updater {

    static final String PREFS = "ring_update";
    private static final String[] FILES = {"index.html", "ring-data.json"};
    private static final long CHECK_EVERY = 6 * 60 * 60 * 1000L;   // en sık 6 saatte bir
    private static final int TIMEOUT = 8000;

    interface Listener {
        void onDataUpdated();

        void onNewVersion(String tag, String pageUrl);
    }

    /** İndirilmiş dosyanın yolu; yoksa APK içindeki kopya kullanılır. */
    static File localFile(Context c, String name) {
        return new File(new File(c.getFilesDir(), "www"), name);
    }

    /**
     * Yeni bir APK kurulduysa indirilmiş kopyaları siler: APK'nın içindeki sürüm baz alınır,
     * daha yenisi varsa bir sonraki kontrolde yine inecektir.
     */
    static void syncWithApk(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int v;
        try {
            v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return;
        }
        if (p.getInt("apk_version", -1) == v) {
            return;
        }
        File dir = new File(c.getFilesDir(), "www");
        File[] files = dir.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                files[i].delete();
            }
        }
        p.edit().clear().putInt("apk_version", v).apply();
    }

    /**
     * Sayfayı her zaman aynı yoldan açabilmek için APK'daki kopyayı bir kez telefona yazar.
     * Böylece güncelleme indiğinde adres değişmez, kayıtlı durak seçimi kaybolmaz.
     */
    static File ensurePage(Context c) throws Exception {
        File f = localFile(c, "index.html");
        if (f.exists()) {
            return f;
        }
        f.getParentFile().mkdirs();
        File tmp = new File(f.getParentFile(), "index.html.tmp");
        InputStream in = c.getAssets().open("www/index.html");
        FileOutputStream out = new FileOutputStream(tmp);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        out.close();
        if (!tmp.renameTo(f)) {
            tmp.delete();
            throw new Exception("kopyalanamadı");
        }
        return f;
    }

    /** CI dışında derlenirse adres yer tutucu kalır; o zaman güncelleme denenmez. */
    private static boolean configured(String s) {
        return s != null && s.length() > 0 && s.indexOf("OWNER") < 0;
    }

    static void checkAsync(Context c, final Listener listener, final boolean force) {
        final Context app = c.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            public void run() {
                SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                long now = System.currentTimeMillis();
                if (!force && now - p.getLong("last_check", 0) < CHECK_EVERY) {
                    return;
                }
                p.edit().putLong("last_check", now).apply();

                boolean changed = false;
                for (int i = 0; i < FILES.length; i++) {
                    try {
                        changed = fetch(app, p, FILES[i]) || changed;
                    } catch (Exception e) {
                        // ağ yoksa ya da sunucu cevap vermezse sessizce eski veriyle devam
                    }
                }
                if (changed) {
                    Schedule.invalidate();
                    RingWidget.updateAll(app);
                    if (listener != null) {
                        main.post(new Runnable() {
                            public void run() {
                                listener.onDataUpdated();
                            }
                        });
                    }
                }
                try {
                    checkVersion(app, p, listener, main);
                } catch (Exception e) {
                    // sürüm kontrolü başarısızsa önemli değil
                }
            }
        }).start();
    }

    /** Dosyayı indirir; değiştiyse true döner. ETag ile değişmediyse indirmez. */
    private static boolean fetch(Context c, SharedPreferences p, String name) throws Exception {
        String base = c.getString(R.string.update_base_url);
        if (!configured(base)) {
            return false;
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(base + name).openConnection();
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        String etag = p.getString("etag_" + name, null);
        if (etag != null && localFile(c, name).exists()) {
            conn.setRequestProperty("If-None-Match", etag);
        }
        int code = conn.getResponseCode();
        if (code != 200) {                       // 304: değişmemiş
            conn.disconnect();
            return false;
        }
        byte[] body = readAll(conn.getInputStream());
        String newEtag = conn.getHeaderField("ETag");
        conn.disconnect();

        if (!valid(name, body)) {                // yarım inen ya da alakasız içerik yazılmasın
            return false;
        }
        File dir = new File(c.getFilesDir(), "www");
        dir.mkdirs();
        File tmp = new File(dir, name + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        out.write(body);
        out.close();
        File dst = localFile(c, name);
        if (dst.exists()) {
            dst.delete();
        }
        if (!tmp.renameTo(dst)) {
            tmp.delete();
            return false;
        }
        if (newEtag != null) {
            p.edit().putString("etag_" + name, newEtag).apply();
        }
        return true;
    }

    private static boolean valid(String name, byte[] body) throws Exception {
        if (body.length < 2000) {
            return false;
        }
        String s = new String(body, "UTF-8");
        if (name.endsWith(".json")) {
            return new JSONObject(s).has("rings");
        }
        return s.indexOf("RING_DATA_START") >= 0;
    }

    /** Releases'teki son APK'nın sürümü bu APK'dan yeniyse haber verir. */
    private static void checkVersion(Context c, SharedPreferences p, final Listener listener, Handler main)
            throws Exception {
        String slug = c.getString(R.string.repo_slug);
        if (!configured(slug) || listener == null) {
            return;
        }
        HttpURLConnection conn = (HttpURLConnection)
                new URL("https://api.github.com/repos/" + slug + "/releases/latest").openConnection();
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        if (conn.getResponseCode() != 200) {
            conn.disconnect();
            return;
        }
        JSONObject o = new JSONObject(new String(readAll(conn.getInputStream()), "UTF-8"));
        conn.disconnect();

        final String tag = o.optString("tag_name", "");
        final String url = o.optString("html_url", "");
        int current = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        if (digits(tag) <= current || tag.equals(p.getString("skip_tag", ""))) {
            return;
        }
        main.post(new Runnable() {
            public void run() {
                listener.onNewVersion(tag, url);
            }
        });
    }

    /** Sürüm etiketindeki sayı: "apk-42" -> 42 */
    private static int digits(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') {
                sb.append(ch);
            }
        }
        try {
            return Integer.parseInt(sb.toString());
        } catch (Exception e) {
            return -1;
        }
    }

    static void skipVersion(Context c, String tag) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("skip_tag", tag).apply();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toByteArray();
    }
}
