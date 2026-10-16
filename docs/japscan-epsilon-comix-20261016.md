# Retrait de Comix et lecteurs — 16 octobre 2026

## Comix retiré

Suppression du module `src/all/comix`, de ses scripts, icônes et tests, des
modules publiés, des tâches de compilation/lint et de sa vérification de langue.
L’index et la nouvelle release doivent contenir 32 extensions, sans Comix ni
Sushiscan. Le retrait du catalogue ne désinstalle pas l’APK sur un téléphone.
L’historique Git et les anciennes releases sont conservés.

## Soft Epsilon Scan 1.6.9

Le lecteur actuellement servi utilise `CSKhBvn8b.js` / `CSL6ERowb.js` et le
décryptage WebAssembly. Deux incompatibilités sont visibles dans le code :

- Le manifeste est construit par `Object.defineProperty`, qui ne déclenche pas
  l’ancien setter `Object.prototype.hint` de l’extension.
- L’import HKDF ultérieur reçoit des octets aléatoires après la dérivation de la
  vraie clé par le lecteur. Capturer cet import ne fournit donc plus la clé.

Le lecteur inspecte également les propriétés ajoutées à `Object.prototype`, les
modifications de fonctions crypto et les interfaces JavaScript natives. Cela
explique pourquoi l’ancienne instrumentation est inadaptée ; le critère exact
refusé par le serveur sur le téléphone de l’utilisateur n’a pas été observé.

La source utilise maintenant une navigation WebView normale pour le lecteur v2,
sans interception de clé, modification de prototypes ou interface JavaScript
native ajoutée à la page. Elle laisse le site effectuer son propre décryptage,
fait défiler les pages et transfère les canvas rendus en PNG aux dimensions du
lecteur. Une seule page est transférée à la fois, puis stockée dans un cache
local propre à la source. Les données complètes du chapitre sont renvoyées
uniquement après réception de toutes les pages ; le cache d’un échec est effacé.
Les fichiers terminés expirent après 24 heures.

Le catalogue, les chapitres, les couvertures, les filtres et le lecteur v1
hérité restent en place. Aucun fichier de `lib-multisrc/pam`, `lib` ou `core`
n’est changé. Les CAPTCHA doivent toujours être validés par l’utilisateur.

## Japscan 1.6.7

L’utilisateur confirme que les images du chapitre sont visibles dans la WebView
Mihon. La capture attend maintenant le montage asynchrone du lecteur : premier
blob en mode paginé, premier canvas dessiné en mode webtoon. Elle ne démarre
plus sur le seul événement `onPageFinished`, qui peut précéder ces étapes.

Les chapitres à une seule page conservent leur premier blob au lieu de le jeter
avant un aller-retour impossible. La fin de capture annonce désormais un nombre
d’images attendu et obtenu ; les chapitres partiels et les échecs d’écriture sont
refusés explicitement. Le nombre de tuiles est réévalué après leur création.
Une absence de progression pendant 45 secondes produit une erreur précise.
Les fichiers créés par une capture échouée sont supprimés. Le processus de
validation interactive reste inchangé et aucune validation n’est simulée.

## Contrôles et limites

`node --test src/fr/softepsilonscan/tests/rendered-reader.test.mjs src/fr/japscan/tests/paginated-reader.test.mjs`
exécute 14 tests de régression sur fixtures : lecture mono/multipage, ordre,
capture partielle, montage asynchrone, canvas vide, rendu par worker, chargement, ancien lecteur,
refus du site et restriction d’export. Ils ne constituent pas un test Android.

Le 16 octobre, le catalogue Epsilon et une page de chapitre répondent HTTP 200 ;
ce chapitre annonce `reader_v2: true` et `page_count: 0`. Le nouveau chemin lit
le nombre de pages réellement créées par le lecteur, pas cette valeur zéro.
L’accès HTTP de diagnostic à Japscan reçoit un challenge Cloudflare 403 ; ce
refus ne permet pas de tester son lecteur en direct depuis cet environnement.

La compilation de tous les APK conservés et leur lint Android sont contrôlés
par les Actions avant fusion. Aucun appareil Android avec Mihon n’est disponible
ici : l’acceptation réelle par les sites, le rendu WebView mobile et la lecture
complète sur le téléphone restent à confirmer. Un refus de compatibilité ou un
canvas non exportable produit une erreur explicite, pas une fausse réussite.
