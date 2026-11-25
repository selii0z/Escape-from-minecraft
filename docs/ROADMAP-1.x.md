# Escape from Minecraft — feuille de route 1.x

Décisions validées après la 1.0.3 : toutes les fonctionnalités proposées sont retenues sauf la collection de trophées et la planque personnelle (retirée à la demande du joueur le 25 novembre). Livraison groupée en 1.1.0 demandée par le joueur. Les lots ci-dessous sont des étapes de développement internes, pas des versions publiques séparées. La version 1.0.3 reste sur sa branche d'origine.

## Lot — Zone industrielle
- Refonte détaillée : usines, entrepôts, garages, voies ferrées, bureaux, souterrains.
- Clés rares et salles verrouillées avec butin dédié.
- Extractions conditionnelles (clé, objet ou crédits).
- Boss spécifique à la carte, équipement exclusif.

## Lot — Équipement et survie
- Assurance facultative de l'équipement, 100 crédits par équipement, 95 % de retour dans un espace de récupération après 30 minutes.
- Coffre sécurisé physique ouvrable, cinq emplacements ; objet et contenu conservés à la mort.
- Blessures localisées, fractures et saignements ; traitements adaptés.
- Usure, enrayement et entretien des armes, selon possibilités de l'API TaCZ.
- Réserves de munitions et rechargement des ennemis.

## Entretien du matériel (sans planque)
- Pas de base personnelle, ni stockage ou atelier propre au mod : Minecraft fournit déjà ces fonctionnalités.
- Entretien et réparation des armes chez l'Armurier ; modification via les outils existants de TaCZ.

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
- Sessions indépendantes par carte : premier lot Forêt Noire + Zone industrielle. Instances multiples d'une même carte à étudier séparément.
- Les packs externes sont uniquement des sources d'éléments compatibles, jamais des remplacements du mod, de son économie, de ses interfaces ou de TaCZ. Aucune reprise des scripts Bedrock ; vérifier droits et compatibilité des ressources et cartes.
- Ne pas confondre compilation réussie et validation en jeu.
- Pas de collection de trophées.

## Avancement initial
- Refonte industrielle : extension du plan, nouveaux bâtiments et secteurs, véhicules, wagons, réservoirs, bureaux, tunnel de service et boss Le Contremaître (code à compiler et tester).
- Nouveau secteur industriel à l'origine 32768 pour générer les nouveaux chunks sans effacer la Forêt Noire ni les anciens fichiers de région. Ne pas installer en cours de raid.
- Les autres lots restent à réaliser avant livraison complète.
