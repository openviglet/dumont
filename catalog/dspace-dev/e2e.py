"""End-to-end check of the catalog connector's DSpace profile (roadmap D314) against the local
DSpace of this folder, through the real connector into a local Viglet Turing.

  python e2e.py turing-setup               # creates the SN site, prints a new Turing API key
  python e2e.py run --key <key>            # source over the Dumont API, harvest, then the checks

`run` expects the Dumont connector on --dumont (default http://localhost:30130), started with
the catalog plugin and turing.apiKey=<key>. It creates (or updates) the catalog source
"lumen-dspace" with the DSpace profile, starts its indexAll (a reindex when the source already
existed), waits for the SN site to settle,
and checks what D314 promises. Exit code 0 only when every check passes.

Standard library only. Local hosts only: it refuses any Turing or Dumont that is not localhost.
"""

import argparse
import http.cookiejar
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

SOURCE_NAME = "lumen-dspace"
SITE_KEY = "lumen"
OAI = "http://localhost:18080/server/oai/request"
REST = "http://localhost:18080/server/api"
LOCALE = "en_US"

# What the seed holds (seed/, build_seed.py): live items and the three that must never be found.
LIVE_TITLES = {
    "Mycorrhizal networks in restored mangrove soils",
    "Low-cost sensors for river turbidity",
    "Rainwater harvesting adoption in semi-arid towns",
    "Seed dispersal by fruit bats in fragmented forests",
    "Cartografia participativa de nascentes urbanas",
    "Thermal comfort in passive school buildings",
}
THESIS = "Mycorrhizal networks in restored mangrove soils"


def local(url):
    host = urllib.parse.urlparse(url).hostname
    if host not in ("localhost", "127.0.0.1"):
        sys.exit(f"Refusing {url}: this script talks to local services only.")
    return url.rstrip("/")


def request(opener, method, url, body=None, headers=None, timeout=60):
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method, headers=dict(headers or {}))
    if data is not None:
        req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    with opener.open(req, timeout=timeout) as response:
        text = response.read().decode("utf-8")
        return json.loads(text) if text.strip() else None


def turing_setup(turing, password, site):
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    opener.open(turing + "/api/discovery", timeout=30).read()

    def xsrf():
        return {c.name: c.value for c in jar}.get("XSRF-TOKEN", "")

    request(opener, "POST", turing + "/api/login", {"username": "admin", "password": password},
            {"X-XSRF-TOKEN": xsrf()})
    # The connector converges the site's fields through Turing's manifest, but that call cannot
    # create a site: a new one needs the search engine instance, which only an admin session sees.
    engines = request(opener, "GET", turing + "/api/se", headers={"X-XSRF-TOKEN": xsrf()}) or []
    engine = next((e for e in engines if e.get("enabled")), engines[0] if engines else None)
    if engine is None:
        sys.exit("No search engine instance in this Turing.")
    request(opener, "POST", turing + "/api/sn/manifest", {
        "name": site, "description": "Local DSpace (Dumont catalog/dspace-dev)",
        "seInstanceId": engine["id"], "locales": [LOCALE],
        "fields": [
            {"name": "site_key", "type": "STRING", "mandatory": False, "multiValued": False,
             "facet": True, "facetName": {LOCALE: "Site"}},
        ],
    }, {"X-XSRF-TOKEN": xsrf()})
    # A new site's own author and type are single-valued, non-facet fields. The DSpace schema
    # declares both multi-valued facets, and the connector's manifest declares no migration, so
    # Turing refuses the whole manifest as a breaking change (409) and the site keeps them as
    # they were. A second call flips them with the RECREATE migration Turing asks for; in the
    # creating call above, the same migration answers 500.
    def facet(name, label):
        return {"name": name, "type": "STRING", "mandatory": False, "multiValued": True, "facet": True,
                "description": label, "facetName": {LOCALE: label}}

    result = request(opener, "POST", turing + "/api/sn/manifest", {
        "name": site, "locales": [LOCALE],
        "fields": [facet("author", "Author"), facet("type", "Type")],
        "migrations": [{"field": "author", "action": "RECREATE"}, {"field": "type", "action": "RECREATE"}],
    }, {"X-XSRF-TOKEN": xsrf()})
    print(f"SN site '{site}' ({LOCALE}) is provisioned; migrated: {result.get('fieldsMigrated')}",
          file=sys.stderr)
    token = request(opener, "POST", turing + "/api/dev/token",
                    {"title": "dumont-dspace-dev", "description": "catalog/dspace-dev e2e"},
                    {"X-XSRF-TOKEN": xsrf()})
    print(token["token"])


def search(turing, site, key, query="*", fq=(), rows=50):
    params = [("q", query), ("rows", str(rows)), ("_setlocale", LOCALE)] + [("fq[]", f) for f in fq]
    url = f"{turing}/api/sn/{urllib.parse.quote(site)}/search?" + urllib.parse.urlencode(params)
    opener = urllib.request.build_opener()
    return request(opener, "GET", url, headers={"Key": key})


def docs(result):
    """The hits' fields, with Turing's highlight markup taken out of the title."""
    hits = [dict(d["fields"]) for d in result["results"]["document"]]
    for hit in hits:
        if isinstance(hit.get("title"), str):
            hit["title"] = re.sub(r"</?mark>", "", hit["title"])
    return hits


def facet(result, name):
    for f in result["widget"]["facet"]:
        if f["name"] == name:
            return {v["label"]: v["count"] for v in f["facets"]}
    return {}


