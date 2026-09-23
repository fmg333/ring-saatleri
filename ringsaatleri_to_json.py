#!/usr/bin/env python3
"""Ring saatlerini index.html ve ring-data.json içine yazar.

Kullanım:
    python ringsaatleri_to_json.py ringsaatleri.xlsx index.html

Girdiler:
    ringsaatleri.xlsx  gidiş-dönüş ring seferleri (her satır bir sefer)
    tekyon.csv         tek yön servisler (varsa; sadece kalkış durağından binilir)
"""
import csv
import datetime as dt
import json
import os
import re
import sys

from openpyxl import load_workbook

HEADER_RE = re.compile(r"R[İIi]NG\s*(\d)", re.IGNORECASE)

ENDS = ["G1", "G9"]                  # uç duraklar; yön bunlara bakarak bulunur
RENAME = {"1100": "G9"}              # tabloda başka adla geçen duraklar

# Farklı isimle geçen ama aynı yerde olan duraklar: (tablodaki isim, eklenecek isim).
# Soldaki durağın hemen arkasına sağdaki isim aynı saatle eklenir.
SAME_STOP = [("1020", "1050")]

# Rotanın tamamını değil, bir duraktan sonrasını yapan seferler:
# (ring, gün, başlangıç durağı, saat). Saatler o ringin normal sefer süresinden türetilir.
HALF_TRIPS = [
    ("2", "weekday", "G9", "08:05"),
    ("2", "weekday", "G9", "08:17"),
]

DAY_KEY = {"haftaici": "weekday", "haftasonu": "weekend"}
ONEWAY_FILE = "tekyon.csv"

# Her duraktan binilen servislerde ara saatler ring verisindeki sürelerden hesaplanır.
# Ringlerde hiç geçmeyen duraklar için süre buraya elle yazılır (dakika):
LEG_OVERRIDE = {
    ("40", "20"): 2, ("20", "G1"): 2,      # 40-G1 arası 4 dk; araya 20 girince ikiye bölündü
    ("320", "220"): 1, ("220", "40"): 1,   # tahmin: 220 ringlerde yok (320-40 arası 1 dk)
    ("610", "510"): 2, ("510", "G1"): 2,   # tahmin: 510 ringlerde yok (610-G1 arası 3 dk)
}


def to_minutes(v):
    if v is None or v == "":
        return None
    if isinstance(v, (dt.time, dt.datetime)):
        return v.hour * 60 + v.minute
    if isinstance(v, (int, float)):
        return int(round(float(v) * 1440))
    if isinstance(v, str):
        m = re.match(r"^\s*(\d{1,2})[:.](\d{2})\s*$", v)
        if m:
            return int(m.group(1)) * 60 + int(m.group(2))
    raise ValueError(f"Saat olarak okunamadı: {v!r}")


def norm_stop(v):
    """Durak adını sadeleştirir: 1100 -> G9, G-4 -> G4, sayılar metne."""
    if isinstance(v, float) and v.is_integer():
        v = int(v)
    s = str(v).strip().upper()
    s = re.sub(r"^G\s*-\s*(\d+)$", r"G\1", s)
    return RENAME.get(s, s)


def sort_key(s):
    return (not s.isdigit(), int(s) if s.isdigit() else s)


def parse_rings(path):
    ws = load_workbook(path, read_only=True, data_only=True).active
    rows = [list(r) for r in ws.iter_rows(values_only=True)]

    rings = {}
    day = "weekday"
    i = 0
    while i < len(rows):
        first = rows[i][0]
        m = HEADER_RE.search(str(first)) if isinstance(first, str) else None
        if not m:
            i += 1
            continue
        title = str(first).upper().replace("İ", "I")
        if "HAFTA SONU" in title or "HAFTASONU" in title:
            day = "weekend"
        elif "HAFTA ICI" in title or "HAFTAICI" in title:
            day = "weekday"
        ring = m.group(1)

        stops = [norm_stop(c) for c in rows[i + 1] if c is not None and str(c).strip() != ""]
        n = len(stops)

        trips = []
        j = i + 2
        while j < len(rows):
            r = rows[j]
            if r[0] is None or (isinstance(r[0], str) and HEADER_RE.search(r[0])):
                break
            times = [to_minutes(c) for c in r[:n]]
            if any(t is None for t in times):
                raise ValueError(f"Satır {j + 1}: {n} saat bekleniyordu, eksik hücre var")
            for a, b in zip(times, times[1:]):
                if b < a:
                    raise ValueError(f"Satır {j + 1}: saatler artan değil ({a}->{b})")
            trips.append(times)
            j += 1

        for a, b in SAME_STOP:
            for k in range(len(stops) - 1, -1, -1):
                if stops[k] == a and (k + 1 >= len(stops) or stops[k + 1] != b):
                    stops.insert(k + 1, b)
                    for tr in trips:
                        tr.insert(k + 1, tr[k])

        rings.setdefault(ring, {})[day] = {"stops": stops, "trips": trips}
        i = j
    return rings


