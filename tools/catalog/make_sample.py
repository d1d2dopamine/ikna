#!/usr/bin/env python3
"""
Write a tiny made-up corpus shaped exactly like Tatoeba's exports.

The real dumps are hundreds of megabytes and live behind a download, which makes
them a bad way to find out whether build_catalog.py still works. This writes a
hundred and twenty sentences in three languages, in the same tab-separated shape,
out of sentences written for this file and belonging to nobody -- so the
catalogue pipeline can be run start to finish in about a second, in CI, on every
change, before anything is downloaded.

    python3 tools/catalog/make_sample.py /tmp/sample
    python3 tools/catalog/build_catalog.py --tatoeba /tmp/sample --out /tmp/cat \\
        --learn en --meanings ru,pl --min-deck 5 --function-top 12

It proves the shape, not the quality: what a real corpus does to the sieve can
only be seen on the real corpus.
"""

import os
import sys

# Forty things, in three languages, in the same order. Ordinary nouns, because
# the pipeline picks the rarest word in a sentence and every one of these appears
# exactly once in the sample.
NOUNS = [
    ("harbour", "\u043f\u043e\u0440\u0442", "port"),
    ("lantern", "\u0444\u043e\u043d\u0430\u0440\u044c", "latarnia"),
    ("kettle", "\u0447\u0430\u0439\u043d\u0438\u043a", "czajnik"),
    ("ladder", "\u043b\u0435\u0441\u0442\u043d\u0438\u0446\u0430", "drabina"),
    ("blanket", "\u043e\u0434\u0435\u044f\u043b\u043e", "koc"),
    ("mirror", "\u0437\u0435\u0440\u043a\u0430\u043b\u043e", "lustro"),
    ("basket", "\u043a\u043e\u0440\u0437\u0438\u043d\u0430", "koszyk"),
    ("pencil", "\u043a\u0430\u0440\u0430\u043d\u0434\u0430\u0448", "o\u0142\u00f3wek"),
    ("window", "\u043e\u043a\u043d\u043e", "okno"),
    ("garden", "\u0441\u0430\u0434", "ogr\u00f3d"),
    ("bridge", "\u043c\u043e\u0441\u0442", "most"),
    ("village", "\u0434\u0435\u0440\u0435\u0432\u043d\u044f", "wioska"),
    ("letter", "\u043f\u0438\u0441\u044c\u043c\u043e", "list"),
    ("suitcase", "\u0447\u0435\u043c\u043e\u0434\u0430\u043d", "walizka"),
    ("ticket", "\u0431\u0438\u043b\u0435\u0442", "bilet"),
    ("market", "\u0440\u044b\u043d\u043e\u043a", "rynek"),
    ("bakery", "\u043f\u0435\u043a\u0430\u0440\u043d\u044f", "piekarnia"),
    ("forest", "\u043b\u0435\u0441", "las"),
    ("river", "\u0440\u0435\u043a\u0430", "rzeka"),
    ("mountain", "\u0433\u043e\u0440\u0430", "g\u00f3ra"),
    ("kitchen", "\u043a\u0443\u0445\u043d\u044f", "kuchnia"),
    ("balcony", "\u0431\u0430\u043b\u043a\u043e\u043d", "balkon"),
    ("library", "\u0431\u0438\u0431\u043b\u0438\u043e\u0442\u0435\u043a\u0430", "biblioteka"),
    ("museum", "\u043c\u0443\u0437\u0435\u0439", "muzeum"),
    ("station", "\u0432\u043e\u043a\u0437\u0430\u043b", "dworzec"),
    ("pharmacy", "\u0430\u043f\u0442\u0435\u043a\u0430", "apteka"),
    ("neighbour", "\u0441\u043e\u0441\u0435\u0434", "s\u0105siad"),
    ("teacher", "\u0443\u0447\u0438\u0442\u0435\u043b\u044c", "nauczyciel"),
    ("driver", "\u0432\u043e\u0434\u0438\u0442\u0435\u043b\u044c", "kierowca"),
    ("doctor", "\u0432\u0440\u0430\u0447", "lekarz"),
    ("painter", "\u0445\u0443\u0434\u043e\u0436\u043d\u0438\u043a", "malarz"),
    ("cousin", "\u0434\u0432\u043e\u044e\u0440\u043e\u0434\u043d\u044b\u0439 \u0431\u0440\u0430\u0442", "kuzyn"),
    ("holiday", "\u043f\u0440\u0430\u0437\u0434\u043d\u0438\u043a", "\u015bwi\u0119to"),
    ("weather", "\u043f\u043e\u0433\u043e\u0434\u0430", "pogoda"),
    ("evening", "\u0432\u0435\u0447\u0435\u0440", "wiecz\u00f3r"),
    ("morning", "\u0443\u0442\u0440\u043e", "poranek"),
    ("winter", "\u0437\u0438\u043c\u0430", "zima"),
    ("summer", "\u043b\u0435\u0442\u043e", "lato"),
    ("question", "\u0432\u043e\u043f\u0440\u043e\u0441", "pytanie"),
    ("answer", "\u043e\u0442\u0432\u0435\u0442", "odpowied\u017a"),
]

