# Ressources graphiques EFT — import local optionnel

Source fournie par le joueur : EFTPack-1.21.1.mrpack (EFT Client de ShiftingKill, licence déclarée CC0-1.0 et fichier LICENSE inclus). Les droits de tiers ne sont pas vérifiés indépendamment. Ne pas publier ces ressources avant cette vérification.

Le script `python3 tools/import_eft_medical.py EFTPack-1.21.1.mrpack EFM-1.1.0-medical-overlay.zip` produit un ZIP de 12 entrées : 5 modèles JSON adaptés au namespace taczoperations, 5 PNG, une licence et une notice. Correspondances : Salewa, army_bandage, splint, calok-b (texture alok), IFAK (medkit). Aucun code Fabric importé, aucune dépendance Fabric. Les JSON provisoires du dépôt restent utilisables sans le pack.

Pour une distribution locale de test, fusionner les entrées de l'overlay dans le JAR compilé (remplacer les cinq modèles existants, ne pas créer d'entrées ZIP en double). Vérifier le CRC et le chargement en jeu. Le build CI standard n'inclut pas cet overlay. Ne pas confondre préparation des assets avec validation du rendu.
