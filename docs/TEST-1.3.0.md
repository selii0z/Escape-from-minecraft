# 1.3.0 — Immersion et survie (développement)

Base : 1.2.0 boss-events. Ne pas fusionner ni remplacer le JAR stable avant tests. 

## Premier lot — code ajouté
- Marchands : 24 offres par page, cases carrées, modèles ItemStack, prix, sélection locale et confirmation, quatre niveaux de fidélité, catégories, reventes séparées (x1/Tout), transactions/commandes conservées, 36 cases d'inventaire affichées en lecture seule. Les couleurs des cases indiquent la fidélité requise (pas une rareté indépendante). Les prix, lots, stocks et validations serveur restent inchangés. Le marché noir utilise également une grille de six offres.
- Protocole 12, version 1.3.0-dev. Tester GUI petite/grande, packs TaCZ client/serveur identiques, filtres vides, offres >24, toutes fidélités, dernière page, revente en lot, changement de marchand, actualisation, refus et journal. La sélection revient à la première case lors d'une réouverture. Pas d'achat en quantité arbitraire ni de glisser-déposer d'inventaire.
- Origine : booléen CustomData sur objets générés dans les caisses, largages, réserves, événements et cadavres d'IA. Tooltip vert « Trouvé en raid ». Les achats et anciens objets ne sont pas rétroactivement marqués. Conservation des autres composants ; les piles marquées/non marquées restent distinctes. Le marqueur signifie provenance et non extraction réussie ; le coffre sécurisé conserve le marqueur. Les contrats de reliques consomment uniquement des exemplaires marqués, avec le contrôle de quantité supplémentaire depuis le départ déjà existant. Tester fouille, empilement, réparation, vente, sauvegarde, coffre, duo et contrats.
- Boss : tirage par session 55 % aucun, 30 % classique, 10 % rare, 5 % convergence. Pool selon carte et nuit. Convergence et butins exclusifs 1.2.0 conservés ; aucun changement aux probabilités C6 ou duo premium. Vérifier toutes les catégories, groupe Goons, escortes, quêtes de boss et les deux cartes.
- Extractions : principale toujours disponible ; relais forêt 65 % activable, clé usine 65 %, payante usine 50 %, tirages indépendants déterministes par session. Annonce au départ et reconnexion, conditions initiales conservées, HUD n'indique que les sorties accessibles. Tester duo, deux sessions, refus sortie fermée, coût clé/crédits, sortie principale garantie.

## Lots à réaliser (pas encore codés)
- Sacs 9/18/27/36/54/72 emplacements, derniers exclusifs raid ; stockage persistant et sécurisé côté serveur, pas d'imbrication récursive, intégration mort/assurance/coffre/revente. Prix proposés 500/1200/3000/6000/12000.
- Gilets tactiques distincts des armures, emplacements spécialisés ; contraintes de rangement côté serveur.
- Patrouilles itinérantes inter-secteurs ; les rondes et combats gardes/PMC existent déjà, à approfondir et vérifier isolation des sessions.
- Marché entre joueurs : séquestre des objets, crédits et récupération hors ligne, taxe proposée 5 %, validation serveur et tests de concurrence.
- Hydratation/énergie 0–100 uniquement en raid, restauration par consommables ; HUD et synchro.
- Inspection persistante par joueur, objets inconnus puis connus ; distinguer de la fouille progressive existante.
- Test en jeu et revue graphique des boutiques, aperçu au survol, achat par quantité, amélioration fidélité/rareté distincte si nécessaire.

Les nouveaux taux et catégories sont des choix d'équilibrage à tester. Pas de planque, trophées ou intégration Woods.
