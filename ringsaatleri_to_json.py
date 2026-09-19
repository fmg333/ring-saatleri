#!/usr/bin/env python3
"""ringsaatleri.xlsx -> index.html içindeki RING_DATA bloğunu günceller.

Kullanım:
    python ringsaatleri_to_json.py ringsaatleri.xlsx            # sadece ring-data.json yazar
    python ringsaatleri_to_json.py ringsaatleri.xlsx index.html # index.html içine de gömer

Excel düzeni (Sheet1):
    "HAFTA İÇİ RİNG 1" / "RİNG 2" / "RİNG 3 HAFTA SONU" gibi bir başlık satırı,
    altında durak isimleri satırı, altında her satırı bir sefer olan saat satırları.
    Başlıkta "HAFTA SONU" geçmiyorsa bir önceki gün tipi devam eder.
"""
import datetime as dt
import json
import re
import sys

from openpyxl import load_workbook

HEADER_RE = re.compile(r"R[İIi]NG\s*(\d)", re.IGNORECASE)

# Tabloda farklı isimle geçen ama aynı yerde olan duraklar: (tablodaki isim, eklenecek isim).
# Soldaki durağın hemen arkasına sağdaki isim aynı saatle eklenir (zaten varsa dokunulmaz).
SAME_STOP = [("1020", "1050")]


def to_minutes(v):
    if v is None or v == "":
        return None
    if isinstance(v, dt.time):
        return v.hour * 60 + v.minute
    if isinstance(v, dt.datetime):
        return v.hour * 60 + v.minute
    if isinstance(v, (int, float)):
        return int(round(float(v) * 1440))
    if isinstance(v, str):
        m = re.match(r"^\s*(\d{1,2})[:.](\d{2})\s*$", v)
        if m:
            return int(m.group(1)) * 60 + int(m.group(2))
    raise ValueError(f"Saat olarak okunamadı: {v!r}")


def norm_stop(v):
    if isinstance(v, float) and v.is_integer():
        v = int(v)
    return str(v).strip().upper()


def parse(path):
    ws = load_workbook(path, read_only=True, data_only=True).active
    rows = [list(r) for r in ws.iter_rows(values_only=True)]

    rings = {}
    day = "weekday"
    i = 0
    while i < len(rows):
        row = rows[i]
        first = row[0]
        m = HEADER_RE.search(str(first)) if isinstance(first, str) else None
        if not m:
            i += 1
            continue
        title = str(first).upper().replace("İ", "I")
        if "HAFTA SONU" in title or "HAFTASONU" in title:
            day = "weekend"
        elif "HAFTA ICI" in title or "HAFTAICI" in title or "HAFTA İÇİ" in str(first).upper():
            day = "weekday"
        ring = m.group(1)

        stops_row = rows[i + 1]
        stops = [norm_stop(c) for c in stops_row if c is not None and str(c).strip() != ""]
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

    all_stops = sorted(
        {s for r in rings.values() for d in r.values() for s in d["stops"]},
        key=lambda s: (not s.isdigit(), int(s) if s.isdigit() else s),
    )
    return {"ends": ["G1", "1100"], "stops": all_stops, "rings": rings}


def embed(html_path, data):
    src = open(html_path, encoding="utf-8").read()
    start, end = "/*RING_DATA_START*/", "/*RING_DATA_END*/"
    a, b = src.index(start) + len(start), src.index(end)
    payload = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    open(html_path, "w", encoding="utf-8").write(src[:a] + payload + src[b:])


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    data = parse(sys.argv[1])
    for ring, days in sorted(data["rings"].items()):
        for day, d in days.items():
            print(f"Ring {ring} {day:8s}: {len(d['trips'])} sefer, {len(d['stops'])} durak")
    with open("ring-data.json", "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    print("ring-data.json yazıldı")
    if len(sys.argv) > 2:
        embed(sys.argv[2], data)
        print(f"{sys.argv[2]} güncellendi")
