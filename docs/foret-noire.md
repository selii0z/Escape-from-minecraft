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
