"""Writes the seed under ./seed: the community/collection structure and one Simple Archive
Format (SAF) package per item, with a small generated PDF on some of them.

Every name, title and topic is invented ("Lumen University"). Each PDF carries a sentence
built around a made-up word that appears nowhere in the item's metadata, so a hit on that
word can only come from the full text.

The output is committed; run this only to change the seed:  python build_seed.py
"""

from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parent / "seed"
THESES = "theses"
ARTICLES = "articles"

# role: applied by seed.sh through REST. "withdrawn" and "private" happen before `oai import`,
# so DSpace's OAI reports them as deleted records; "private-after-oai" happens after it, so the
# OAI index still serves the item and only the REST check can tell it must not be indexed.
ITEMS = [
    {
        "dir": "item-01", "collection": THESES, "type": "Thesis",
        "title": "Mycorrhizal networks in restored mangrove soils",
        "authors": ["Silva, Ana"], "advisors": ["Okafor, Daniel"],
        "issued": "2021-06-15", "language": "en",
        "subjects": ["Mangroves", "Soil ecology", "Restoration"],
        "abstract": "A survey of fungal partners on the roots of replanted mangroves, "
                    "comparing three restoration ages along one estuary.",
        "pdf": "During the dry season the crew logged quillbramble lichen on every sampled root.",
    },
    {
        "dir": "item-02", "collection": ARTICLES, "type": "Article",
        "title": "Low-cost sensors for river turbidity",
        "authors": ["Silva, Ana", "Moreau, Claire"], "advisors": [],
        "issued": "2022-03", "language": "en",
        "subjects": ["Water quality", "Sensors"],
        "abstract": "Open hardware turbidity probes calibrated against a laboratory nephelometer.",
        "pdf": None,
    },
    {
        "dir": "item-03", "collection": THESES, "type": "Thesis",
        "title": "Rainwater harvesting adoption in semi-arid towns",
        "authors": ["Tanaka, Hiro"], "advisors": ["Okafor, Daniel", "Moreau, Claire"],
        "issued": "2020", "language": "en",
        "subjects": ["Water supply", "Household survey"],
        "abstract": "Interviews with households in four towns on why cisterns are or are not adopted.",
        "pdf": "The pilot cisterns were painted glimmerfen green so neighbours could spot them.",
    },
    {
        "dir": "item-04", "collection": ARTICLES, "type": "Article",
        "title": "Seed dispersal by fruit bats in fragmented forests",
        "authors": ["Ndiaye, Fatou", "Okafor, Daniel"], "advisors": [],
        "issued": "2023-09-01", "language": "en",
        "subjects": ["Seed dispersal", "Bats", "Forest fragments"],
        "abstract": "Camera traps and seed rain collectors across eleven forest patches.",
        "pdf": None,
    },
    {
        "dir": "item-05", "collection": THESES, "type": "Thesis",
        "title": "Cartografia participativa de nascentes urbanas",
        "authors": ["Costa, Bruno"], "advisors": ["Silva, Ana"],
        "issued": "2019-11-20", "language": "pt",
        "subjects": ["Nascentes", "Cartografia participativa"],
        "abstract": "Mapeamento de nascentes em bairros centrais com moradores e escolas.",
        "pdf": "Os moradores batizaram a nascente mais antiga de bica do vorlantim.",
    },
    {
        "dir": "item-06", "collection": ARTICLES, "type": "Article",
        "title": "Thermal comfort in passive school buildings",
        "authors": ["Lindqvist, Erik"], "advisors": [],
        "issued": "2024-02-10", "language": "en",
        "subjects": ["Building physics", "Schools"],
        "abstract": "A year of indoor temperature logs from six naturally ventilated classrooms.",
        "pdf": None,
    },
    {
        "dir": "item-07", "collection": THESES, "type": "Thesis", "role": "withdrawn",
        "title": "Glacier retreat signals in alpine lake cores",
        "authors": ["Novak, Petra"], "advisors": ["Okafor, Daniel"],
        "issued": "2018-05-30", "language": "en",
        "subjects": ["Paleoclimate", "Lake sediments"],
        "abstract": "Varve counts from two alpine lakes read against regional glacier records.",
        "pdf": "Core seven held a band of ashen strobilite that no other core showed.",
    },
    {
        "dir": "item-08", "collection": ARTICLES, "type": "Article", "role": "private",
        "title": "Pollinator corridors along railway verges",
        "authors": ["Haddad, Leila"], "advisors": [],
        "issued": "2025-04-22", "language": "en",
        "subjects": ["Pollinators", "Linear habitats"],
        "abstract": "Transect counts of bees and hoverflies on mown and unmown railway verges.",
        "pdf": None,
    },
    {
        # Hidden after `oai import`: OAI still serves it, only REST says it is private.
        "dir": "item-09", "collection": ARTICLES, "type": "Article", "role": "private-after-oai",
        "title": "Tide pool temperature extremes on basalt shores",
        "authors": ["Moreau, Claire"], "advisors": [],
        "issued": "2025-08", "language": "en",
        "subjects": ["Intertidal ecology", "Temperature"],
        "abstract": "Loggers in twelve tide pools recorded daily extremes through one summer.",
        "pdf": None,
    },
]


