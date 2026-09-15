#!/usr/bin/env python3
"""Génère csb.json — annuaire fictif des centres de santé de base (tâche B03, CDC §B5.1).

Données ENTIÈREMENT FICTIVES : les noms de régions et de districts sont réels,
les centres, numéros de téléphone et horaires sont inventés. Ne pas appeler ces
numéros. Aucune dépendance externe.

    python tools/generer_csb.py
    python tools/generer_csb.py --sortie app/src/main/assets/csb.json
    python tools/generer_csb.py --version 3 --publie-le 2026-10-01

Contrat respecté (§B5.1) : schemaVersion, version (entier strictement croissant),
publieLe, source, identifiants de région / district / centre stables.
"""
import argparse
import json
import random
import sys
import unicodedata
from datetime import date
from pathlib import Path

# Graine fixe : deux exécutions produisent le même fichier à l'octet près (CDC Annexe 1).
random.seed(42)

SCHEMA_VERSION = 1
VERSION = 2
PUBLIE_LE = date(2026, 9, 14)
SOURCE = "Annuaire fictif généré pour un projet universitaire"

CENTRES_MIN, CENTRES_MAX = 3, 8   # §B5.1 : 3 à 8 centres par district

RACINE = Path(__file__).resolve().parent.parent
SORTIE_DEFAUT = RACINE / "app" / "src" / "main" / "assets" / "csb.json"

# 23 régions, 2 à 6 districts par région (§B5.1). Noms réels, découpage simplifié.
REGIONS = {
    "Analamanga": ["Antananarivo I", "Antananarivo II", "Ambohidratrimo", "Ankazobe",
                   "Andramasina"],
    "Vakinankaratra": ["Antsirabe I", "Antsirabe II", "Betafo", "Faratsiho", "Ambatolampy"],
    "Itasy": ["Miarinarivo", "Soavinandriana", "Arivonimamo"],
    "Bongolava": ["Tsiroanomandidy", "Fenoarivobe"],
    "Atsinanana": ["Toamasina I", "Toamasina II", "Vatomandry", "Brickaville", "Mahanoro"],
    "Analanjirofo": ["Fenoarivo Atsinanana", "Soanierana Ivongo", "Sainte-Marie", "Vavatenina"],
    "Alaotra-Mangoro": ["Ambatondrazaka", "Moramanga", "Amparafaravola"],
    "Haute Matsiatra": ["Fianarantsoa I", "Fianarantsoa II", "Ambalavao"],
    "Amoron'i Mania": ["Ambositra", "Fandriana", "Manandriana"],
    "Vatovavy": ["Mananjary", "Nosy Varika", "Ifanadiana"],
    "Fitovinany": ["Manakara", "Vohipeno", "Ikongo"],
    "Atsimo-Atsinanana": ["Farafangana", "Vangaindrano", "Midongy-Atsimo"],
    "Ihorombe": ["Ihosy", "Ivohibe"],
    "Boeny": ["Mahajanga I", "Mahajanga II", "Marovoay", "Ambato-Boeny"],
    "Sofia": ["Antsohihy", "Bealanana", "Mandritsara"],
    "Betsiboka": ["Maevatanana", "Tsaratanana", "Kandreho"],
    "Melaky": ["Maintirano", "Besalampy"],
    "Atsimo-Andrefana": ["Toliara I", "Toliara II", "Sakaraha", "Morombe"],
    "Androy": ["Ambovombe", "Beloha", "Tsihombe"],
    "Anosy": ["Taolagnaro", "Amboasary Atsimo", "Betroka"],
    "Menabe": ["Morondava", "Miandrivazo"],
    "Diana": ["Antsiranana I", "Antsiranana II", "Nosy Be", "Ambanja"],
    "Sava": ["Sambava", "Antalaha", "Vohemar", "Andapa"],
}

PREFIXES = ["Ambohi", "Andra", "Anka", "Antan", "Ambato", "Tsara", "Maha", "Ampasi", "Manja", "Soa"]
SUFFIXES = ["manga", "tsoa", "be", "kely", "vato", "rano", "fotsy", "mainty", "lava", "tsimo"]

HORAIRES = [
    "Lun–Ven 7h30–16h00, vaccination mardi et jeudi matin",
    "Lun–Sam 8h00–15h00, vaccination lundi matin",
    "Lun–Ven 8h00–16h00, vaccination mercredi",
    "Lun–Ven 7h00–15h30, vaccination vendredi matin",
]


def slug(s):
    """Identifiant stable : minuscules, sans accent, séparé par des tirets."""
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower()
    return "".join(c if c.isalnum() else "-" for c in s).strip("-")


def nom_quartier():
    return random.choice(PREFIXES) + random.choice(SUFFIXES)


def telephone():
    return (f"+261 3{random.choice('234')} {random.randint(10, 99)} "
            f"{random.randint(100, 999)} {random.randint(10, 99)}")


