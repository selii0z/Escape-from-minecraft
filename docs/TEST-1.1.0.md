# Soirée test 1.1.0 — préparation

La livraison complète est en développement. Ne pas remplacer le JAR stable tant que la compilation et les tests de base ne sont pas validés.

## Tests prioritaires
- Sauvegarder le monde, les données joueurs et la configuration du serveur.
- Installer la même version côté serveur et sur tous les clients.
- Vérifier départ, extraction, décès, déconnexion/reconnexion, redémarrage.
- Coffre sécurisé : clic droit, transfert normal et rapide, changement de slot, mort avec coffre ouvert, keepInventory, inventaire plein, absence de duplication.
- Assurance : 100 crédits par objet, restitution différée de 30 minutes, 95 % par objet ; retrait individuel dans l'espace dédié.
- Bilan : animation kaki/vert pomme, XP exacte, passage de niveau, victoire et défaite.
- Zone industrielle : nouveaux chunks, entrées/sorties, tunnel, boss et coffres.
- Blessures : saignements, fractures et soins ; vérifier effets et retour hors raid.

Les fonctionnalités restantes du roadmap ne sont pas terminées.

- Bandage : stoppe un saignement léger, réduit un saignement important d'un cran ; ne rend aucun PV. Salewa : rend jusqu'à 8 PV (4 cœurs), sans nourriture et sans traiter le saignement. Attelle : fracture ; kit hémostatique : hémorragie ; kit de premiers secours : bras blessé. Vérifier que les objets ne sont consommés que si le soin est utile.

- Statistiques cumulées : vérifier victoire/défaite, taux de survie, éliminations, K/D, record, coffres, contrats, durée, XP. Migrer seulement les dix rapports historiques existants, conserver les totaux au-delà de dix raids et après reconnexion.

- Défis périodiques : 3 quotidiens, 3 hebdomadaires, automatique et indépendant des 3 contrats actifs. Vérifier gains sur bilan, extraction uniquement pour défi extraction, limites et renouvellement minuit UTC / lundi, sauvegarde, crédits/XP/réputation attribués une seule fois.

- Marché noir : six offres quotidiennes, stock par joueur, conservation des prérequis des marchands, prix réduits, refus inventaire plein, renouvellement UTC, aucun équipement de classe 5 en vente. Pas de planque personnelle.

- Réserves industrielles : trois salles générées dans les nouveaux chunks, porte blindée à deux blocs, clé industrielle consommée (8 % caisses ordinaires, 3 % gardes, garantie Contremaître), accès partagé par équipe, caisse spéciale créée uniquement après ouverture. Vérifier reset de porte et caisse à chaque raid, essais sans clé, en duo, après reconnexion, et absence de contournement. Les chunks industriels déjà générés avec une version dev antérieure ne recevront pas les bâtiments automatiquement.

- Extractions industrielles : principale gratuite (or), alternative clé industrielle (fer, 1 clé), alternative payante (émeraude, 600 crédits). Vérifier sortie sans prérequis refusée, paiement uniquement après téléportation, bilan unique, duo (coût individuel), et coordonnées HUD selon sorties accessibles. Nouveaux chunks nécessaires pour les marqueurs.

- IA : mémoire de la dernière position (5 s), alerte entre membres de même escouade, déplacement latéral des PMC/élites, recherche de couverture après blessure ou épuisement des munitions. Vérifier obstacles, nuit, absence de tirs à travers les murs, cadence/dégâts inchangés, et impact TPS avec plusieurs groupes. Renforts dynamiques non inclus dans ce lot.

- Renforts dynamiques : après 10 éliminations (20 pour deuxième vague duo), délai d'alerte 30 secondes, 2 PMC maximum solo / 2 vagues de 3 maximum duo. Attendre au moins 60 s après départ et 90 s entre vagues terminées. Spawn seulement dans chunks chargés, à distance des joueurs et hors vue, un garde par seconde, abandon après 45 secondes si aucun emplacement valide. Aucun déclenchement si joueurs hors ligne, état réinitialisé au prochain raid. Vérifier les sorties, performances et absence de vagues infinies.

- Entretien des armes TaCZ : état par objet dans CustomData (ancien objet sans champ = 100 %), perte de 0,02 point de pourcentage par déclenchement réel de tir en raid, aucun enrayement au-dessus de 70 %, risque progressif jusqu'à 6 %, rechargement pour débloquer, réparation à 0 %. Armes récupérées sur gardes 55–95 %. Armurier : 8 crédits par point de condition manquant, minimum 50 ; main principale, préserve chargeur, accessoires et autres données. Vérifier tous modes (rafale, auto, pompe), enrayement et munitions, tooltip et synchro, assurance, coffre, revente, compatibilité packs, et absence d'usure hors raid.

- Vérifier les cinq soins en butin médical : bandages, Salewa, attelles, kits hémostatiques, kits de premiers secours. Les clés industrielles gardent leur emplacement indépendant.
- Ressources EFT optionnelles : tester les cinq modèles 3D en inventaire, en main et au sol, sans Fabric, puis contrôler l'absence de texture manquante. Voir docs/EFT-ASSETS.md.

- 24 objets de revente distincts des reliques de quête : contrôler poids de tirage, rareté, vente unitaire au marchand désigné, conservation des clés/soins, butin rare des réserves et prix. Modèles vanilla provisoires dans CI ; modèles EFT locaux facultatifs.