# Three frames, so the same forty words appear in a hundred and twenty sentences
# and the glue words become common enough to be recognised as glue.
FRAMES = [
    (
        "I left the %s at home this morning.",
        "\u042f \u043e\u0441\u0442\u0430\u0432\u0438\u043b %s \u0434\u043e\u043c\u0430 \u0441\u0435\u0433\u043e\u0434\u043d\u044f \u0443\u0442\u0440\u043e\u043c.",
        "Zostawi\u0142em %s w domu dzisiaj rano.",
    ),
    (
        "She asked me about the %s again.",
        "\u041e\u043d\u0430 \u0441\u043d\u043e\u0432\u0430 \u0441\u043f\u0440\u043e\u0441\u0438\u043b\u0430 \u043c\u0435\u043d\u044f \u043f\u0440\u043e %s.",
        "Znowu zapyta\u0142a mnie o %s.",
    ),
    (
        "We can talk about the %s tomorrow.",
        "\u041c\u044b \u043c\u043e\u0436\u0435\u043c \u043f\u043e\u0433\u043e\u0432\u043e\u0440\u0438\u0442\u044c \u043f\u0440\u043e %s \u0437\u0430\u0432\u0442\u0440\u0430.",
        "Mo\u017cemy porozmawia\u0107 o %s jutro.",
    ),
]


def main(argv):
    if len(argv) != 2:
        print("usage: make_sample.py <folder>", file=sys.stderr)
        return 2
    out = argv[1]
    os.makedirs(out, exist_ok=True)

    rows = []
    links = []
    next_id = 1000
    for frame_en, frame_ru, frame_pl in FRAMES:
        for noun_en, noun_ru, noun_pl in NOUNS:
            eng = next_id
            rus = next_id + 1
            pol = next_id + 2
            next_id += 3
            rows.append((eng, "eng", frame_en % noun_en))
            rows.append((rus, "rus", frame_ru % noun_ru))
            rows.append((pol, "pol", frame_pl % noun_pl))
            # Tatoeba stores both directions, and so does this.
            links.append((eng, rus))
            links.append((rus, eng))
            links.append((eng, pol))
            links.append((pol, eng))

    with open(os.path.join(out, "sentences.csv"), "w", encoding="utf-8") as handle:
        for sid, lang, text in rows:
            handle.write("%d\t%s\t%s\n" % (sid, lang, text))
    with open(os.path.join(out, "links.csv"), "w", encoding="utf-8") as handle:
        for left, right in links:
            handle.write("%d\t%d\n" % (left, right))
    # A quarter of them, so the public-domain family has something to build from.
    with open(os.path.join(out, "sentences_CC0.csv"), "w", encoding="utf-8") as handle:
        for sid, lang, text in rows:
            if sid % 4 == 0:
                handle.write("%d\t%s\t%s\t\\N\n" % (sid, lang, text))

    print("wrote %d sentences and %d links to %s" % (len(rows), len(links), out))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
