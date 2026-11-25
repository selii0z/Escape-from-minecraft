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
