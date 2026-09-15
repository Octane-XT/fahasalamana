#!/usr/bin/env python3
"""Génère calendrier.json — Fahasalamana Zaza (tâche B03, contrat CDC §B5.1).

Calendrier de DÉMONSTRATION inspiré d'un PEV : les valeurs sont à remplacer par
la source officielle avant tout usage réel. Aucune dépendance externe.

    python tools/generer_calendrier.py
    python tools/generer_calendrier.py --sortie app/src/main/assets/calendrier.json
    python tools/generer_calendrier.py --version 4 --publie-le 2026-10-01

Contrat respecté (§B5.1) : schemaVersion, version (entier strictement croissant),
publieLe, source, identifiants de vaccin stables.
"""
import argparse
import json
import random
import sys
from datetime import date
from pathlib import Path

# Graine fixe : les données produites sont reproductibles à l'octet près (CDC Annexe 1).
random.seed(42)

SCHEMA_VERSION = 1
VERSION = 3
PUBLIE_LE = date(2026, 9, 14)
SOURCE = (
    "Calendrier de démonstration — projet universitaire. "
    "À valider auprès du Ministère de la Santé Publique."
)

# Chemin par défaut : assets de l'application, relatif à la racine du dépôt.
RACINE = Path(__file__).resolve().parent.parent
SORTIE_DEFAUT = RACINE / "app" / "src" / "main" / "assets" / "calendrier.json"


def vac(id_, nom, dose, ordre, age, dep, tol, desc):
    """Une ligne du calendrier de référence (cf. VaccinReference, §B4)."""
    return {
        "id": id_,
        "nom": nom,
        "dose": dose,
        "ordre": ordre,
        "ageJours": age,       # délai depuis la naissance, ou depuis « dependDe » si non nul
        "dependDe": dep,
        "toleranceJours": tol,  # fenêtre après la date prévue avant « En retard »
        "description": desc,
    }


VACCINS = [
    vac("bcg", "BCG", "dose unique", 1, 0, None, 30, "À la naissance"),
    vac("vpo0", "Polio oral", "dose 0", 2, 0, None, 14, "À la naissance"),
    vac("penta1", "Pentavalent", "1re dose", 3, 42, None, 14, "6 semaines"),
    vac("vpo1", "Polio oral", "1re dose", 4, 42, None, 14, "6 semaines"),
    vac("pcv1", "Pneumocoque", "1re dose", 5, 42, None, 14, "6 semaines"),
    vac("rota1", "Rotavirus", "1re dose", 6, 42, None, 14, "6 semaines"),
    vac("penta2", "Pentavalent", "2e dose", 7, 28, "penta1", 14, "4 semaines après la 1re dose"),
    vac("vpo2", "Polio oral", "2e dose", 8, 28, "vpo1", 14, "4 semaines après la 1re dose"),
    vac("pcv2", "Pneumocoque", "2e dose", 9, 28, "pcv1", 14, "4 semaines après la 1re dose"),
    vac("rota2", "Rotavirus", "2e dose", 10, 28, "rota1", 14, "4 semaines après la 1re dose"),
    vac("penta3", "Pentavalent", "3e dose", 11, 28, "penta2", 14, "4 semaines après la 2e dose"),
    vac("vpo3", "Polio oral", "3e dose", 12, 28, "vpo2", 14, "4 semaines après la 2e dose"),
    vac("pcv3", "Pneumocoque", "3e dose", 13, 28, "pcv2", 14, "4 semaines après la 2e dose"),
    vac("vpi", "Polio injectable", "dose unique", 14, 98, None, 14, "14 semaines"),
    vac("rr1", "Rougeole-Rubéole", "1re dose", 15, 270, None, 30, "9 mois"),
    vac("rr2", "Rougeole-Rubéole", "2e dose", 16, 450, None, 30, "15 mois"),
]


def construire(version, publie_le):
    return {
        "schemaVersion": SCHEMA_VERSION,
        "version": version,
        "publieLe": str(publie_le),
        "source": SOURCE,
        "vaccins": VACCINS,
    }


def verifier(doc):
    """Vérifie le contrat §B5.1. Lève AssertionError au premier écart."""
    assert doc["schemaVersion"] == SCHEMA_VERSION, "schemaVersion inattendu"
    assert isinstance(doc["version"], int) and doc["version"] > 0, "version doit être un entier > 0"
    assert doc["publieLe"] and doc["source"], "publieLe et source sont obligatoires"

    vaccins = doc["vaccins"]
    assert len(vaccins) == 16, f"16 vaccins attendus, {len(vaccins)} trouvés"

    ids = [v["id"] for v in vaccins]
    assert len(ids) == len(set(ids)), "identifiants de vaccin dupliqués"

    ordres = sorted(v["ordre"] for v in vaccins)
    assert ordres == list(range(1, len(vaccins) + 1)), "ordre non contigu à partir de 1"

    champs = {"id", "nom", "dose", "ordre", "ageJours", "dependDe", "toleranceJours", "description"}
    connus = set(ids)
    par_id = {v["id"]: v for v in vaccins}
    for v in vaccins:
        assert set(v) == champs, f"champs inattendus sur {v['id']}"
        assert v["ageJours"] >= 0, f"ageJours négatif sur {v['id']}"
        assert v["toleranceJours"] >= 0, f"toleranceJours négatif sur {v['id']}"
        dep = v["dependDe"]
        if dep is not None:
            assert dep in connus, f"{v['id']} dépend de {dep}, absent du calendrier"
            assert par_id[dep]["ordre"] < v["ordre"], f"{v['id']} dépend d'une dose d'ordre supérieur"

    # Aucune dépendance circulaire (le calculateur boucle sinon — cf. R1).
    for v in vaccins:
        vus, courant = set(), v
        while courant["dependDe"] is not None:
            assert courant["id"] not in vus, f"dépendance circulaire autour de {v['id']}"
            vus.add(courant["id"])
            courant = par_id[courant["dependDe"]]


def main(argv=None):
    parseur = argparse.ArgumentParser(description="Génère calendrier.json (Fahasalamana Zaza).")
    parseur.add_argument("--sortie", type=Path, default=SORTIE_DEFAUT,
                         help=f"fichier à écrire (défaut : {SORTIE_DEFAUT})")
    parseur.add_argument("--version", type=int, default=VERSION,
                         help="entier strictement croissant à chaque publication")
    parseur.add_argument("--publie-le", default=str(PUBLIE_LE), help="date ISO yyyy-MM-dd")
    args = parseur.parse_args(argv)

    doc = construire(args.version, date.fromisoformat(args.publie_le))
    verifier(doc)

    args.sortie.parent.mkdir(parents=True, exist_ok=True)
    with args.sortie.open("w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
        f.write("\n")

    taille = args.sortie.stat().st_size
    print(f"{args.sortie} : {len(doc['vaccins'])} vaccins, version {doc['version']}, {taille / 1024:.1f} Ko")
    return 0


if __name__ == "__main__":
    sys.exit(main())
