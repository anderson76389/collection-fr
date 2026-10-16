# Comix et Epsilon — 16 octobre 2026

## Comix 1.6.53

Les chargements HTML des catalogues, fiches et lecteurs réessaient au plus trois fois les erreurs 502, 503, 504, 522 et 523, avec une attente de 2,5 puis 5 secondes. Les pages de liste de chapitres utilisent le même budget borné via la méthode API du site. Les refus 403 et 429 ne déclenchent pas ces relances. Une liste partielle garde son indicateur incomplet et les chapitres déjà présents restent conservés. Les caches, identités, filtres et délais entre requêtes existants sont conservés.

Des tests du script réel dans Node, avec une API simulée, vérifient une récupération après deux 502 et après deux 504, un échec borné à trois 502, et une seule tentative pour 403/429. Ces simulations valident la logique de reprise, pas la disponibilité du serveur Comix. Les sondes publiques et le navigateur de test restent bloqués par Cloudflare : aucune lecture complète Comix ne peut être certifiée ici. Une erreur 502 ne prouve pas à elle seule un blocage anti-bot.

## Soft Epsilon Scan 1.6.8

PAM n’est utilisé que par cette extension dans ce dépôt. Son code est modifié et son compteur de thème passe de 4 à 5 ; le compteur local de l’extension reste 3.

Les fiches utilisent d’abord les données Inertia du HTML natif, au lieu de lancer systématiquement un lecteur WebView. Le catalogue accepte les deux formes de données Inertia, dans l’attribut data-page ou dans le script JSON. Les API de recherche, de pagination et de chapitres partagent les en-têtes JSON attendus. Une réponse 403/429 reçue de ces routes peut être récupérée par la session WebView du site ; les autres échecs restent des erreurs explicites. Les données HTML et API sont rafraîchies par le réseau pour éviter une ancienne réponse.

Pour le lecteur v2, le script existant est injecté avant les modules du site dans le HTML frais. Les injections onPageStarted/onPageFinished restent présentes pour les navigations suivantes. Le message rendu par le site « Lecture impossible sur cet appareil » est remonté explicitement, au lieu d’attendre un délai générique. Le chiffrement, les clés, les variantes d’images et les conditions d’accès du lecteur ne sont pas modifiés.

Vérifications directes : bibliothèque populaire, bibliothèque récente, deuxième page JSON, recherche Solo, fiche Solo Leveling et HTML de son prologue répondent HTTP 200. La fiche indique 201 chapitres et les renvoie via une API paginée. Le navigateur de test affiche néanmoins « Lecture impossible sur cet appareil ». L’accès au catalogue n’est donc pas présenté comme une preuve de lecture réussie. La lecture réelle du lecteur v2 sur Android reste à tester ; ce correctif ne garantit pas que le site acceptera tous les appareils.

## Portée et compilation

Les autres sources, les API vendues localement, core, compiler, les autres bibliothèques et la configuration de signature ne changent pas. Le format Kotlin et git diff --check sont contrôlés localement. Gradle local ne peut pas télécharger sa distribution (Network is unreachable) ; compilation de tous les APK et lint des modules modifiés, dont PAM, sont exécutés par GitHub Actions avant fusion. Les APK signés et l’index sont ensuite vérifiés après publication.
