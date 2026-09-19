package tr.ring.saatleri;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/** assets/www/ring-data.json dosyasını okur; widget'ın ihtiyacı olan kalkışları hesaplar. */
class Schedule {

    /** Bir duraktan bir binişi: hangi ring, hangi yöne, saat kaçta. */
    static class Dep {
        final String ring;
        final String dir;
        final int time;          // gece yarısından itibaren dakika
        final boolean tomorrow;

        Dep(String ring, String dir, int time, boolean tomorrow) {
            this.ring = ring;
            this.dir = dir;
            this.time = time;
            this.tomorrow = tomorrow;
        }
    }

    private static JSONObject cache;

    private static JSONObject data(Context c) throws Exception {
        if (cache == null) {
            InputStream in = c.getAssets().open("www/ring-data.json");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            in.close();
            cache = new JSONObject(new String(out.toByteArray(), "UTF-8"));
        }
        return cache;
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
                    // yön: bu duraktan sonra gelen ilk uç durak (G1 / 1100)
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
                        int time = trips.getJSONArray(t).getInt(i);
                        if (time >= from) {
                            out.add(new Dep(ring, dir, time, tomorrow));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // veri okunamazsa boş liste
        }
        Collections.sort(out, new Comparator<Dep>() {
            public int compare(Dep a, Dep b) {
                return a.time != b.time ? a.time - b.time : a.ring.compareTo(b.ring);
            }
        });
        return out;
    }
}
