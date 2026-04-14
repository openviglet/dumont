# Retro Arcade — Insert Coin to Continue

> *"The Arcade Vault — where every cabinet has a backstory and every pixel is searchable."*

**Retro Arcade** is the neon-drenched showcase SPA for the **Dumont Database Connector**. Point it at a SQL database full of classic arcade games, hit index, and watch a coin-op catalog materialize in glorious CRT-inspired style.

![DB Connector](https://img.shields.io/badge/connector-Database-f59e0b?style=flat-square)
![JDBC Ready](https://img.shields.io/badge/JDBC-PostgreSQL%20%7C%20MariaDB%20%7C%20MySQL-336791?style=flat-square)
![React 19](https://img.shields.io/badge/React-19-61dafb?style=flat-square)

---

## What's the pitch?

You have a database. Maybe it's a product catalog, a CMS, an inventory, a legacy Oracle behemoth. Boring, right? **Not anymore.** The Retro Arcade sample shows how easily the Dumont DB Connector can transform any SQL table into a full-text, faceted, beautifully designed search experience — dressed up here as an arcade game vault circa 1987.

The sample ships with two preconfigured sources:

- 🎮 **Retro Arcade Games** — PostgreSQL-backed catalog of classic coin-op titles (Pac-Man, Galaga, Street Fighter II… bring your own data)
- 🧬 **Sample MariaDB (Rfam)** — a live connection to the European Bioinformatics Institute's public Rfam database, indexing RNA families (because why not show off?)

## Why you'll love it

- 🕹️ **Arcade Vault aesthetic** — neon particle animations, synthwave gradients, and a search bar that practically begs you to type `pac-man`.
- 💾 **HTML sanitization on the fly** — the sample strips HTML from the `text` and `abstract` columns so messy legacy data looks clean.
- 🏷️ **Multi-valued field support** — genres, tags, categories split on a custom separator (`,`) and rendered as pills.
- 🧩 **Custom extension hook** — drop in your own `DumDbExt*` class to enrich rows with computed fields, joins, or external lookups.
- 📑 **Pagination, facets, detail views** — the full SDK treatment, wrapped in a retro skin.
- 🎨 **Dark-mode default** — because arcades are meant to be dark.

## What the SPA does

1. Queries Turing ES for every indexed game.
2. Shows them in a card grid: title, developer, release date, genre tags.
3. Lets you filter by facets (genre, era, developer…).
4. Dives into a detail page with the full description and metadata when you click a card.
5. Handles empty results with a very-on-brand *"No games found. Insert coin to try again."*

## SQL → Search in one query

```sql
SELECT id,
       title,
       description       AS text,
       summary           AS abstract,
       developer         AS author,
       release_date      AS publication_date,
       updated_at        AS modification_date,
       genres            AS tags
FROM   games
```

That's it. The connector maps those aliases into Turing's canonical fields, chunks by 50 rows, normalizes encoding, and indexes everything — with no middle-layer, no ETL pipeline, no tears.

## Architecture

```
  Any JDBC Database  ──►  DB Plugin  ──►  Dumont Connector  ──►  Turing ES  ──►  Retro Arcade SPA
  (Postgres, MariaDB,                                                                  │
   MySQL, Oracle…)                                                                      ▼
                                                                             You, finding
                                                                             your high score
```

## Get coin-operated in 3 steps

1. Download the zip from the **Dumont Marketplace**.
2. Upload it to a Turing SN Site named `retro-arcade`.
3. Configure a DB source with your JDBC URL and run *Index All*.

## Tech Stack

React 19 · TypeScript · Vite · Tailwind CSS v4 · shadcn/ui · Tabler Icons · Turing React SDK

---

**Game over?** Drop a quarter at [openviglet/dumont](https://github.com/openviglet/dumont/issues) to continue.
