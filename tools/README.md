# Générateurs de données de référence

Deux scripts Python **sans dépendance** produisent les fichiers de référence de
Fahasalamana Zaza, conformément au contrat du CDC §B5.1. À script identique, les
fichiers produits sont identiques à l'octet près : le calendrier est écrit en dur,
et l'annuaire, seul à tirer des valeurs au sort, le fait avec une graine fixe
(`random.seed(42)`).

| Script | Produit | Volumétrie |
|---|---|---|
| `generer_calendrier.py` | `calendrier.json` | 16 vaccins |
| `generer_csb.py` | `csb.json` | 23 régions, 76 districts, 402 centres (≈ 115 Ko) |

## Régénérer

Depuis la racine du dépôt (Python 3.9+) :

```bash
python tools/generer_calendrier.py
python tools/generer_csb.py
```

Sans argument, chaque script écrit directement dans les assets de l'application :

- `app/src/main/assets/calendrier.json`
- `app/src/main/assets/csb.json`

C'est cette copie embarquée qui permet au premier lancement de fonctionner sans
réseau (`chargerEmbarqueSiVide()`, tâche B04). Les deux scripts valident leur
sortie avant de l'écrire (identifiants uniques, pas de dépendance circulaire
entre doses, 2 à 6 districts par région, 3 à 8 centres par district) et
échouent avec un message explicite si le contrat §B5.1 n'est pas respecté.

Options utiles :

```bash
python tools/generer_csb.py --sortie /tmp/csb.json      # écrire ailleurs
python tools/generer_csb.py --version 3                 # nouvelle publication
python tools/generer_csb.py --publie-le 2026-10-01
```

## Publier une nouvelle version

Les fichiers sont servis depuis un dépôt GitHub public **séparé**,
`fahasalamana-data`, lu par l'application via `raw.githubusercontent.com` :

```
https://raw.githubusercontent.com/<organisation>/fahasalamana-data/main/v1/calendrier.json
https://raw.githubusercontent.com/<organisation>/fahasalamana-data/main/v1/csb.json
```

Marche à suivre :

1. Modifier le script (jamais le JSON à la main : il serait écrasé à la
   régénération suivante).
2. **Incrémenter `--version`** — l'application ne remplace son contenu local que
   si `distant.version > local.version` — et mettre `--publie-le` à la date du
   jour.
3. Régénérer, puis copier les deux fichiers dans `v1/` du dépôt de données et
   dans `app/src/main/assets/` du dépôt applicatif (les deux copies doivent
   rester alignées).
4. Vérifier que les URL `raw` répondent en 200 avant d'annoncer la mise à jour.

Le préfixe `v1/` fige le format : un changement **incompatible** de structure se
publie sous `v2/` avec un nouveau `schemaVersion`, pour ne pas casser les APK
déjà installés.

## Règles à ne pas casser

- **Identifiants stables.** `id` de vaccin, de région, de district et de centre
  ne changent jamais : les données personnelles (`vaccins_administres`) et les
  routes de navigation y font référence. Les identifiants de centre sont
  préfixés par leur district (`antananarivo-i-csb2-soamanga`) pour qu'ajouter
  une région plus tard n'en décale aucun.
- **`version` strictement croissant**, jamais réutilisé ni diminué.
- **`publieLe` et `source`** sont affichés tels quels dans l'écran « À propos des
  données » (tâche B15). Le calendrier reste un calendrier de **démonstration**
  tant qu'il n'a pas été validé auprès du Ministère de la Santé Publique : ne
  pas retirer cette mention de `source`.
- L'annuaire des CSB est **fictif** (noms de régions et de districts réels,
  centres et numéros inventés). Ne pas le présenter comme un annuaire officiel.