- Mobilier de fouille : six blocs (sac médical, boîte à outils, caisse militaire, coffre-fort, ordinateur, armoire à dossiers), 27 emplacements, retrait seulement, contenu commun au raid, ancien mobilier supprimé à proximité avant le nouveau remplissage. Vérifier clic droit, shift-click, fermeture à la fin du raid, deux joueurs, rechargement de chunk, permissions et textures. Largage reste un tonneau standard. Modèles détaillés en overlay local distinct.

- Sessions parallèles par carte : Forêt Noire et Zone industrielle peuvent chacune accueillir une équipe indépendamment. Même carte encore limitée à une équipe. Vérifier départ simultané, fin d'un raid sans suppression des ennemis/corps/objets de l'autre, largages et renforts propres à chaque équipe, déconnexion, décès, extraction, réouverture des coffres, maintien du verrou pendant 10 min. Installer uniquement hors raid.

- Nettoyage renforcé : vérifier suppression des conteneurs du raid terminé dans les chunks chargés, blocage puis suppression des conteneurs périmés dans les chunks revisités, absence de suppression des meubles décoratifs non marqués, réinitialisation des portes chargées, fermeture du menu avant extraction, et indépendance des deux cartes. Largage : au plus 8 tentatives de sélection à 5 secondes d'intervalle. Les chunks non chargés ne sont pas forcés pour le nettoyage.

- Audio : sons vanilla discrets espacés de 25–70 secondes (premier son après 20–40 s), sélection par carte, soin uniquement appliqué avec succès, fouille première fois, alerte de renfort et extraction. Sons privés, vérifier volume et absence de sons après extraction. Aucun son tiers importé.

- PMC : spécialités stables (50 % assaut, 20 % reconnaissance, 5 % précision, 15 % soutien, 10 % médic). 70 % des tirages privilégient une arme TaCZ existante de la spécialité, 30 % conservent le catalogue complet; repli catalogue si arme absente. Réserves propres à la spécialité, consommées comme avant. Butin bonus probabiliste (assaut 50 %, reconnaissance 55 %, soutien 65 %, médic 75 %, précision 80 %), tirage parmi soins, reliques, accessoires et objets de revente. Médic favorise les soins sans garantie; soutien favorise les outils. Bonus supplémentaire si emplacement libre : précision 30 %, autres 15 %. Boss : soin aléatoire à 80 %, hors récompenses de progression déjà définies. Bandage occasionnel chez les gardes. Vérifier titre de fouille, armes, munitions restantes et butin fixe après déchargement de chunk. Pas de changement des PV, dégâts ou cadence d'IA.

- Fouille progressive : cases vides révélées immédiatement jusqu'au prochain objet, seuls les emplacements occupés prennent 10 ticks (0,5 s à 20 TPS); 6 objets = 3 s, conteneur vide immédiat, 27 objets = 13,5 s, meubles, cadavres et largages. Seuls les objets révélés sont envoyés au client et peuvent être retirés. Curseur commun aux équipiers, progression persistée dans le NBT de la source, pas de relance du butin; progression uniquement lorsqu'un menu valide est ouvert; après fermeture de tous les menus, le prochain objet recommence son délai à la reprise, sans masquer les objets déjà découverts. Vérifier ouverture simultanée, fermeture/réouverture, reprise après déchargement du chunk, clic rapide, shift-click, touches de barre rapide et interdiction de déposer. Interface de coffre standard avec message de progression; pas encore d'interface visuelle dédiée.

- Fouille : tester conteneur vide, un objet en dernière case, trous entre objets, conteneur plein, deux joueurs (aucune accélération), fermeture pendant recherche et réouverture. Progression affichée par emplacement, durée dépend uniquement du nombre d'objets.

- Interface dédiée de fouille : menu enregistré raid_search, synchronisation du curseur et de la progression courante par DataSlot. Cases inconnues marquées ?, case en cours encadrée avec petite barre, cases vides déjà découvertes sans symbole. Thème anthracite/olive. Vérifier inventaire joueur, clics, shift-click, hotbar, taille GUI, ouverture serveur dédié, synchronisation duo et absence d'affichage des objets non révélés. Client et serveur doivent être mis à jour ensemble.

- Soins progressifs : maintenir clic droit avec le soin en main principale, bandage 2 s, hémostatique 3 s, attelle et Salewa 4 s, IFAK 5 s (20 TPS). Pose vanilla de garde, progression en action bar. Soin et consommation uniquement à la fin, avec contrôle serveur de la blessure et du raid. Relâcher, changer de case, quitter le raid ou perdre le besoin de soin annule sans consommation. Vérifier saignement pendant soin, mort, réouverture inventaire, survie solo/duo, absence de double consommation et synchronisation avec TaCZ. Pas d'animation 3D dédiée importée.

- HUD blessures : panneau en haut à gauche uniquement en raid, état stable / saignement / hémorragie / fracture / bras blessé et traitement conseillé, barre du soin en cours. Paquets privés à chaque changement de blessure/soin et une fois par seconde en raid ; expiration 3,5 s, effacement hors raid/mort/changement de monde, F1 respecté. Progression du soin affichée côté client à partir de l'usage vanilla, application toujours validée serveur. Vérifier coexistence avec minimap et TaCZ, absence de données d'un équipier. Protocole réseau 5 : client et serveur doivent utiliser la même version.
