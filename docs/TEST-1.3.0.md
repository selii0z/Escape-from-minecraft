# 1.3.0 — Immersion et survie (développement)

Base : 1.2.0 boss-events. Ne pas fusionner ni remplacer le JAR stable avant tests. 

## Premier lot — code ajouté
- Marchands : 24 offres par page, cases carrées, modèles ItemStack, prix, sélection locale et confirmation, quatre niveaux de fidélité, catégories, reventes séparées (x1/Tout), transactions/commandes conservées, 36 cases d'inventaire affichées en lecture seule. Les couleurs des cases indiquent la fidélité requise (pas une rareté indépendante). Les prix, lots, stocks et validations serveur restent inchangés. Le marché noir utilise également une grille de six offres.
- Protocole 12, version 1.3.0-dev. Tester GUI petite/grande, packs TaCZ client/serveur identiques, filtres vides, offres >24, toutes fidélités, dernière page, revente en lot, changement de marchand, actualisation, refus et journal. La sélection revient à la première case lors d'une réouverture. Pas d'achat en quantité arbitraire ni de glisser-déposer d'inventaire.
- Origine : booléen CustomData sur objets générés dans les caisses, largages, réserves, événements et cadavres d'IA. Tooltip vert « Trouvé en raid ». Les achats et anciens objets ne sont pas rétroactivement marqués. Conservation des autres composants ; les piles marquées/non marquées restent distinctes. Le marqueur signifie provenance et non extraction réussie ; le coffre sécurisé conserve le marqueur. Les contrats de reliques consomment uniquement des exemplaires marqués, avec le contrôle de quantité supplémentaire depuis le départ déjà existant. Tester fouille, empilement, réparation, vente, sauvegarde, coffre, duo et contrats.
- Boss : tirage par session 20 % aucun, 65 % classique, 10 % rare, 5 % convergence. Pool selon carte et nuit. Convergence et butins exclusifs 1.2.0 conservés ; aucun changement aux probabilités C6 ou duo premium. Vérifier toutes les catégories, groupe Goons, escortes, quêtes de boss et les deux cartes.
- Extractions : principale toujours disponible ; relais forêt 65 % activable, clé usine 65 %, payante usine 50 %, tirages indépendants déterministes par session. Annonce au départ et reconnexion, conditions initiales conservées, HUD n'indique que les sorties accessibles. Tester duo, deux sessions, refus sortie fermée, coût clé/crédits, sortie principale garantie.


## Inventaire tactique — deuxième lot ajouté
- Curios facultatif : trois slots EFM dédiés (lunettes, sac, gilet), optiques historiques toujours compatibles. En survie avec les trois slots présents, E ouvre le menu tactique ; Maj+E ou le bouton Classique ouvre l'inventaire vanilla pour le crafting et les autres mods.
- Les quatre armures restent à gauche du personnage, les trois accessoires à droite ; 36 cases natives conservées. Le sac et le gilet ajoutent leurs propres cases visibles et utilisables dans la même fenêtre. Changement du porteur : fermeture et réouverture serveur du menu pour adapter la forme (curseur traité par Minecraft). Pas de modification de la taille interne de l'inventaire vanilla.
- Six sacs 9/18/27/36/54/72 ; trois gilets 4/8/12. Les gilets acceptent les objets IAmmo TaCZ et les soins FieldMedicine EFM, pas les armes ni les conteneurs. Les sacs refusent autres sacs, coffres sécurisés, conteneurs et bundles. Le contenu est un composant Minecraft de l'objet physique, pas un stockage global du joueur. Gestes multi-slots bloqués dans ce menu, shift-clic de rangement disponible.
- Récupérateur : cinq sacs à 500/1200/3000/6000/12000 crédits, deux gilets à 700/1800. Élite exclusivement en raid : boss 8 % sac élite, 30 % sac militaire/expédition, 12 % gilet élite ; PMC 25 % sac tiers 1–3. Sacs générés vides. Prix/taux proposés à vérifier en jeu.
- Préparation et estimation du butin lisent les sacs/gilets. Récupération interrompue : quantité initiale par type/ID incluant ces équipements et leur contenu ; ne recrée pas d'objets après départ. Pas de garantie contre tous les conteneurs tiers. Vente et remise des contrats restent dans les cases natives pour l'instant ; les munitions du sac doivent être déplacées en inventaire si TaCZ ne les détecte pas. 
- Visuels d'objet provisoires vanilla, pas encore de modèle dorsal 3D. Assurance spécifique des nouveaux accessoires non intégrée ; mort et règles Curios à tester avant livraison. Protocole 13.
- Tests requis : deux joueurs, changement de sac vide/plein, curseur plein, shift-clic, sortie et mort, retour forcé, reconnexion, drop Curios et keepInventory, sauvegarde, absence de duplication, résolution/GUI scale, AllTheMons/JEI, touche E et Maj+E, ouverture sans Curios, gilets, lunettes N avec casque, achats, butin élite.

## Lots à réaliser (pas encore codés)
- Patrouilles itinérantes inter-secteurs ; les rondes et combats gardes/PMC existent déjà, à approfondir et vérifier isolation des sessions.
- Marché entre joueurs : séquestre des objets, crédits et récupération hors ligne, taxe proposée 5 %, validation serveur et tests de concurrence.
- Hydratation/énergie 0–100 uniquement en raid, restauration par consommables ; HUD et synchro.
- Inspection persistante par joueur, objets inconnus puis connus ; distinguer de la fouille progressive existante.
- Test en jeu et revue graphique des boutiques, aperçu au survol, achat par quantité, amélioration fidélité/rareté distincte si nécessaire.

Les nouveaux taux et catégories sont des choix d'équilibrage à tester. Pas de planque, trophées ou intégration Woods.
