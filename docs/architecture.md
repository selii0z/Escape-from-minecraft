# Architecture technique proposée — à valider par investigation
## Modules
- `operations` : bloc, menus et sélection de carte, jour/nuit, contrats, marchands.
- `raid` : machine à états (préparation, actif, extraction, terminé), sessions par carte et participants.
- `worldgen` : dimension dédiée, zones de carte, terrain déterministe, routes, bâtiments uniques et intérieurs accessibles.
- `protection` : blocage serveur de placement, casse, explosions et modifications par mods; tests d'intégration.
- `loot` : points candidats de conteneurs, sélection aléatoire par raid, tables de loot, nettoyage.
- `combat` : adaptateur TaCZ isolé, IA, boss, renforts; **faisabilité à vérifier en premier**.
- `extraction` : zones, conditions, délais, sorties individuelles.
- `party` : invitations, synchronisation solo/duo et reconnexion.
- `persistence` : état durable des raids, provenance des objets, récupération après crash, anti-duplication.
- `contracts` : objectifs individuels, récompenses immédiates, crédits et XP.

## Principes
Autorité serveur pour inventaires, dégâts, loot, extractions et récompenses. Éviter la génération massive synchrone sur le thread serveur; utiliser une préparation bornée et sécurisée. Génération unique persistée; à chaque raid seuls les objets temporaires et entités sont renouvelés. Ne pas coder d'intégration TaCZ supposée : étudier d'abord les signatures réelles, dépendances et licences. Ne pas modifier le modpack de production avant validation sur une copie.

## Livraisons
1. Audit compatibilité et projet NeoForge compilable.
2. Dimension, terrain, tableau et téléportation.
3. Loot, chronomètre, extraction, pertes et persistance.
4. Combat TaCZ, PNJ et boss.
5. Duo, reconnexion et tests de robustesse.

Chaque étape exige preuve de compilation et, si pertinent, de test sur serveur dédié.