def centres_du_district(district_id, district_nom):
    """3 à 8 centres, noms uniques à l'intérieur du district."""
    centres, pris = [], set()
    for _ in range(random.randint(CENTRES_MIN, CENTRES_MAX)):
        typ = random.choice(["CSB1", "CSB2", "CSB2"])   # les CSB2 sont majoritaires
        quartier = nom_quartier()
        while (typ, quartier) in pris:
            quartier = nom_quartier()
        pris.add((typ, quartier))
        # Identifiant préfixé par le district : ajouter une région plus tard
        # ne décale aucun identifiant existant (§B5.1, identifiants stables).
        centres.append({
            "id": f"{district_id}-{slug(typ)}-{slug(quartier)}",
            "nom": f"{typ} {quartier}",
            "type": typ,
            "telephone": telephone(),
            "horaires": random.choice(HORAIRES),
            "adresse": f"{quartier}, {district_nom}",
        })
    return sorted(centres, key=lambda c: c["nom"])


def construire(version, publie_le):
    regions = []
    for region, districts_noms in REGIONS.items():
        districts = []
        for district_nom in districts_noms:
            district_id = slug(district_nom)
            districts.append({
                "id": district_id,
                "nom": district_nom,
                "centres": centres_du_district(district_id, district_nom),
            })
        regions.append({"id": slug(region), "nom": region, "districts": districts})

    return {
        "schemaVersion": SCHEMA_VERSION,
        "version": version,
        "publieLe": str(publie_le),
        "source": SOURCE,
        "regions": regions,
    }


def verifier(doc):
    """Vérifie le contrat §B5.1. Lève AssertionError au premier écart."""
    assert doc["schemaVersion"] == SCHEMA_VERSION, "schemaVersion inattendu"
    assert isinstance(doc["version"], int) and doc["version"] > 0, "version doit être un entier > 0"
    assert doc["publieLe"] and doc["source"], "publieLe et source sont obligatoires"

    regions = doc["regions"]
    assert len(regions) == 23, f"23 régions attendues, {len(regions)} trouvées"

    ids_region, ids_district, ids_centre = set(), set(), set()
    champs_centre = {"id", "nom", "type", "telephone", "horaires", "adresse"}

    for r in regions:
        assert set(r) == {"id", "nom", "districts"}, f"champs inattendus sur la région {r.get('id')}"
        assert r["id"] and r["nom"], "région sans identifiant ou sans nom"
        assert r["id"] not in ids_region, f"identifiant de région dupliqué : {r['id']}"
        ids_region.add(r["id"])

        assert 2 <= len(r["districts"]) <= 6, \
            f"{r['id']} : {len(r['districts'])} districts (2 à 6 attendus)"

        for d in r["districts"]:
            assert set(d) == {"id", "nom", "centres"}, f"champs inattendus sur le district {d.get('id')}"
            assert d["id"] not in ids_district, f"identifiant de district dupliqué : {d['id']}"
            ids_district.add(d["id"])

            assert CENTRES_MIN <= len(d["centres"]) <= CENTRES_MAX, \
                f"{d['id']} : {len(d['centres'])} centres ({CENTRES_MIN} à {CENTRES_MAX} attendus)"

            for c in d["centres"]:
                assert set(c) == champs_centre, f"champs inattendus sur le centre {c.get('id')}"
                assert c["id"] not in ids_centre, f"identifiant de centre dupliqué : {c['id']}"
                ids_centre.add(c["id"])
                assert c["type"] in ("CSB1", "CSB2"), f"type inconnu : {c['type']}"
                assert c["telephone"].startswith("+261 "), f"téléphone hors format : {c['telephone']}"
                assert c["nom"] and c["horaires"] and c["adresse"], f"champ vide sur {c['id']}"

    return len(ids_region), len(ids_district), len(ids_centre)


def main(argv=None):
    parseur = argparse.ArgumentParser(description="Génère csb.json (Fahasalamana Zaza).")
    parseur.add_argument("--sortie", type=Path, default=SORTIE_DEFAUT,
                         help=f"fichier à écrire (défaut : {SORTIE_DEFAUT})")
    parseur.add_argument("--version", type=int, default=VERSION,
                         help="entier strictement croissant à chaque publication")
    parseur.add_argument("--publie-le", default=str(PUBLIE_LE), help="date ISO yyyy-MM-dd")
    args = parseur.parse_args(argv)

    doc = construire(args.version, date.fromisoformat(args.publie_le))
    nb_regions, nb_districts, nb_centres = verifier(doc)

    args.sortie.parent.mkdir(parents=True, exist_ok=True)
    with args.sortie.open("w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
        f.write("\n")

    taille = args.sortie.stat().st_size
    print(f"{args.sortie} : {nb_regions} régions, {nb_districts} districts, "
          f"{nb_centres} centres, version {doc['version']}, {taille / 1024:.1f} Ko")
    return 0


if __name__ == "__main__":
    sys.exit(main())
