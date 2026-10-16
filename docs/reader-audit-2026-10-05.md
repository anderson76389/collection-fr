# Scan-Manga et Japscan — audit des 4 et 5 octobre 2026

## Méthode et limites

APK signés de Collection FR installés dans une installation neuve de Mihon 0.20.4, émulateur Android 15/API 35, réseau GitHub Actions. La signature de chaque APK est vérifiée avant installation. Les captures XML/PNG et les journaux réseau sont conservés dans les artefacts Actions pendant 14 jours. Un workflow vert signifie que le scénario de diagnostic est terminé, pas que la source fonctionne.

Le téléphone de l’utilisateur utilise un autre réseau et ses précédentes captures proviennent de TachiyomiSY. Les résultats de l’émulateur ne prouvent donc pas le comportement de ce téléphone. Aucun compte personnel n’est utilisé.

## Japscan

- Version précédente : 1.4.72. Catalogue, nouveautés et recherche renvoient HTTP 403 depuis le runner.
- La capture WebView du 30 septembre donne la cause précise : **Cloudflare 1005**, avec interdiction de l’ASN **8075** par le propriétaire du site. Un captcha ne supprime pas cette interdiction réseau.
- Le code amont a changé depuis : migration 1.6 le 30 septembre, puis lecteur WebView suspendu le 3 octobre, commit `0e94db46fef78393c6bc6f098bf77e6fc92646c2`.
- Collection FR intègre désormais cette implémentation en **1.6.1** : exécution via le helper WebView, sérialisation des chargements de pages par mutex, pont JavaScript pour les pages mises en cache, ouverture de la WebView sur le catalogue. Identifiant de source `11` et nom du paquet conservés.
- La validation de compilation/lint a réussi. La clé de signature reste celle de Collection FR.

## Scan-Manga

La version 1.4.27 a été exécutée le 4 octobre : catalogue, nouveautés, recherche « High », fiche High Class Society et liste de 88 chapitres chargés. L’ouverture du chapitre 88 redirige vers `/404.html`.

Le diagnostic du HTML montre une erreur différente de la 404 `/lel/` signalée par l’utilisateur : ce chapitre pointe réellement vers `https://m.webtoons.com/fr/romance/high-society/ep-88/viewer?title_no=7653&episode_no=88`. L’extension supprimait le domaine et fabriquait une requête `https://www.scan-manga.com/fr/romance/high-society/ep-88/viewer?...`, qui ne peut pas fonctionner.

La version **1.4.28** :

- conserve les liens de chapitres externes et permet à la WebView d’ouvrir leur véritable destination ; elle indique explicitement qu’un tel chapitre ne relève pas du lecteur Scan-Manga ;
- conserve l’URL finale du document après les redirections HTTP pour produire les en-têtes `Origin`, `Referer` et `Source` de la requête `/lel/` ;
- traite une réponse JSON vide de recherche comme une liste vide, au lieu d’une exception de décodage.

La correction des liens WEBTOON ne constitue pas une correction démontrée de `/lel/`. Le test distinct High-Martial du 4 octobre a chargé sa fiche et sélectionné le chapitre 70 sous Scan-Manga 1.4.27. Le chargement du lecteur mobile a ensuite reçu HTTP 403 et affiché « Just a moment… » dans Cloudflare. Ce test ne permet pas de valider l’appel `/lel/`, situé après ce blocage. Japscan 1.6.1 a également reçu HTTP 403 sur catalogue, nouveautés et recherche.

## Traces vérifiables

