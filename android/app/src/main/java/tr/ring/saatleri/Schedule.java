package tr.ring.saatleri;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/** assets/www/ring-data.json dosyasını okur; widget'ın ihtiyacı olan kalkışları hesaplar. */
class Schedule {

    /** Bir duraktan bir binişi: ring seferi ya da ek servis (TEK / FM). */
    static class Dep {
        final String ring;       // ek serviste null
        final String label;      // ek servis rozeti: TEK veya FM
        final String dir;        // yön durağı ya da uğradığı duraklar
        final int time;          // gece yarısından itibaren dakika
        final boolean tomorrow;

        Dep(String ring, String label, String dir, int time, boolean tomorrow) {
            this.ring = ring;
            this.label = label;
            this.dir = dir;
            this.time = time;
            this.tomorrow = tomorrow;
        }

        boolean isExtra() {
            return ring == null;
        }
    }

    private static JSONObject cache;

    /** Güncelleme indirildiyse onu, yoksa APK içindeki kopyayı okur. */
    private static JSONObject data(Context c) throws Exception {
        if (cache == null) {
            File f = Updater.localFile(c, "ring-data.json");
            if (f.exists()) {
                try {
                    cache = new JSONObject(read(new FileInputStream(f)));
                } catch (Exception e) {
                    f.delete();          // bozuk indirme: APK'daki kopyaya dön
                }
            }
            if (cache == null) {
                cache = new JSONObject(read(c.getAssets().open("www/ring-data.json")));
            }
        }
        return cache;
    }

    /** Veri güncellenince çağrılır; bir sonraki okumada yeniden yüklenir. */
    static void invalidate() {
        cache = null;
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), "UTF-8");
    }

    static List<String> stops(Context c) {
        List<String> out = new ArrayList<String>();
        try {
            JSONArray a = data(c).getJSONArray("stops");
            for (int i = 0; i < a.length(); i++) {
                out.add(a.getString(i));
            }
        } catch (Exception e) {
            // veri okunamazsa boş liste
        }
        return out;
    }

    static String dayType(Calendar cal) {
        int d = cal.get(Calendar.DAY_OF_WEEK);
        return (d == Calendar.SATURDAY || d == Calendar.SUNDAY) ? "weekend" : "weekday";
    }

    static String dayLabel(Calendar cal) {
        return "weekend".equals(dayType(cal)) ? "hafta sonu" : "hafta içi";
    }

    static String hhmm(int m) {
        int h = (m / 60) % 24;
        return (h < 10 ? "0" : "") + h + ":" + (m % 60 < 10 ? "0" : "") + (m % 60);
    }

    static int nowMinutes() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    /** Şu andan itibaren ilk `limit` kalkış; bugün bittiyse yarının ilk seferleriyle tamamlar. */
    static List<Dep> next(Context c, String stop, int limit) {
        Calendar cal = Calendar.getInstance();
        List<Dep> out = collect(c, stop, dayType(cal), nowMinutes(), false);
        if (out.size() < limit) {
            Calendar t = (Calendar) cal.clone();
            t.add(Calendar.DAY_OF_MONTH, 1);
            out.addAll(collect(c, stop, dayType(t), 0, true));
        }
        return out.size() > limit ? new ArrayList<Dep>(out.subList(0, limit)) : out;
    }

    private static List<Dep> collect(Context c, String stop, String dayType, int from, boolean tomorrow) {
        List<Dep> out = new ArrayList<Dep>();
        try {
            JSONObject d = data(c);
            JSONArray ends = d.getJSONArray("ends");
            JSONObject rings = d.getJSONObject("rings");
            Iterator<String> it = rings.keys();
            while (it.hasNext()) {
                String ring = it.next();
                JSONObject day = rings.getJSONObject(ring).optJSONObject(dayType);
                if (day == null) {
                    continue;
                }
                JSONArray stops = day.getJSONArray("stops");
                JSONArray trips = day.getJSONArray("trips");
                for (int i = 0; i < stops.length() - 1; i++) {
                    if (!stops.getString(i).equals(stop)) {
                        continue;
                    }
                    // yön: bu duraktan sonra uğradığı ilk uç durak (G1 / G9)
                    String dir = null;
                    for (int j = i + 1; j < stops.length(); j++) {
                        String s = stops.getString(j);
                        for (int e = 0; e < ends.length(); e++) {
                            if (ends.getString(e).equals(s)) {
                                dir = s;
                                break;
                            }
                        }
                        if (dir != null) {
                            break;
                        }
                    }
                    if (dir == null) {
                        continue;
                    }
                    for (int t = 0; t < trips.length(); t++) {
                        JSONArray times = trips.getJSONArray(t);
                        if (times.isNull(i)) {
                            continue;            // bu sefer o durağa uğramıyor
                        }
                        int time = times.getInt(i);
                        if (time >= from) {
                            out.add(new Dep(ring, null, dir, time, tomorrow));
                        }
                    }
                }
            }
            out.addAll(oneway(d, stop, dayType, from, tomorrow));
        } catch (Exception e) {
            // veri okunamazsa boş liste
        }
        Collections.sort(out, new Comparator<Dep>() {
            public int compare(Dep a, Dep b) {
                if (a.time != b.time) {
                    return a.time - b.time;
                }
                return (a.ring == null ? "Z" : a.ring).compareTo(b.ring == null ? "Z" : b.ring);
            }
        });
        return out;
    }

    /**
     * Ek servisler. board="first": sadece ilk duraktan binilir, varış saatleri yok.
     * board="all": her duraktan binilir, son durakta inilir.
     */
    private static List<Dep> oneway(JSONObject d, String stop, String dayType, int from, boolean tomorrow)
            throws Exception {
        List<Dep> out = new ArrayList<Dep>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        JSONArray arr = d.optJSONArray("oneway");
        if (arr == null) {
            return out;
        }
        for (int k = 0; k < arr.length(); k++) {
            JSONObject o = arr.getJSONObject(k);
            if (!dayType.equals(o.getString("day"))) {
                continue;
            }
            JSONArray stops = o.getJSONArray("stops");
            JSONArray times = o.getJSONArray("times");
            boolean all = "all".equals(o.optString("board"));
            String label = o.optString("label", "EK");
            for (int i = 0; i < stops.length() - 1; i++) {
                if (!stop.equals(stops.getString(i)) || times.isNull(i)) {
                    continue;
                }
                int time = times.getInt(i);
                if (time < from) {
                    continue;
                }
                String dir;
                if (all) {
                    dir = stops.getString(stops.length() - 1) + " yönü";
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int j = i + 1; j < stops.length() && j - i <= 6; j++) {
                        sb.append(j == i + 1 ? "" : ", ").append(stops.getString(j));
                    }
                    if (stops.length() - i - 1 > 6) {
                        sb.append("…");
                    }
                    dir = sb.toString();
                }
                if (seen.add(time + "|" + label + "|" + dir)) {
                    out.add(new Dep(null, label, dir, time, tomorrow));
                }
            }
        }
        return out;
    }
}