def add_half_trips(rings):
    """Rotanın ortasından başlayan seferler: öncesindeki duraklar null kalır."""
    for ring, day, start, hhmm in HALF_TRIPS:
        data = rings[ring][day]
        stops, trips = data["stops"], data["trips"]
        if start not in stops:
            raise ValueError(f"Ring {ring} rotasında {start} yok")
        idx = stops.index(start)
        ref = trips[0]                                   # sefer süreleri her seferde aynı
        offsets = [t - ref[idx] for t in ref[idx:]]
        t0 = to_minutes(hhmm)
        trips.append([None] * idx + [t0 + o for o in offsets])
    for r in rings.values():
        for d in r.values():
            d["trips"].sort(key=lambda tr: next(t for t in tr if t is not None))


def leg_table(rings):
    """Ring verisinden durak çiftleri arası süreler: önce ardışık çiftler, sonra aradaki en kısa geçiş."""
    direct, span = {}, {}
    for days in rings.values():
        for d in days.values():
            stops, times = d["stops"], d["trips"][0]
            for i in range(len(stops)):
                if times[i] is None:
                    continue
                for j in range(i + 1, len(stops)):
                    if times[j] is None:
                        continue
                    key, dt = (stops[i], stops[j]), times[j] - times[i]
                    span[key] = min(span.get(key, 10 ** 6), dt)
                    if j == i + 1:
                        direct[key] = min(direct.get(key, 10 ** 6), dt)
    return direct, span


def leg_minutes(a, b, direct, span):
    for table in (LEG_OVERRIDE, direct, span):
        if (a, b) in table:
            return table[(a, b)]
    if (b, a) in span:                     # ters yön: aynı iki durak arası
        return span[(b, a)]
    raise ValueError(f"{a} -> {b} arası süre bilinmiyor; LEG_OVERRIDE'a ekle")


def parse_oneway(path):
    if not os.path.exists(path):
        return []
    out = []
    with open(path, encoding="utf-8") as f:
        for row in csv.DictReader(line for line in f if not line.startswith("#")):
            if not row.get("kalkis"):
                continue
            stops = [norm_stop(row["kalkis"])]
            stops += [norm_stop(s) for s in row["varislar"].split("-") if s.strip()]
            item = {
                "day": DAY_KEY[row["gun"].strip().lower()],
                "stops": stops,
                "t0": to_minutes(row["saat"]),
                "board": "all" if row.get("binis", "").strip().lower() == "hepsi" else "first",
            }
            if row.get("not", "").strip():
                item["note"] = row["not"].strip()
            out.append(item)
    return out


def build_oneway(items, rings):
    """Her servise saat dizisi verir: 'first' ise sonraki duraklar boş, 'all' ise hesaplanır."""
    direct, span = leg_table(rings)
    out = []
    for it in items:
        stops, t0 = it["stops"], it.pop("t0")
        if it["board"] == "all":
            times, t = [t0], t0
            for a, b in zip(stops, stops[1:]):
                t += leg_minutes(a, b, direct, span)
                times.append(t)
            it["label"] = "FM"                     # fazla mesai ringi
        else:
            times = [t0] + [None] * (len(stops) - 1)
            it["label"] = "TEK"
        it["times"] = times
        out.append(it)
    out.sort(key=lambda x: (x["day"], x["times"][0], x["stops"][0]))
    return out


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)

    rings = parse_rings(sys.argv[1])
    add_half_trips(rings)
    oneway = build_oneway(parse_oneway(ONEWAY_FILE), rings)

    stops = {s for r in rings.values() for d in r.values() for s in d["stops"]}
    stops |= {s for o in oneway for s in o["stops"]}
    data = {"ends": ENDS, "stops": sorted(stops, key=sort_key), "rings": rings, "oneway": oneway}

    for ring, days in sorted(rings.items()):
        for day, d in days.items():
            print(f"Ring {ring} {day:8s}: {len(d['trips'])} sefer, {len(d['stops'])} durak")
    n_all = sum(1 for o in oneway if o["board"] == "all")
    print(f"Tek yön servis: {len(oneway)} ({n_all} tanesi her duraktan binişli)  |  toplam durak: {len(data['stops'])}")

    with open("ring-data.json", "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    print("ring-data.json yazıldı")

    if len(sys.argv) > 2:
        src = open(sys.argv[2], encoding="utf-8").read()
        a = src.index("/*RING_DATA_START*/") + len("/*RING_DATA_START*/")
        b = src.index("/*RING_DATA_END*/")
        payload = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
        open(sys.argv[2], "w", encoding="utf-8").write(src[:a] + payload + src[b:])
        print(f"{sys.argv[2]} güncellendi")
