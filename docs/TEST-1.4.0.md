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

## Lots 2–4 — services et menu de gestion
- Réserve privée (18/36/54 cases), persistante avec les composants d'objets. Recettes bandages, Salewa, rations et eau ; validation de la place, des matériaux et du solde avant consommation.
- Générateur : 24/48/72 unités max, un bidon +12/24/36, une unité par fabrication ou réparation réussie. Pas de perte hors connexion. Atelier : réduction de 10/20/30 % sur réparation de l'arme tenue. Repos : protection de l'énergie durant les 120/180/240 premières secondes du prochain raid, consommée au départ.
- Menu de gestion provisoire de 54 cases (écran coffre vanilla) : huit installations, état des niveaux, coût et matériaux pour l'amélioration, actions de réserve/fabrication/carburant/réparation/repos, solde et énergie. Clic gauche sur les tuiles d'action uniquement ; aucun transfert, shift-clic ou dépôt possible dans ce menu. La réserve est un écran distinct qui accepte les vrais objets.
- Clic droit sur l'un des huit blocs d'installation ou /efm planque menu ; accès contrôlé par UUID, dimension, position et état du joueur. Les interactions vanilla avec les blocs décoratifs sont bloquées. Vérifier fermeture/réouverture du menu, clics rapides et paquets périmés, maintien des quantités, menu ouvert lors d'un déplacement forcé ou d'une déconnexion.
- Stand de tir non implémenté ; interface finale personnalisée, décoration, compétences et missions toujours à faire.
- Builds 555, 559, 566 échoués, logs indisponibles (BlobNotFound). Ajout de capture Gradle et artefact build-diagnostics même en cas d'échec de compilation ; si le job ne démarre pas cet artefact ne sera pas produit. Ne pas utiliser en production avant compilation et tests.
