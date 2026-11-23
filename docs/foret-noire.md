# Forêt Noire 0.4.0

## Installation

- Copier la sauvegarde AllTheMons et tester sur la copie avec Minecraft 1.21.1, Java 21, NeoForge 21.1.249 et TaCZ 1.2.0.
- Arrêter le jeu ou le serveur. Remplacer l'ancien JAR TaCZ Operations dans `mods/` par `taczoperations-0.4.0.jar`. En multijoueur, installer la même version sur le serveur et tous les clients.
- Ouvrir le Tableau des opérations puis choisir **Forêt Noire → Départ de jour** ou **Départ de nuit**. Pour un duo, les deux joueurs doivent être près du tableau. Si nécessaire, obtenir le bloc avec `/give @s taczoperations:operations_board`.

## Parcours de test

- Partir plusieurs fois pour vérifier les entrées aléatoires. Les deux membres d'un duo doivent apparaître ensemble et pouvoir rentrer séparément à leur point de départ.
- Suivre les routes autour de la rivière et inspecter les accès de la scierie, des entrepôts, des tentes, de la station, des cabanes, des tours et du bunker.
- Rejoindre le bloc d'or de la sortie principale à X 9015, Z 8374. Essayer la sortie secondaire à X 9012, Z 8874 avant puis après avoir activé à la main le relais en cuivre de la station à X 8904, Z 8633.
- Vérifier la casse, la pose, les explosions, les outils d'AllTheMons et les tentatives de franchissement du mur par le dessus et par le dessous. Vérifier aussi l'ouverture d'une porte en bois.
- Redémarrer pendant la préparation. Vérifier que les locaux déjà visités restent identiques et que les nouvelles zones se chargent progressivement.

## Génération et sauvegarde

La dimension conserve l'identifiant `taczoperations:black_forest`. La carte s'étend de X et Z 8192 à 9091 inclus. Le déplacement préserve les anciens chunks de reconnaissance sans les exposer aux raids. En dehors de la carte, les nouveaux chunks sont vides. Le générateur calcule chaque colonne et chaque bâtiment à partir du plan et clippe ses écritures au chunk courant. Minecraft sauvegarde les chunks normalement. En arrière-plan, le mod demande un chunk à la fois, libère son ticket après préparation et sauvegarde son curseur dans les données du monde. Une zone atteinte par un joueur peut se générer à la demande.

Les points de loot, les patrouilles, les renforts, les emplacements possibles du Traqueur et les positions de couverture sont définis dans le code. Ce build ne lance pas ces systèmes de raid. Le jour, la nuit et la météo aléatoire sont appliqués côté client pour l'ambiance de la sortie. Les règles serveur de lumière et d'apparition restent celles de la dimension.

La protection bloque les changements ordinaires du monde serveur par `Level#setBlock`, les événements de casse et les dégâts d'explosion aux blocs, tout en laissant les portes et trappes changer d'état. Un mod qui écrit directement dans les chunks peut contourner ce point d'entrée. La compatibilité avec les machines, objets et capacités du pack doit être vérifiée sur la copie. La carte autorise un groupe connecté à la fois. La réservation après déconnexion et la récupération complète de raid restent à développer.

## Compilation

```sh
./gradlew --no-daemon build
```

Un test hors jeu a généré 60 chunks et vérifié des points de contrôle du mur, du vide, de la rivière, des accès aux bâtiments, des points de départ et des extractions. La comparaison de blocs sur deux passages a vérifié la reproductibilité de ces chunks. La circulation complète, le relais en session, la sauvegarde après interruption et les performances du pack complet restent à tester en jeu.

## Combat et fouille dans la version 0.5.0

Le Tableau des opérations lance toujours le départ normal. En s'approchant des secteurs de Forêt Noire, une équipe peut rencontrer huit gardes sur des emplacements vérifiés. Les gardes essaient de s'équiper du pistolet TaCZ intégré `tacz:glock_17`. Une arbalète sert de repli si l'arme TaCZ est indisponible ou si l'adaptateur échoue. Après la mort d'un garde, fouiller le baril **Effets du garde** près du point de chute lorsque le terrain permet de le placer.

À chaque départ, le serveur tire vingt emplacements de caisses et renouvelle leur contenu. Les barils se matérialisent dans les chunks chargés à proximité de l'équipe. Faire un clic droit pour récupérer les vivres, les fournitures ou les munitions TaCZ. En duo, les conteneurs sont partagés. Les anciennes caisses ne sont plus accessibles et sont retirées lorsque le nouveau raid approche. La carte permanente reste en place.

Le filtre de la dimension refuse l'entrée des mobs ordinaires en laissant passer les gardes du raid courant. La synchronisation du ciel de Forêt Noire n'envoie plus l'heure globale en alternance avec l'heure de la sortie. Le ciel et la météo restent des effets visuels propres aux participants.

La compilation utilise l'API officielle de TaCZ 1.2.0. Un test hors jeu a généré 76 chunks et testé huit points de garde ainsi que vingt positions de caisse distinctes et constructibles pour chacun de 32 tirages. Le tir réel des gardes, l'interface de fouille, le ciel et l'absence d'apparitions parasites dans AllTheMons doivent être vérifiés en jeu. La refonte graphique du menu, la régénération administrateur et le boss ne font pas partie de cette version.