- [High-Martial et Japscan 1.6.1 ; tentative 1 du 4 octobre](https://github.com/anderson76389/collection-fr/actions/runs/37166828991/attempts/1).
- [Audit Android du 4 octobre, versions 1.4.27 et 1.4.72](https://github.com/anderson76389/collection-fr/actions/runs/37166013878).
- [HTML réel des liens de chapitres High Class Society](https://github.com/anderson76389/collection-fr/actions/runs/37166609491).
- [Validation Japscan 1.6.1](https://github.com/anderson76389/collection-fr/actions/runs/37166200513).
- [Validation Scan-Manga 1.4.28](https://github.com/anderson76389/collection-fr/actions/runs/37166777415).
- [Correctif amont Japscan du 3 octobre](https://github.com/keiyoushi/extensions-source/commit/0e94db46fef78393c6bc6f098bf77e6fc92646c2).

## Reproduire

Le workflow `Android source audit` installe les APK réellement publiés. Le scénario enregistre les écrans du catalogue, des nouveautés, de la recherche, des détails et du lecteur lorsqu’il peut l’atteindre. Il ne résout pas un captcha humain et ne contourne pas une interdiction d’ASN. Les résultats de lecture exigent l’examen des captures et des erreurs ; aucune réussite de lecture n’est déduite de la seule présence de chapitres.


## Contrôles du 5 octobre

Scan-Manga 1.4.28 a été installé et exécuté dans Mihon 0.20.4 : catalogue, nouveautés, recherche et fiche High-Martial fonctionnent dans ce scénario. Le chapitre 70 reste bloqué dans la WebView Cloudflare sur `m.scan-manga.com`. Japscan 1.6.1 renvoie toujours HTTP 403 sur ce réseau de test.

Le contrôle des deux hôtes avec deux User-Agent donne :

| Hôte demandé | Navigateur annoncé | Résultat |
| --- | --- | --- |
| www.scan-manga.com | ordinateur | HTTP 200, véritable HTML du lecteur, identifiant de chapitre présent |
| www.scan-manga.com | mobile | redirection vers m.scan-manga.com puis HTTP 403 |
| m.scan-manga.com | ordinateur | HTTP 403 |
| m.scan-manga.com | mobile | HTTP 403 |

La version **1.4.29** utilise donc le lecteur ordinateur, normalise les anciens liens mobiles vers `www` et garde un User-Agent cohérent pour le lecteur, l’API et les images. Le lint puis la compilation et la publication ont réussi. Cela corrige une voie d’accès bloquée ; cela ne suffit pas à prouver la lecture des images.

Le JavaScript public `lel.js?vers=5.8` a été récupéré et son tableau de chaînes décodé localement. Il confirme l’API `https://bqj.scan-manga.com/lel/<idc>.json`, le jeton `yf`, les champs `a=sme`, `b=sml`, `c=base64(JSON)` et le fingerprint `{gpu, connection}`. Le site lit `navigator.connection?.type ?? "IC"`, alors que l’extension utilise encore la valeur fixe `cellular`.

Un test HTTP reproduisant ces paramètres sur le chapitre 70, avec le fallback GPU `IC` et les connexions `IC` puis `cellular`, reçoit dans les deux cas HTTP 200 avec le corps `{"error":"503"}`. **Aucune liste de pages ni image n’a été validée par ce test.** Il ne permet pas d’attribuer cette réponse à une panne générale du site.

Traces supplémentaires :

- [Android 1.4.28, tentative 2](https://github.com/anderson76389/collection-fr/actions/runs/37166828991/attempts/2).
- [Comparaison des hôtes](https://github.com/anderson76389/collection-fr/actions/runs/37247907174).
- [JavaScript public du lecteur](https://github.com/anderson76389/collection-fr/actions/runs/37248182021).
- [Réponses de l’API](https://github.com/anderson76389/collection-fr/actions/runs/37248445326).
- [Lint de Scan-Manga 1.4.29](https://github.com/anderson76389/collection-fr/actions/runs/37248093721).
- [Publication de Scan-Manga 1.4.29](https://github.com/anderson76389/collection-fr/actions/runs/37248319899).

Le contrôle supplémentaire dans Chrome (JavaScript du site, sans résolution automatisée de captcha) reste sur une page Cloudflare avant l’appel API. Il ne fournit donc pas de preuve de lecture. [Trace Chrome](https://github.com/anderson76389/collection-fr/actions/runs/37248598663).

## Résultat Android final : version 1.4.29

La tentative 3 a installé Scan-Manga **1.4.29** puis ouvert **High-Martial, chapitre 70**. Le lecteur ordinateur est désormais chargé et l’appel `/lel/` est atteint. À 00:45:20 UTC, Mihon enregistre :

```text
ReaderActivity: java.lang.IllegalStateException:
Received error response from data API: { "error": "503" }
```

Le lecteur revient à la fiche du manga, sans pages affichées. La correction est donc **partielle** : accès au HTML du lecteur rétabli dans ce test, mais lecture des images toujours en échec. Aucun contournement complet n’est annoncé. Japscan 1.6.1 reste en HTTP 403 sur le même runner.

[Audit Android final, tentative 3](https://github.com/anderson76389/collection-fr/actions/runs/37166828991/attempts/3).

Le comportement sur le réseau du téléphone peut différer. Pour poursuivre un diagnostic spécifique au téléphone, relever la version de l’application, la version 1.4.29 de l’extension et le message complet du même chapitre, après actualisation des extensions.
