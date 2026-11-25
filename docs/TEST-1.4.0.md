# 1.4.0 — La Planque (développement)
Base figée : 1.3.0 RC1 build 547. Branche indépendante, aucun changement au JAR privé avec skins.

## Lot 1 — planque
- Dimension dédiée plate, parcelles persistantes allouées par UUID, grille espacée de 256 blocs, bunker fermé de 31 × 31 blocs, sol/éclairage et huit emplacements décoratifs. Génération initiale avec marqueur, pas de coffre ou ressources gratuites.
- /efm planque : monde principal, survie, hors raid. /efm planque sortir : retour au point enregistré si sûr, sinon spawn principal. Limitation de déplacement à sa parcelle. Pas d'invitation ni décoration libre dans ce prototype.
- /efm planque installations : huit installations, niveaux 0–3. /efm planque ameliorer <installation> : coût en crédits, niveau militaire, 1/2/3 exemplaires d'un matériau marqué Trouvé en raid. Les objets dans sacs/gilets équipés sont inclus. Niveaux persistants dans le compte. Les fonctions propres aux installations ne sont PAS encore implémentées.
- Réserve, infirmerie, atelier, générateur, cuisine, collecteur d'eau, repos, tir. Coûts/niveaux provisoires à équilibrer. Casse, placement et dégâts de blocs par explosion bloqués dans la dimension. Aucun accès multi-joueur prévu.
- Vérifier génération/chargement de la dimension, départ/retour, deux parcelles distinctes, reconnexion, décès, retour bloqué, interactions modpack (téléportations, pistons, dégâts de blocs, machines), sauvegarde et coût d'amélioration. Protection de parcelle non garantie contre tous les mods tiers. Tester à part du monde de production.
- Compilation et tests runtime non confirmés.

## Lots suivants (non livrés)
1. Menu planque, réserve serveur, fabrication, énergie et améliorations visibles ; décoration dans zone autorisée après protection des parcelles.
2. Stand de tir privé, cibles fixes/mobiles, protections factices, munitions et usure isolées ; pas de génération d'objets exportables ni de progression farmable. Tester sortie et déconnexion.
3. Missions scénarisées à étapes, objectifs d'exploration/collecte/élimination/extraction, état serveur persistant, récompenses uniques et absence de conflit avec les contrats.
4. Huit compétences passives, 50 niveaux chacune ; XP plafonnée, activités réelles et bonus modestes. Aucun bonus de commerce, médecine, tir, etc. encore codé.
