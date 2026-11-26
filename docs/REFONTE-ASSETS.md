# Refonte graphique EFM — inventaire des assets

Inventaire des 60 identifiants enregistrés dans OperationsMod, TradeLoot, RaidRelic et FieldPack sur la branche 1.4.0. Liste d'audit, pas une liste de textures manquantes confirmées.

## Renseignements — priorité haute

- [ ] `taczoperations:engraved_tag` — Plaque métallique et chaîne
- [ ] `taczoperations:tactical_radio` — Radio noire avec antenne
- [ ] `taczoperations:classified_documents` — Dossier kraft tamponné
- [ ] `taczoperations:military_watch` — Montre kaki
- [ ] `taczoperations:secure_circuit` — Carte électronique verte
- [ ] `taczoperations:encrypted_drive` — Disque renforcé
- [ ] `taczoperations:intel_case` — Mallette blindée noire
- [ ] `taczoperations:prototype_optic` — Optique militaire
- [ ] `taczoperations:command_chip` — Microprocesseur doré
- [ ] `taczoperations:omega_file` — Dossier noir marqué Ω

## Butin — priorité haute

- [ ] `taczoperations:screwdriver` — Tournevis
- [ ] `taczoperations:wrench` — Clé à molette
- [ ] `taczoperations:flat_pliers` — Pince plate
- [ ] `taczoperations:measuring_tape` — Mètre ruban jaune
- [ ] `taczoperations:hand_drill` — Perceuse manuelle
- [ ] `taczoperations:tool_set` — Trousse à outils
- [ ] `taczoperations:fuel_can` — Jerrican kaki
- [ ] `taczoperations:air_filter` — Filtre plissé
- [ ] `taczoperations:water_filter` — Cartouche bleue
- [ ] `taczoperations:aramid_fabric` — Tissu aramide
- [ ] `taczoperations:ripstop_fabric` — Tissu camouflage
- [ ] `taczoperations:paracord` — Corde enroulée
- [ ] `taczoperations:saline` — Poche de sérum
- [ ] `taczoperations:medical_tools` — Plateau médical
- [ ] `taczoperations:surgical_kit` — Pochette chirurgicale
- [ ] `taczoperations:ophthalmoscope` — Instrument optique
- [ ] `taczoperations:blood_set` — Poche et tubulure
- [ ] `taczoperations:defibrillator` — Appareil portable
- [ ] `taczoperations:ledx` — Scanner médical
- [ ] `taczoperations:tea_box` — Boîte de thé
- [ ] `taczoperations:bear_plush` — Ourson marron
- [ ] `taczoperations:master_tools` — Mallette professionnelle
- [ ] `taczoperations:metal_scissors` — Ciseaux métalliques
- [ ] `taczoperations:filter_cartridge` — Cartouche FP-100

## Soins et consommation — priorité haute

- [ ] `taczoperations:bandage` — Rouleau de gaze
- [ ] `taczoperations:hemostat` — Pansement hémostatique
- [ ] `taczoperations:splint` — Attelle
- [ ] `taczoperations:medkit` — Trousse de secours
- [ ] `taczoperations:salewa` — Sac médical rouge
- [ ] `taczoperations:field_ration` — Sachet de ration militaire

## Équipement — priorité haute

- [ ] `taczoperations:night_vision_goggles` — Lunettes nocturnes
- [ ] `taczoperations:thermal_goggles` — Lunettes thermiques
- [ ] `taczoperations:backpack_small` — Petit sac
- [ ] `taczoperations:backpack_patrol` — Sac patrouille
- [ ] `taczoperations:backpack_tactical` — Sac tactique
- [ ] `taczoperations:backpack_military` — Sac militaire
- [ ] `taczoperations:backpack_expedition` — Sac expédition
- [ ] `taczoperations:backpack_elite` — Sac élite
- [ ] `taczoperations:rig_patrol` — Gilet patrouille
- [ ] `taczoperations:rig_tactical` — Gilet tactique
- [ ] `taczoperations:rig_elite` — Gilet élite
- [ ] `taczoperations:secure_case` — Coffre sécurisé
- [ ] `taczoperations:industrial_key` — Clé industrielle

## Blocs — priorité haute, modèles 3D

- [ ] `taczoperations:operations_board` — Tableau opérationnel
- [ ] `taczoperations:raid_medbag` — Sac médical
- [ ] `taczoperations:raid_toolbox` — Caisse à outils
- [ ] `taczoperations:raid_weaponbox` — Caisse militaire d'armes
- [ ] `taczoperations:raid_safe` — Coffre-fort
- [ ] `taczoperations:raid_pc` — Ordinateur
- [ ] `taczoperations:raid_cabinet` — Armoire métallique

## Intégration

- Vérifier chaque modèle existant et conserver les assets déjà satisfaisants. PNG RGBA 64×64 pour les icônes originales, lisibles dans une case d'inventaire ; aucun fond opaque, aucun modèle vanilla de substitution. Perspective et éclairage cohérents, variantes reconnaissables par leur forme.
- Minecraft 1.21.1 : assets/taczoperations/models/item/<id>.json et textures/item/<id>.png ; item/generated pour sprites, parent modèle de bloc pour les objets 3D. Vérifier la génération Gradle des textures FieldPack pour éviter les collisions et l'écrasement des nouvelles textures.
- Pour les six meubles de raid, modèles de blocs détaillés, textures et blockstates compatibles avec les identifiants existants ; conserver les block entities, le loot, la persistance et les interactions. Ajuster les formes de collision avec prudence.
- Examiner les JAR de référence fournis par le propriétaire avant réutilisation ; n'intégrer leurs assets que si la licence ou l'autorisation le permet, sinon créer des modèles originaux. Ne pas copier des textures TaCZ externes sans droit.
- L'eau de la recette actuelle est une potion d'eau vanilla, pas un item EFM enregistré. Une bouteille EFM personnalisée est une évolution distincte : ne pas modifier la texture de toutes les potions vanilla.
- Contrôler en jeu inventaire, marchands, fouille, équipement porté, langue française, absence de texture manquante. Ne pas modifier les IDs, prix, raretés ou probabilités.

## Autres demandes de test

- Masquer entièrement les articles de fidélité supérieure au niveau du joueur et recomposer la grille.
- Ajouter une étape d'assurance avant le lancement du raid avec sélection d'équipements et coût, sans facturation double.
- Fonds de bilan par carte et résultat, natifs 1920×1080 ou mieux, préserver le ratio et éviter le grossissement pixelisé.

Statut : spécifications uniquement. La compilation 1.4.0 reste à valider.
