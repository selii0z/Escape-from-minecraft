# Campagne Tarkov — chantier séparé

Base : RC14-fix1, NeoForge 1.21.1. Les 12 missions existantes et les contrats renouvelables restent actifs tant que le remplacement n'est pas jouable et testé.

## Référence
- Tarkov.dev, miroir JSON v2 Tarkovstammtisch : https://api.tarkov-stammtisch.de/
- Wiki officiel : https://escapefromtarkov.fandom.com/wiki/Quests
- Collecteur : https://escapefromtarkov.fandom.com/wiki/Collector
- Mode de référence proposé : PvE, le plus proche des raids EFM contre l'IA.
- Figer un snapshot avec date, empreintes SHA-256 et mode. Ne pas actualiser les quêtes en cours automatiquement.
- Conserver les identifiants stables, les prérequis structurés, les objectifs, quantités et récompenses. Écrire des dialogues et illustrations originaux ; ne pas recopier les pages du wiki.
- Les données de référence brutes restent des entrées de travail, non des ressources distribuées dans le JAR.

## Marchands
Prapor (armurerie), Therapist (médecin), Skier (contrebande), Peacekeeper (militaire), Mechanic (atelier), Ragman (équipements), Jaeger (survie), Fence (marché et Collecteur), Lightkeeper (fin de jeu), Ref (défis si compatibles).
Préserver les identifiants et ordinals des trois magasins EFM existants lors de la migration. Ne pas convertir les crédits ou réputations sans règle de migration testée.

## Cartes
Woods -> Forêt Noire ; Customs -> Zone industrielle ; Factory -> Bunker ; Interchange -> Entrepôt ; Streets -> Zombiepolis.
Les objectifs des autres cartes, et les lieux précis manquants, restent bloqués par leur implantation réelle. Ne jamais substituer des coordonnées arbitraires ni valider gratuitement ces missions.

## Lots d'implémentation
1. Importer et auditer tâches, marchands, objets et cartes. Comparer les exigences Collector actuelles entre sources ; trancher avant de figer la campagne.
2. Registre de quêtes à identifiants stables, dépendances entre marchands, acceptation simultanée, validation serveur, sauvegarde par joueur, migration/wipe EFM.
3. Table de correspondance des objets : items existants EFM, TaCZ, nouveaux items fonctionnels, objets de quête séparés, provenance trouvée en raid ; aucun remplacement factice.
4. Gestionnaires d'objectifs : élimination ciblée, restrictions d'équipement, livraison, exploration, balisage, extraction, modification d'armes, niveau/compétence, quêtes exclusives ou à échec.
5. Menus marchands et quête, validation et récompenses atomiques, réputation, déblocages d'offres ; conserver l'économie EFM (crédits et 200 niveaux + Oméga).
6. Collector : tous les objets du snapshot retenu, conditions de déblocage explicites, conteneur Kappa 12 places. Ne pas l'activer avant que les prérequis et objets soient réalisables.
7. Vérification automatique : références manquantes, cycles, branches exclusives, objectifs non implémentés, duplication de récompenses, mort/extraction/reconnexion, solo/duo et migration.
8. Tests en jeu sur les six cartes, puis seulement fusion dans la branche principale de développement.

## Import de référence
`python3 tools/tarkov_campaign_snapshot.py --mode pve --out build/tarkov-reference`
Le script télécharge quatre fichiers JSON et produit un manifeste de contrôle. Le mode `--offline` relit un snapshot existant sans réseau.
Le contrôle initial exige au moins 200 quêtes et la présence de Collector ; il ne prouve pas la jouabilité des quêtes.
