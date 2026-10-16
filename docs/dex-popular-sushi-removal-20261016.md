# MangaDex, retrait Sushiscan et lecteurs encore bloqués — 16 octobre 2026

## MangaDex 1.6.8 : Populaires sur tout le catalogue disponible

Le tri par popularité était limité aux fiches créées dans les trente derniers jours. Cette borne createdAtSince est supprimée ; le tri order[followedCount]=desc, la pagination et les préférences de langues, de contenu et de langue originale restent conservés. hasAvailableChapters=true évite de remplir Populaires avec des fiches sans contenu lisible, comme le fait désormais la recherche par défaut.

Contrôle direct de l’API le 16 octobre 2026 : en français, la requête bornée à trente jours renvoie 8 fiches ; sans cette borne elle en renvoie 6 714, avec une première page complète de vingt résultats dans le test. Toutes langues confondues, les totaux mesurés sont 239 contre 61 429. Ces nombres décrivent ces requêtes de test avec les valeurs par défaut de l’API ; les préférences de contenu ou de langue originale choisies dans Mihon peuvent réduire le catalogue.

## Retrait définitif de Sushiscan de la version courante

Le module src/fr/sushiscanfr, son code et ses cinq icônes sont retirés. Le script de publication, la liste de compilation signée, le lint et le README sont ajustés à 33 extensions. Les autres modules et les bibliothèques partagées sont conservés. Sushiscan ne doit plus figurer dans le nouvel index ni dans la nouvelle release. Les commits et les anciennes releases restent dans l’historique ; ce changement ne désinstalle pas un ancien APK présent sur le téléphone.

## Comix et Soft Epsilon Scan : lecture toujours non résolue

L’utilisateur confirme encore HTTP 502 avec Comix 1.6.53 et lecteur incompatible avec Soft Epsilon Scan 1.6.8. Les relances et les corrections de catalogue précédentes n’ont pas suffi à rétablir leur lecture.

Les signalements ouverts https://github.com/keiyoushi/extensions-source/issues/20043 et https://github.com/keiyoushi/extensions-source/issues/19915 décrivent respectivement HTTP 502 sur Comix et une lecture Epsilon bloquée. Le commentaire Epsilon du 14 octobre confirme l’impossibilité de poursuivre. Les derniers changements disponibles pour les chemins Comix et PAM restent ceux des 15 et 13 octobre, déjà présents/adaptés dans ce dépôt.

Le module lecteur Epsilon inspecté (CSL6ERowb.js, chargé par le composant réel du chapitre) calcule des informations WebGL, Canvas, CSS, géométrie et WebGPU et soumet une attestation. Il lit ensuite le champ supported de la réponse du serveur ; supported=false conduit au message d’appareil incompatible. Cette vérification est distincte du contrôle Cloudflare et de la simple récupération des fiches et des chapitres. L’inspection ne prouve pas quel critère échoue sur le téléphone de l’utilisateur. Aucun changement de lecteur sans validation réelle n’est présenté comme une correction, et les versions de ces deux APK ne sont pas augmentées dans cette publication.

## Validation

Vérification de syntaxe du script de publication, égalité de sa liste avec les 33 modules retenus, format Kotlin et git diff --check. Le lint local ne peut pas télécharger Gradle (Network is unreachable) ; compilation de tous les modules conservés et lint Android par GitHub Actions avant fusion, puis contrôle des APK signés et de l’index après publication. Les identités de source et d’application, la signature et les 32 autres extensions conservées restent inchangées.
