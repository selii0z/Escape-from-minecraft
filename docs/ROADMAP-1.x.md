# Escape from Minecraft — feuille de route 1.x

Décisions validées après la 1.0.3 : toutes les fonctionnalités proposées sont retenues sauf la collection de trophées. Livraison groupée en 1.1.0 demandée par le joueur. Les lots ci-dessous sont des étapes de développement internes, pas des versions publiques séparées. La version 1.0.3 reste sur sa branche d'origine.

## Lot — Zone industrielle
- Refonte détaillée : usines, entrepôts, garages, voies ferrées, bureaux, souterrains.
- Clés rares et salles verrouillées avec butin dédié.
- Extractions conditionnelles (clé, objet ou crédits).
- Boss spécifique à la carte, équipement exclusif.

## Lot — Équipement et survie
- Assurance facultative de l'équipement, règles de retour et pertes à définir.
- Coffre sécurisé de capacité limitée ; contenu conservé à la mort.
- Blessures localisées, fractures et saignements ; traitements adaptés.
- Usure, enrayement et entretien des armes, selon possibilités de l'API TaCZ.
- Réserves de munitions et rechargement des ennemis.

## Lot — Planque
- Base personnelle améliorable avec matériaux de raid.
- Stockage personnel et atelier d'armement.
- Modification et réparation des armes, installations et coûts à équilibrer.

## Lot — IA et raids dynamiques
- Couvert, contournement, coopération et déplacements en escouade.
- Renforts dynamiques.
- Différencier Scavs et PMC ; conserver une difficulté cohérente en solo et duo.

## Lot — Économie et long terme
- Marché noir et offres rares renouvelées quotidiennement.
- Contrats quotidiens et hebdomadaires.
- Statistiques détaillées : survie, éliminations, temps en raid, valeur du butin.

## Lot — Cartes supplémentaires
- Base militaire.
- Zone de quarantaine.
- Complexe souterrain.
- Village abandonné.
- Pour chacune : boss propre, clés, extractions, butin et identité visuelle distincts.

## Garde-fous transversaux
- Maintenir les règles de progression approuvées en 1.0.3 : niveaux 1–200, Oméga, fidélité marchands indépendante avec seuils de niveau 20/50/100.
- Préserver les données des joueurs et la compatibilité Curios facultative.
- Tester pertes et récupération, déconnexion, mort, extraction, réinitialisation des cartes, raids solo/duo ; éviter duplications et exploits.
- Étudier les sessions indépendantes simultanées.
- Ne pas confondre compilation réussie et validation en jeu.
- Pas de collection de trophées.

## Avancement initial
- Refonte industrielle : extension du plan, nouveaux bâtiments et secteurs, véhicules, wagons, réservoirs, bureaux, tunnel de service et boss Le Contremaître (code à compiler et tester).
- Nouveau secteur industriel à l'origine 32768 pour générer les nouveaux chunks sans effacer la Forêt Noire ni les anciens fichiers de région. Ne pas installer en cours de raid.
- Les autres lots restent à réaliser avant livraison complète.
