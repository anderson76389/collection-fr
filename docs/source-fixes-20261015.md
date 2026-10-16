# OrtegaScans, MangaBall et Lelmanga — 15 octobre 2026

## OrtegaScans 1.6.4

Le lecteur actuel utilise `/api/covers/moby-dick.webp?v=ce018bab` alors que le champ du manga contient `storage/covers/moby-dick_ce018bab.webp`. Le précédent remplacement de préfixe conservait le hash dans le nom de fichier et produisait une adresse différente de celle du site. Les résultats du catalogue et les détails utilisent maintenant la même conversion ; les adresses absolues externes et les adresses sans hash sont conservées.

Vérification sur le HTML actuel rendu du site et ses données Next.js. L'accès HTTP direct depuis cet environnement renvoie une page « Site Unavailable » ; le HTML public a pu être récupéré via Jina pour le diagnostic uniquement. Aucune dépendance à Jina n'est ajoutée à l'extension. Le chargement réel des couvertures dans Mihon reste à confirmer sur téléphone.

## MangaBall 1.6.6

Le module passe de `src/all/mangaball` à `src/fr/mangaball`, avec le même paquet Android `all.mangaball`, la même signature de publication et le même identifiant français `4804845656972446863`. La source `all` est retirée. Les listes populaires, récentes et de recherche envoient `translated_languages=fr`, `adult_mode=no_18` et les exclusions de tags adultes. Les réponses sont également filtrées avant affichage. Les détails refusent les œuvres sans français ou marquées adultes. Le réglage permettant de réactiver le contenu adulte et les filtres adultes sont retirés.

Vérifications API : trois listes de 24 résultats français sans indicateur 18+, détail de One Piece en français, 264 chapitres français parmi 9847 traductions, lecteur de 16 pages.

## Lelmanga 1.6.2

Les requêtes annexes de compteur de vues sont désactivées. Les documents de détails, recommandations et lecteurs sont réutilisés pendant 20 secondes, dans un cache limité à six documents, pour éviter les téléchargements répétés rapprochés. Les mises à jour respectent les demandes séparées de détails et de chapitres. Les couvertures hébergées sur le site sont récupérées directement au lieu de transiter par les domaines `i0` à `i3.wp.com` ; les images externes restent inchangées. Les demandes d'images conservent un Referer du site.

Vérification : catalogue et œuvre accessibles, 191 liens de chapitres sur One Piece et 16 pages sur le chapitre échantillon. Couverture directe HTTP 200 image/jpeg, 27197 octets contre 42840 via le proxy redimensionné ; environ 3,1 s contre 5,0 s lors de la mesure. Ce sont des mesures ponctuelles depuis cet environnement, pas une garantie de délai sur téléphone. Le temps de réponse du serveur reste une limite pour les premiers chargements.

Les autres extensions et toutes les bibliothèques partagées sont conservées. La compilation et le lint Android sont vérifiés par GitHub Actions ; aucun émulateur Mihon n'est disponible dans cet environnement.
