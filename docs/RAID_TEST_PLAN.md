# Validation en jeu — bilan des raids

Version cible : branche `fix/raid-inventory-cleanup-0.8.1` (NeoForge 1.21.1).

## Préparation
- Utiliser le JAR de la **dernière** compilation GitHub Actions réussie, pas un ancien artefact.
- Faire une sauvegarde du monde et du dossier de configuration.
- Tester en solo, puis avec deux joueurs sur un serveur dédié.
- Relever l'XP Escape et les contrats actifs avant chaque raid.

## Scénarios prioritaires

| Test | Action | Résultat attendu |
| --- | --- | --- |
| R01 | Entrer, éliminer 2 gardes, ouvrir 3 coffres distincts, extraire | Écran « EXTRACTION RÉUSSIE », 2 éliminations, 3 coffres, 13 XP de base et bonus de 10 % arrondi |
| R02 | Ouvrir deux fois le même coffre | Un seul coffre comptabilisé, une seule attribution d'XP de fouille |
| R03 | Mourir en raid après avoir gagné 60 XP d'activité | Écran « RAID ÉCHOUÉ » après réapparition, 54 XP finaux, malus -6 XP |
| R04 | Terminer un contrat de sortie pendant l'extraction | Contrat compté dans le bilan du raid |
| R05 | Faire 11 raids consécutifs | Historique limité aux 10 derniers, le plus récent en premier |
| R06 | Quitter et relancer le monde | XP Escape, contrats et historique conservés |
| R07 | Mourir hors raid | Statistique vanilla des morts conservée, aucun bilan de raid |
| R08 | Mourir dans un raid en duo | Le survivant peut poursuivre son raid ; bilan du mort indépendant |
| R09 | Ouvrir l'onglet Progression en interface réduite | Aucun texte de l'historique ne déborde dans la colonne voisine |
| R10 | Extraire puis repartir immédiatement | Aucun compteur du raid précédent ne se retrouve dans le nouveau |

## Contrôles après test
- Consulter le journal Minecraft (`latest.log`) en cas de plantage ou de problème réseau.
- Noter le SHA du commit et le numéro de l'exécution GitHub Actions du JAR testé.
- Une compilation réussie ne garantit pas l'affichage des écrans ni la persistance après respawn.

## Points de vigilance
- Le bonus/malus de 10 % concerne actuellement l'XP **d'activité** (combats et fouilles), pas l'XP des contrats.
- Les rapports affichent l'XP d'activité du raid, pas le cumul de toute la progression.
- Tester spécifiquement la réception du bilan après respawn sur serveur dédié.
