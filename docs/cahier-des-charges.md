# Escape from Minecraft — TaCZ Operations
## Statut
Spécification V0.1, prototype non implémenté. Les chiffres du prototype sont des cibles à ajuster après essais.

## Environnement
Minecraft 1.21.1, NeoForge, Java 21 à vérifier, serveur Minestrator, intégration au modpack AllTheMons (ATMons), TaCZ comme dépendance à valider dans l'environnement exact. Dépôt indépendant; ne pas modifier les sources de TaCZ ni d'ATMons.

## Concept
Raids tactiques accessibles via un bloc physique **Tableau des Opérations**, sans raccourci clavier. Six cartes prévues : Forêt Noire, Zone industrielle, Village abandonné, Base militaire, Ville en quarantaine, Complexe souterrain. Une dimension dédiée, cartes séparées, générées automatiquement une seule fois puis conservées. Bâtiments tous différents, tous visitables. Terrain protégé contre pose, destruction et dégâts indirects; limites naturelles/physiques complétées par une barrière invisible.

## Raids
Solo ou duo avec invitation. Un raid actif maximum par carte. Apparition aléatoire parmi des entrées prédéfinies; coéquipiers ensemble. Choix jour/nuit avant le départ, météo aléatoire à chaque raid. Plusieurs extractions, certaines conditionnelles; sorties individuelles possibles en duo. Temps limité selon carte, 30 à 45 minutes envisagées. À expiration : retour au monde normal, perte uniquement du loot acquis pendant le raid. À la mort : pénalité variable selon difficulté; corps fouillable par le partenaire. Reconnexion autorisée pendant 5 minutes. Sauvegarde et récupération transactionnelles contre la duplication.

## Ennemis
Ennemis présents au départ et renforts occasionnels; un boss garanti par carte. Même quantité et difficulté en solo et duo. Intelligence artificielle : patrouille, perception, tirs, couverture, alerte, recherche et retraite. Faisabilité d'armes TaCZ sur PNJ à examiner, sans supposer d'API existante.

## Loot et progression
À chaque raid : emplacements **et** contenus des conteneurs aléatoires parmi des positions valides; ennemis renouvelés. Inventaire Minecraft standard, stockage du butin dans les coffres Minecraft ordinaires. Contrats facultatifs choisis avant raid, trois maximum par joueur, objectifs individuels en duo, récompenses immédiates conservées même si le joueur meurt. Récompenses : objets, crédits militaires, XP. Marchands dans le Tableau des Opérations; meilleures armes déverrouillées par rang puis achetables. Modalité de remise sécurisée des récompenses d'objets à concevoir sans imposer de coffre spécial permanent.

## Prototype Forêt Noire V0.1
Carte complète de 900×900 blocs dès la première génération (pas de prototype réduit 300×300), terrain forestier naturel, chemins, 5 bâtiments uniques visitables, 15–25 conteneurs aléatoires, 6–10 ennemis et renforts mesurés, boss Le Traqueur, 2 extractions dont une conditionnelle, raid de 30 min, solo/duo. Ces nombres ne sont pas des contraintes définitives.

## Critères d'acceptation
Compilation NeoForge; démarrage serveur dédié avec versions exactes ATMons et TaCZ; génération reproductible et persistante; accès aux bâtiments et extractions; protection contre les mécaniques d'autres mods; raid complet solo puis duo; loot et extraction corrects; mort, expiration, déconnexion et redémarrage sans duplication; réinitialisation de raid; mesures de performances.

## Points à confirmer
Versions exactes ATMons, NeoForge et TaCZ installées, compatibilité entre elles, API TaCZ pour IA armée, règles détaillées de pertes selon difficulté, mécanismes de sécurisation des récompenses de contrat, paramètres de génération et performances sur Minestrator.

## Décision validée — périmètre Forêt Noire 900×900
Générer directement la carte complète dans la dimension dédiée. Prévoir mur physique continu adapté au terrain, barrière invisible au-dessus, zone extérieure vide/inaccessible, sans accès aux autres cartes. Génération incrémentale avec reprise après interruption et sans blocage prolongé du thread serveur; conserver le terrain et les bâtiments entre les raids. Les téléportations ont été signalées fonctionnelles par le testeur, mais ne présument pas de la validation des autres systèmes.
