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
