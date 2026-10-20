# Raijin / Aniverse, Japscan et Soft Epsilon — 20 octobre 2026

## Raijin Scans 1.4.72

L’adresse était déjà https://aniverse.fr dans la version 1.4.71. Le problème signalé ne pouvait donc pas être corrigé par un nouveau remplacement du domaine.

La source conserve son identité, ses favoris et sa langue française. Lorsqu’une requête GET au domaine du site reçoit HTTP 403 ou 503, un seul essai est effectué dans la WebView avec sa session locale. Les requêtes JSON sont exécutées depuis la page du catalogue ; les fiches et chapitres sont chargés à leur adresse réelle. Les images des CDN et les réponses déjà réussies ne passent pas par ce repli.

La réponse refusée est fermée avant le repli. Les erreurs du lecteur sont transmises à Mihon ; aucun faux chapitre ou résultat vide n’est créé pour masquer un refus. Aucun captcha n’est résolu automatiquement.

## Japscan 1.6.10

Les hooks du lecteur sont installés au démarrage du document, avant les scripts du site lorsque le moteur WebView le permet. L’ancienne injection asynchrone dans onPageStarted pouvait manquer des appels précoces du lecteur. Le repli pour les anciens moteurs reste celui de l’utilitaire commun. Le mécanisme de validation manuelle reste en place.

## Soft Epsilon Scan 1.6.12

Un refus HTTP 403 tient désormais compte du cookie de validation au moment du refus. Une nouvelle validation dans la WebView peut débloquer immédiatement la tentative suivante au lieu de conserver systématiquement l’attente de cinq minutes. Le délai de protection après HTTP 429 reste inchangé.

## Vérifications et limites

Une sonde depuis GitHub Actions a reçu HTTP 403 pour le catalogue JSON, la recherche, une fiche et un lecteur Aniverse. Les pages visibles via le moteur de recherche confirment le domaine et des routes de lecture, mais ne prouvent pas l’accès réseau depuis Mihon.

Les tests du transport contrôlent les requêtes concernées, la conservation des URL et types de contenu, la fermeture des réponses et l’absence de boucle de tentatives. La compilation et le lint sont exécutés dans GitHub Actions.

Ces changements corrigent des problèmes précis du transport et du démarrage du lecteur. Ils ne garantissent pas que le site accepte chaque appareil ou session, ni qu’une validation Cloudflare rende tous les chapitres accessibles. L’audit Android reste distinct des tests de compilation.


## Résultat réel après publication — Mihon 0.21.2 / Android 35

Les APK publiés ont été téléchargés depuis l’index et installés dans un émulateur Android neuf. Les signatures et versions ont été contrôlées. Le script a essayé populaires, récents, recherche, ouverture en WebView et nouvelle tentative.

| Extension | Version testée | Résultat |
| --- | --- | --- |
| Raijin Scans / Aniverse | 1.4.72 | HTTP 403, y compris le repli par navigateur ; aucun catalogue disponible |
| Japscan | 1.6.10 | HTTP 403 ; aucun catalogue disponible |
| Soft Epsilon Scan | 1.6.12 | HTTP 403 puis demande de validation ; aucun catalogue disponible |

**Le blocage Cloudflare n’est pas résolu dans cet environnement. Aucun chapitre ni page de lecture n’a été validé.** Les tests de compilation et le succès du workflow d’audit ne signifient pas que les sources fonctionnent. L’audit a exécuté les essais et recueilli leurs erreurs ; il n’a pas résolu de captcha interactif.

Ces résultats concernent le réseau de GitHub Actions et Android 35, pas la session du téléphone de l’utilisateur. Ils ne permettent pas de conclure que les scripts de lecture corrigés fonctionnent après une validation manuelle réussie sur ce téléphone.

- [Audit des versions précédentes](https://github.com/anderson76389/collection-fr/actions/runs/39489815364/attempts/1)
- [Audit des nouvelles versions](https://github.com/anderson76389/collection-fr/actions/runs/39489815364/attempts/2)
- [Compilation et lint avant intégration](https://github.com/anderson76389/collection-fr/actions/runs/39490698889)
- [Publication signée](https://github.com/anderson76389/collection-fr/actions/runs/39491538545)