## Tableau des opérations dans la version 0.6.0

Le tableau s'ouvre sur le tableau de bord illustré. Il affiche six secteurs, la fiche du secteur sélectionné et les données réelles du joueur. La Forêt Noire est disponible au départ. Les cinq autres fiches sont consultables et le lancement y est désactivé.

Sélectionner **Jour** ou **Nuit**, puis **Solo** ou **Duo**, et cliquer sur **Lancer l’opération**. Le bouton **Duo** ouvre les invitations si aucun équipier n'est associé. L'onglet **Marchands** vend des vivres, des torches et des flèches en crédits militaires. **Progression** affiche l'XP obtenue avec les contrats et le rang correspondant. **Paramètres** enregistre la préférence d'affichage des coordonnées d'extraction.

Remplacer l'ancien JAR dans `mods/` sur le client et, en multijoueur, sur le serveur dédié. Les six sections sont accessibles depuis le tableau placé dans le monde.

## Fouiller les corps dans la version 0.7.0

Abattre un garde en Forêt Noire puis faire un clic droit sur son corps au sol. La première rangée contient son arme et son équipement. La deuxième contient sa réserve de munitions restante et ses effets personnels. L'arme TaCZ garde les données du chargeur, de la chambre et des accessoires au moment où le garde tombe.

Les gardes disposent d'une réserve limitée et y prélèvent les munitions au rechargement. Le corps conserve les objets restants lorsqu'on ferme la fouille ou que le chunk se sauvegarde. Un duo accède au même inventaire. La fouille permet de prendre des objets et les corps sont supprimés lorsqu'ils appartiennent à un ancien raid.

## Mise à jour 0.8.0

Remplacer le JAR dans `mods/` sur tous les clients et le serveur. Faire d'abord un essai sur une copie d'AllTheMons. L'interface du tableau conserve la disposition du tableau de bord et utilise un filtrage linéaire pour ses illustrations et ses polices. Les autres écrans Minecraft conservent leur propre rendu.

- **Contrats.** Activer jusqu'à trois contrats puis partir. Les éliminations sont attribuées au joueur qui donne le coup fatal. Le contrat au fusil d'assaut utilise le signal de mise à mort de TaCZ et accepte l'AK-47 et le M4A1 du pack par défaut. Les objectifs d'extraction sont évalués à une vraie sortie. Les trésors demandés doivent s'ajouter à ce que le joueur possédait au départ et sont remis automatiquement à la sortie. L'ancien solde et l'XP des contrats terminés restent comptabilisés.
- **Marchands.** L'Armurier vend armes, munitions et accessoires. Le Médecin propose les soins. Le Récupérateur rachète les trouvailles. Le Courtier rachète les renseignements et consomme un trésor pour ouvrir l'accès permanent à une offre. Les dix objets sont disponibles uniquement comme objets du mod. Ils peuvent être vendus, remis en contrat ou échangés pour un accès. Les catalogues sont permanents.
- **Forêt Noire.** La carte conserve sa taille de 900 × 900. Le nouveau terrain couvre X/Z 12288 à 13187. Cette nouvelle zone conserve les anciens chunks hors du parcours jouable et lance sa propre préparation progressive. Tester le hameau, les porches, les sentiers et les nouvelles installations. Jusqu'à quarante conteneurs sont répartis parmi les emplacements intérieurs et extérieurs. Jusqu'à 28 gardes et un Traqueur apparaissent à proximité de l'équipe. Les profils utilisent des armes, armures, distances et cadences différentes. Les corps gardent leur inventaire fouillable.
- **Zone industrielle.** Sélectionner la deuxième carte pour essayer la première tranche jouable de 650 × 650. Elle couvre X/Z 16384 à 17033 dans la même dimension. Elle contient une usine à passerelle, des ateliers, un dépôt ferroviaire et une aire de stockage. La sortie est à X 16970, Y 70, Z 16478. Les premières positions de loot et les gardes partagent le système de raid de Forêt Noire. Les traversées souterraines et le détail de chaque bâtiment restent à compléter.

Pour un essai rapide, vendre ou remettre une plaque au Récupérateur ou au Courtier, acheter des munitions à l'Armurier, puis activer **Première extraction** et terminer un raid. Vérifier ensuite la persistance du solde et des accès après reconnexion. Les parcours de combat, la nuit, les achats simultanés et les reprises après crash doivent être testés sur le serveur AllTheMons complet. La migration vers la nouvelle zone ne supprime aucun chunk. La commande de régénération administrateur et les tests de récupération après arrêt brutal restent à développer.

Vérifications 0.8.0. Le build Gradle a réussi. Le test de génération a couvert 167 chunks, 32 tirages de 40 conteneurs forestiers et 16 tirages industriels. Dans un client local NeoForge 21.1.249 avec TaCZ 1.2.0, une vente, un déblocage, un achat de munitions et un contrat d'extraction après départ industriel ont fonctionné. Le solde et le déblocage ont été retrouvés après reconnexion. En Forêt Noire, un tireur d'élite portait bien une AWP TaCZ. Un avertissement de retard serveur a été observé lors du premier chargement de la forêt. Les mesures sur AllTheMons restent nécessaires.
