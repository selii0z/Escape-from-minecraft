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

## Lot 5 — stand de tir, prototype
- Couloir privé fermé généré à côté du bunker, extension à mesure des améliorations du stand (niveau I : 10/25 blocs ; II : +50 ; III : +100). Entrée depuis le menu ou /efm planque tir ; même commande pour retour au bunker. Les limites de la parcelle autorisent uniquement son couloir débloqué. Les cibles sont des porte-armures invulnérables sans équipement, associés au propriétaire et à une distance, recréés au début de la séance.
- Compteurs temporaires tirs et impacts via GunFireEvent et EntityHurtByGunEvent.Pre ; annulation des impacts sur cibles, affichage distance nominale et tête/corps, bilan au retour. Plusieurs impacts peuvent correspondre à un seul tir. Aucun gain d'XP, butin ou usure EFM ; les munitions normales restent consommées. La détection des porte-armures invulnérables par TaCZ doit être vérifiée sur 1.2.0, aucune promesse de fonctionnement runtime. Les cibles mobiles, mannequins balistiques et munitions d'entraînement illimitées ne sont PAS implémentés.
- Vérifier accès, génération sans collision entre parcelles, extension du couloir, séparation des joueurs, redémarrage/déconnexion, cibles persistantes sans doublons, annulation dégâts et interactions, TaCZ tirs/impacts, absence de gains et d'export d'objets. Aucun changement au JAR de la RC1.
- Build 575 échoué, aucun artefact de diagnostic et logs indisponibles. Compilation de ce lot non confirmée. Priorité au rétablissement de la CI avant distribution.

## Lots 6–8 — campagne et compétences
- Campagne séquentielle de 12 opérations, indices 0–2 conservés. Déblocage militaire 1 à 150, missions de nuit, quantités de reliques, cibles génériques/PMC/élites/boss. Une tentative dans le raid correspondant : secteur atteint, quantité supplémentaire de reliques marquées trouvées en raid par rapport au départ, éliminations validées par la session, extraction confirmée. Consommation des reliques avant récompense, une seule récompense par opération. Les reliques restent celles de la table de butin ordinaire ; aucune garantie de présence du boss ou du butin dans un raid. Vérifier difficulté des derniers chapitres et interaction avec les contrats de récupération concurrents, qui peuvent utiliser les mêmes objets.
- Huit compétences 1–50, seuil cumulatif 6*n*(n-1), plafonds XP par raid (commerce par jour UTC). Bonus modestes sur consommation, soin Salewa, découverte progressive, détection accroupie et usure EFM. Force basée provisoirement sur 24 cases natives occupées ; pas de système de poids. Commerce : XP sur achat/vente effectués, réduction commerciale encore inactive.
- Menus provisoires de type coffre : campagne (12 opérations, état, prérequis, objectifs et récompenses au survol, acceptation de la suivante), compétences (niveau, XP et bonus). Accès depuis la planque ou /efm missions menu et /efm competences menu, hors raid. Aucun transfert d'objets de présentation. Les interfaces personnalisées finales restent à réaliser.
- Vérifier progression individuelle, sauvegarde après décès et reconnexion, ordre des opérations, nuit/jour, attribution unique et concurrence avec contrats standards. Vérifier XP cap, pas de gain hors raid sauf commerce, précision des bonus et protection des menus contre les clics/paquets invalides.
- Builds 584, 593 et 610 en échec ; le 610 ne fournit aucun artefact de diagnostic. Aucune compilation ni validation runtime confirmée pour la branche 1.4.0. Ne pas distribuer comme build jouable.

## Lot 9 — personnalisation contrôlée
- Quatre thèmes gratuits persistants : militaire, industriel, confort, moderne. Changement depuis le menu ou /efm planque decoration. Modification des sols et murs protégés, sans dépôt ni récupération de blocs. Trois indicateurs de niveau par installation, rafraîchis lors de l'arrivée et des améliorations.
- Construction libre toujours bloquée. La dimension utilise désormais le biome the_void pour les nouveaux chunks (pas de spawn naturel du biome) ; les mods tiers et anciens chunks doivent être vérifiés. Retour : position de secours ajustée via le système vanilla, point de retour conservé si changement de dimension non confirmé.
- Build 622 : échec initial, relance acceptée, nouvelle tentative également en échec, aucun artefact de diagnostic, aucune étape renvoyée par l'API. La cause n'est pas confirmée. Java 21 présent dans l'environnement de travail, mais pas les dépendances Gradle/NeoForge nécessaires à une compilation locale hors ligne. Ne pas confondre commits enregistrés et build jouable.
- Tests : changement des thèmes et conservation des installations/réserve, couleurs/niveaux, reconnexion, absence de bloc récupérable, arrivée et retour sécurisés. Compilation non confirmée.

## Lot 10 — remise commerciale
- Achats des quatre marchands classiques : prix unitaire réduit de 0 à 10 % selon compétence Commerce, arrondi au crédit supérieur (minimum 1). Même fonction de calcul pour client/serveur, montant total par lot. Remise synchronisée dans OperationsMenu.View ; une ancienne remise invalide le devis, et le menu est rafraîchi. Protocole réseau 18, même JAR client et serveur obligatoire.
- Pas de changement de prix de revente, marché joueurs, marché noir, réparations ou coûts de la planque. Les achats continuent de respecter fidélité, niveau, accès permanent, quantité, capacité et solde. XP Commerce plafonnée inchangée.
- Vérifier prix des cases et aperçu de quantité, paiement journalisé, transition de niveau après achat, arrondi, prix de revente inchangé, aucun avantage indu par transaction périmée. Build 630 échoué ; compilation du lot non confirmée.

## Lot 11 — entraînement avancé
- Mode cibles mobiles (niveau II : 50 blocs ; niveau III : 50 et 100 blocs), déplacement latéral périodique, option persistante /efm planque mobile. Cibles proches restent fixes ; recréation à l'entrée avec recherche dans l'enveloppe de mouvement.
- Bilan de séance : tirs, impacts, impacts tête, détail par distance ; records personnels persistants des impacts, des impacts tête et du nombre de séances. /efm planque records. Les impacts peuvent dépasser le nombre de tirs (projectiles multiples) ; aucune statistique de précision en pourcentage affichée.
- Tir TaCZ annulé dans la dimension planque hors du couloir personnel. Vérifier en jeu annulation des événements, collision et détection des porte-armures, mouvement visible côté client, tirs et impacts, reconnexion, limites et absence de gains de raid.
- Munitions réelles toujours consommées. Pas d'arme ou munition d'entraînement générée : munitions illimitées et mannequins balistiques reportés jusqu'à validation de la compilation et du comportement TaCZ. Build 641 échoué, aucun artefact ; compilation du lot non confirmée.
