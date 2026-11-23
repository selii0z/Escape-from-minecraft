# Audit du cahier des charges — Escape from Minecraft

Audit statique du code de la branche `fix/raid-inventory-cleanup-0.8.1` au 23 novembre 2026. **Une présence dans le code ne vaut pas validation en jeu.**

## Référence : décisions de conception

Raids solo/duo ; six cartes ; Forêt Noire prototype puis Zone industrielle ; instances réinitialisées ; extraction, équipement perdu/récupéré ; ennemis, renforts et boss garanti ; butin aléatoire ; inventaire Minecraft ; corps fouillables ; coffre de stockage ; tableau physique ; contrats facultatifs, au maximum trois actifs et renouvelables ; quatre marchands illustrés ; armes TaCZ ; reliques rares ; pas de Cobblemon/mobs vanilla dans les cartes ; ciel stable ; environnements détaillés.

## État vérifié dans le dépôt

| Demande | Statut | Indice technique / réserve |
| --- | --- | --- |
| Six cartes jouables | **MANQUANT** | `OperationsMenu` ne propose des départs que pour Forêt Noire et Zone industrielle. Des images `military.png`, `quarantine.png`, `underground.png`, `village.png` existent, mais ne prouvent pas l'existence de cartes jouables. |
| Raids solo/duo | **CODE PRÉSENT** | `ForestExpedition.depart` et `OperationsGroups` ; test en duo nécessaire. |
| Instance de raid / occupation exclusive | **PARTIEL** | Une dimension partagée avec session exclusive, pas des dimensions instanciées indépendantes par groupe. `ForestRaid.begin` et `hasActiveSession`. |
| Réinitialisation après raid | **PARTIEL** | `ForestRaid.closeSession` supprime ennemis, corps et objets au sol ; réinitialisation complète des coffres/blocs non démontrée. |
| Extraction et retour | **CODE PRÉSENT** | `ForestExpedition.returnToOrigin` ; tester succès, mort, déconnexion et échec de téléportation. |
| Perte / retour d'équipement | **À TESTER / PARTIEL** | Sauvegarde/restauration d'inventaire, et drops vanilla en cas de mort. Pas de garantie d'un mécanisme d'assurance/récupération. |
| Coffre de stockage personnel | **NON IDENTIFIÉ** | Pas de classe ou interface dédiée au stockage dans les modules examinés. |
| Corps fouillables | **PARTIEL** | `GuardCorpse` pour les gardes ; la mort du joueur utilise les drops vanilla, pas un corps fouillable dédié. |
| Ennemis présents et renforts | **À TESTER** | `ForestRaid.populateNear` fait apparaître des gardes autour du joueur. Il faut vérifier les déclencheurs et la densité des renforts. |
| Boss garanti | **À TESTER** | `ForestRaid` utilise un emplacement supplémentaire en forêt ; la garantie de spawn/présence du boss n'est pas démontrée par cet audit. |
| Difficulté solo/duo identique | **À TESTER** | Vérifier l'absence de multiplicateur selon la taille du groupe. |
| Butin aléatoire, coffres | **CODE PRÉSENT** | `ForestRaid.putCrate` et choix d'objets ; contrôler quantité et répartition. |
| Tableau physique | **CODE PRÉSENT** | `OperationsBoardBlock`. |
| Contrats facultatifs, maximum 3, rejouables | **CODE PRÉSENT** | `SupplyContract` limite à 3 actifs et permet de renouveler les contrats terminés. |
| Quatre marchands illustrés | **CODE PRÉSENT** | Armurier, Médecin, Récupérateur, Courtier ; quatre textures `merchant_*.png` disponibles. |
| Catalogue TaCZ | **CODE PRÉSENT, EXHAUSTIVITÉ À TESTER** | `OperationsShop` parcourt les armes installées ; vérifier armes, munitions et accessoires en jeu. |
| Reliques rares / vente / contrats | **CODE PRÉSENT** | `RaidRelic`, `OperationsShop`, `SupplyContract` ; tester les déblocages. |
| Pas de Cobblemon/mobs vanilla | **À TESTER** | Examiner les règles de spawn et les cas de mobs provenant d'autres mods. |
| Ciel stable | **CODE PRÉSENT, À TESTER** | `ForestExpedition.synchronizeAtmosphere` et paquets client de temps. |
| Décors détaillés, chemins et bâtiments réalistes | **QUALITÉ NON VALIDÉE** | Génération procédurale `ForestPlan`, `ForestBuildings`, `IndustrialZone` ; inspection visuelle indispensable. |
| Bilan de raid, XP et historique | **CODE PRÉSENT** | `RaidResultScreen`, `SupplyContract`, `OperationsScreen` ; compilations réussies, tests de jeu requis. |

## Priorités recommandées

1. **Bloquants de gameplay** : coffre de stockage, règles de perte/récupération, réinitialisation complète des raids, corps de joueurs.
2. **Contenu promis** : quatre cartes supplémentaires réellement jouables.
3. **Boucle de raid** : boss garanti, renforts, loot, difficulté identique solo/duo.
4. **Finitions** : vérifier la suppression des mobs indésirables, la stabilité du ciel, la qualité des routes et bâtiments, les portraits et interfaces.
5. **Validation** : exécuter `docs/RAID_TEST_PLAN.md` sur un JAR récent et sur serveur dédié.

## Limites

Audit de lecture de fichiers, sans lancement de Minecraft ni exécution des scénarios. Les statuts **NON IDENTIFIÉ** et **À TESTER** ne sont pas des preuves absolues d'absence. Aucun élément ne doit être marqué « terminé » avant une vérification en jeu.