def dublin_core(item):
    rows = [("title", "none", item["title"])]
    rows += [("contributor", "author", a) for a in item["authors"]]
    rows += [("contributor", "advisor", a) for a in item["advisors"]]
    rows += [
        ("date", "issued", item["issued"]),
        ("description", "abstract", item["abstract"]),
    ]
    rows += [("subject", "none", s) for s in item["subjects"]]
    rows += [
        ("type", "none", item["type"]),
        ("language", "iso", item["language"]),
        ("publisher", "none", "Lumen University"),
    ]
    lines = ['<?xml version="1.0" encoding="UTF-8"?>', '<dublin_core schema="dc">']
    for element, qualifier, value in rows:
        lines.append(f'  <dcvalue element="{element}" qualifier="{qualifier}">{escape(value)}</dcvalue>')
    lines.append("</dublin_core>")
    return "\n".join(lines) + "\n"


def pdf(lines):
    """A one-page PDF in Helvetica: the smallest file PDFBox reads text from."""
    def pdf_string(text):
        return "(" + text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)") + ")"

    stream = "BT /F1 12 Tf 72 720 Td 16 TL\n" + "\n".join(f"{pdf_string(l)} '" for l in lines) + "\nET"
    objects = [
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
        "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
        f"<< /Length {len(stream.encode('latin-1'))} >>\nstream\n{stream}\nendstream",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
    ]
    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for number, body in enumerate(objects, start=1):
        offsets.append(len(out))
        out += f"{number} 0 obj\n{body}\nendobj\n".encode("latin-1")
    xref = len(out)
    out += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode("latin-1")
    for offset in offsets:
        out += f"{offset:010d} 00000 n \n".encode("latin-1")
    out += f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode("latin-1")
    return bytes(out)


def structure():
    return """<?xml version="1.0" encoding="UTF-8"?>
<import_structure>
  <community>
    <name>Lumen University Research</name>
    <description>Theses and articles of the fictional Lumen University, seeded for Dumont's catalog connector.</description>
    <collection>
      <name>Theses and Dissertations</name>
      <description>Graduate theses.</description>
    </collection>
    <collection>
      <name>Journal Articles</name>
      <description>Articles by faculty and students.</description>
    </collection>
  </community>
</import_structure>
"""


def main():
    (ROOT / "structure.xml").parent.mkdir(parents=True, exist_ok=True)
    (ROOT / "structure.xml").write_text(structure(), encoding="utf-8", newline="\n")
    roles = []
    for item in ITEMS:
        folder = ROOT / "saf" / item["collection"] / item["dir"]
        folder.mkdir(parents=True, exist_ok=True)
        (folder / "dublin_core.xml").write_text(dublin_core(item), encoding="utf-8", newline="\n")
        contents = ""
        if item["pdf"]:
            (folder / "fulltext.pdf").write_bytes(pdf([
                "Lumen University - full text",
                "",
                item["pdf"],
            ]))
            contents = "fulltext.pdf\tbundle:ORIGINAL\n"
        (folder / "contents").write_text(contents, encoding="utf-8", newline="\n")
        if item.get("role"):
            roles.append(f"{item['collection']}/{item['dir']} {item['role']}")
    (ROOT / "roles.txt").write_text("\n".join(roles) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
