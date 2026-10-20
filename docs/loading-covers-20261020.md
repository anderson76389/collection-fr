# Chargements et couvertures — 20 octobre 2026

## Constats mesurés

Requêtes publiques ordinaires, depuis l'environnement de contrôle, sans session Android :

| Site | Catalogue ou accueil | Fiche échantillonnée | Chapitre échantillonné |
| --- | --- | --- | --- |
| Astral-Manga | HTTP 200, 5,32 s | HTTP 200, 6,05 s | HTTP 200, 5,32 s |
| Manhuarm | HTTP 200, 5,67 s | HTTP 200, 5,08 s | HTTP 200, 5,74 s |
| Lelmanga | HTTP 200, 4,50 s | HTTP 200, 4,62 s | Non remesuré |
| Dassou Scan | HTTP 200, 4,52 s | HTTP 200, 5,61 s | HTTP 200, 4,57 s |

Ces durées incluent le réseau de cet environnement ; elles ne constituent pas un benchmark avant/après sur le téléphone. La première requête reste dépendante du serveur.

Sur AnimeSama, les 48 cartes échantillonnées fournissent une URL originale `data-img`. La miniature de « 07 Ghost » est une WebP de 440 × 247 pixels (23 790 octets) ; son original JPEG fait 2 438 × 1 369 pixels (680 101 octets). Les deux répondent en HTTP 200. L'original est horizontal : sa qualité seule ne corrige pas le recadrage en portrait.

## Changements

- **AnimeSama 1.4.22** : préférer les originaux `data-img` aux miniatures, dans le parseur commun du catalogue et des récents. Adapter aussi les couvertures des fiches. Le client de l'extension place l'image entière dans un cadre portrait 2:3 sombre, sans découper le dessin, avec une largeur maximale de 1 000 pixels et un encodage JPEG qualité 95. Le marqueur d'URL renouvelle la clé du cache des couvertures. Les requêtes et les images de chapitres ne portent pas ce marqueur et passent normalement.
- **Astral-Manga 1.4.53** : appliquer aux pages de chapitres le mécanisme de résolution différée déjà utilisé pour les couvertures. La liste de pages ne déclenche plus une requête de signature synchrone par image ; chaque signature est demandée uniquement au chargement de l'image correspondante. Les URL signées et leur contrôle d'accès restent fournis par le site.
- **Manhuarm 1.6.6** : les images de `cdn.manhuarmtl.com`, observées dans le lecteur réel, ne partagent plus la file de deux requêtes par seconde des pages HTML et du lecteur OCR. Les limites des requêtes au site et aux traducteurs restent en place. Réutiliser brièvement les fiches identiques. La récupération du texte et sa composition ne changent pas ; les pages contenant les identifiants OCR à usage unique restent toujours chargées par le chemin réseau existant.
- **Lelmanga 1.6.3** : compléter le cache existant par la fusion des requêtes simultanées vers le même document ; fournir une copie indépendante aux parseurs de fiche, chapitres et œuvres liées.
- **Dassou Scan 1.6.5** : réutiliser brièvement les documents de fiche/lecteur et fusionner leurs requêtes simultanées ; respecter les indicateurs demandant les détails ou les chapitres, et éviter toute requête si aucun n'est demandé. Le filtrage premium reste intact.

Les caches de documents sont locaux à chaque source, limités à six documents et vingt secondes. Les erreurs ne sont pas conservées ; les appels vers des URL différentes peuvent charger en parallèle. Aucune bibliothèque partagée, langue, identité de source ou configuration de signature n'est modifiée.

## Vérifications

Les tests JVM couvrent les cartes AnimeSama avec original prioritaire, la géométrie sans découpe, la résolution différée des images, et, pour les trois caches de documents, la fusion de requêtes, l'isolation des parseurs, l'expiration, la capacité et la reprise après erreur. GitHub Actions exécute ces tests, vérifie les API locales, compile tous les APK conservés et lance le lint des versions de publication.

Le dessin Android et la lecture complète sur le téléphone ne sont pas validés par un test JVM. Les serveurs, l'OCR, la traduction et les premières images peuvent encore prendre plusieurs secondes.