def run(args):
    turing, dumont = local(args.turing), local(args.dumont)
    # Dumont checks the Key on every /api call and, under /api/v2/catalog, a CSRF token on every
    # write: the token from /api/csrf, sent back as its header beside the XSRF-TOKEN cookie the
    # same call set. A write rotates it, so each one asks again.
    api = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    key = {"Key": args.key}

    def write(method, path, body=None):
        csrf = request(api, "GET", dumont + "/api/csrf", headers=key)
        return request(api, method, dumont + path, body, dict(key, **{csrf["headerName"]: csrf["token"]}))

    source = {
        "name": SOURCE_NAME,
        "description": "Local DSpace 8 (catalog/dspace-dev), fictional Lumen University",
        "locale": LOCALE,
        "oaiPmhBaseUrl": OAI,
        "oaiPmhProfile": "dspace",
        "dspaceRestUrl": REST,
        "oaiPmhFullText": True,
        "siteKey": SITE_KEY,
        "turSNSites": [args.site],
    }
    existing = [s for s in request(api, "GET", dumont + "/api/v2/catalog/source", headers=key)
                if s.get("name") == SOURCE_NAME]
    if existing:
        source_id = existing[0]["id"]
        write("PUT", f"/api/v2/catalog/source/{source_id}", dict(source, id=source_id))
        print(f"Updated catalog source {SOURCE_NAME} ({source_id})")
    else:
        source_id = write("POST", "/api/v2/catalog/source", source)["id"]
        print(f"Created catalog source {SOURCE_NAME} ({source_id})")
    if existing:
        # A rerun reindexes: indexAll would skip every item the connector's ledger already holds
        # as sent, even when the site lost it (or did not exist when it was sent). This call
        # returns only when the run has, so it gets the whole indexing budget.
        request(api, "GET", f"{dumont}/api/v2/connector/reindex/{SOURCE_NAME}/all", headers=key,
                timeout=args.timeout)
        print("reindex/all started; waiting for the SN site to settle...")
    else:
        request(api, "GET", f"{dumont}/api/v2/catalog/source/{source_id}/indexAll", headers=key)
        print("indexAll started; waiting for the SN site to settle...")

    last, stable, count = None, 0, 0
    deadline = time.time() + args.timeout
    while time.time() < deadline:
        time.sleep(5)
        try:
            count = search(turing, args.site, args.key, fq=[f"site_key:{SITE_KEY}"])["queryContext"]["count"]
        except urllib.error.HTTPError:
            count = 0
        stable = stable + 1 if count == last and count > 0 else 0
        last = count
        print(f"  {count} document(s)")
        if stable >= 3:
            break

    failures = []

    def check(label, ok, detail):
        print(f"[{'PASS' if ok else 'FAIL'}] {label}: {detail}")
        if not ok:
            failures.append(label)

    every = docs(search(turing, args.site, args.key, fq=[f"site_key:{SITE_KEY}"]))
    titles = {d.get("title") for d in every}
    check("only the live items are indexed", titles == LIVE_TITLES,
          f"{len(titles)} indexed: {sorted(titles)}")

    hit = docs(search(turing, args.site, args.key, "mycorrhizal"))
    check("title word 'mycorrhizal'", [d.get("title") for d in hit] == [THESIS],
          [d.get("title") for d in hit])

    hit = docs(search(turing, args.site, args.key, "quillbramble"))
    check("PDF-only word 'quillbramble' (full text)", [d.get("title") for d in hit] == [THESIS],
          [d.get("title") for d in hit])

    result = search(turing, args.site, args.key, fq=[f"site_key:{SITE_KEY}"])
    authors = facet(result, "author")
    grouped = docs(search(turing, args.site, args.key, fq=['author:Silva, Ana']))
    check("author facet groups 'Silva, Ana'",
          authors.get("Silva, Ana") == 2 and THESIS in {d.get("title") for d in grouped},
          f"facet {authors}; filtered: {sorted(d.get('title') for d in grouped)}")

    for word, why in (("glacier", "withdrawn before the OAI index"),
                      ("pollinator", "private before the OAI index"),
                      ("basalt", "private after the OAI index: only REST knows")):
        hit = docs(search(turing, args.site, args.key, word))
        check(f"'{word}' absent ({why})", not hit, [d.get("title") for d in hit])

    thesis = next((d for d in every if d.get("title") == THESIS), {})
    check("community and collection are names",
          thesis.get("community") in (["Lumen University Research"], "Lumen University Research")
          and thesis.get("collection") in (["Theses and Dissertations"], "Theses and Dissertations"),
          f"community={thesis.get('community')} collection={thesis.get('collection')}")

    print("\nThe thesis as indexed:")
    for name in ("title", "author", "advisors", "publication_date", "abstract", "subjects", "type",
                 "language", "publisher", "community", "collection", "url", "image", "site_key"):
        print(f"  {name}: {thesis.get(name)}")
    print(f"  text: {str(thesis.get('text'))[:160]!r}")

    if failures:
        sys.exit(f"\n{len(failures)} check(s) failed: {failures}")
    print("\nEvery check passed.")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--turing", default="http://localhost:2700")
    parser.add_argument("--site", default="LumenRepository", help="the SN site to index into")
    sub = parser.add_subparsers(dest="command", required=True)
    setup = sub.add_parser("turing-setup", help="create the SN site and mint a Turing API key")
    setup.add_argument("--password", default="dumont-dev", help="Turing's admin password")
    go = sub.add_parser("run", help="create the source, harvest, check")
    go.add_argument("--key", required=True, help="the Turing API key the connector was started with")
    go.add_argument("--dumont", default="http://localhost:30130")
    go.add_argument("--timeout", type=int, default=300, help="seconds to wait for indexing")
    args = parser.parse_args()
    if args.command == "turing-setup":
        turing_setup(local(args.turing), args.password, args.site)
    else:
        run(args)


if __name__ == "__main__":
    main()
