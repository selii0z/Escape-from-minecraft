# Première mission Codex
Tu développes **Escape from Minecraft / TaCZ Operations** sur Minecraft 1.21.1 NeoForge, destiné à fonctionner dans **AllTheMons** avec **TaCZ** sur serveur dédié.

Lis d'abord `docs/cahier-des-charges.md` et `docs/architecture.md`. Ne modifie pas les dépôts PokéContracts, Pokeworld, ni les sources des mods tiers.

## Travail initial
1. Identifier les versions exactes compatibles du modpack, de NeoForge, Java et TaCZ; ne pas prétendre avoir vérifié la cohabitation sans démarrage réel.
2. Examiner les dépendances et la possibilité de faire tirer des PNJ avec TaCZ. Documenter les API réellement trouvées, les limites et les solutions.
3. Préparer le squelette NeoForge, le build Gradle, les métadonnées, le `.gitignore` et un workflow CI approprié.
4. Compiler; fournir la commande exécutée, les résultats et les limites de test.
5. Présenter une feuille de route concrète vers le prototype Forêt Noire. Ne pas annoncer comme terminées les fonctions encore non testées.

## Contraintes
Code modulaire, logique autoritaire serveur, inventaires transactionnels, carte générée une seule fois, protection contre interactions d'ATMons, bâtiments tous visitables. Ne pas inventer de méthode TaCZ. Ne pas produire de builds cosmétiques successifs; livrer des étapes testables.
