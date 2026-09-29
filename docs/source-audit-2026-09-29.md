# Vérification des 17 extensions — 29 septembre 2026

## Limites de validation

Tests HTTP effectués depuis l'environnement de développement, avec les routes et sélecteurs des extensions. Pour les lecteurs accessibles, téléchargement complet et décodage de trois images (première, centrale, dernière). Ce n'est **pas** une exécution des APK dans Mihon : aucun téléphone Android, ADB, émulateur ou accélération KVM n'est disponible dans cet environnement. Les résultats réseau peuvent différer de ceux du téléphone.

Une page d'accueil accessible, une compilation réussie ou une image téléchargée ne permettent pas d'affirmer que toute une source fonctionne. Aucun résultat ci-dessous ne signifie que tous les mangas et tous leurs chapitres ont été testés.

## Résultats

| Extension | Test effectué | Résultat / limite |
| --- | --- | --- |
| AnimeSama | Catalogue : 48 entrées reconnues ; fiche One Piece ; API des chapitres ; chapitre 1195, 12 pages, images 1/7/12 décodées | Parcours HTTP accessible. Couverture de fiche corrigée : le sélecteur `#coverOeuvre` ne correspond plus à la page ; utilisation de `og:image`. |
| Astral-Manga | Requête réelle `/api/mangas` avec paramètres du catalogue | HTTP 403, challenge Cloudflare ; catalogue et lecture non validés. |
| Harmony-Scan | `https://harmony-scan.fr/` | HTTP 403 Cloudflare ; lecture non validée. |
| Blossom Scans | `/api/series?page=1&limit=36&sort=popularity` | HTTP 403 Cloudflare ; catalogue et lecture non validés. |
| Dassou Scan | Catalogue : 14 cartes ; fiche System Universe ; chapitre 1 : 24 pages, images 1/13/24 décodées | Parcours HTTP accessible. Chapitres verrouillés exclus conformément à la préférence existante. |
| Japscan | Accueil, catalogue, fiche One Piece et chapitre 1194 | Accueil 200 ; catalogue, fiche et chapitre 403 Cloudflare. Le lecteur WebView du dépôt n'a pas pu être exécuté ici. Pas de nouvelle correction du lecteur confirmée. |
| Manga-Scantrad | `https://manga-scantrad.io/` | HTTP 403 Cloudflare ; lecture non validée. |
| Mangas Origines | `https://mangas-origines.fr/` | HTTP 403 Cloudflare ; lecture non validée. |
| Manhuarm | `/manga/?m_orderby=trending` | HTTP 403 Cloudflare ; recherche, détails et traduction des images non validés. |
| Pantheon Scan | Catalogue : 12 cartes ; fiche Solo Leveling ; POST `/ajax/chapters/` ; chapitre 194 : 12 pages, images 1/7/12 décodées | Parcours HTTP accessible. |
| Phenix Scans | Accueil et API `/api/front/homepage?section=top` | Accueil 502 ; API 522. Le serveur d'origine de l'API ne répond pas depuis cet environnement. |
| Rimu Scans | `https://rimuscan.fr/` | HTTP 403 Cloudflare ; lecture non validée. |
| Scan-Manga | Accueil et lecture du code de récupération `/lel/` | Accueil 404 depuis cet environnement. Correction ciblée du parseur et des en-têtes API, mais **l'erreur 404 de lecture reste non résolue/non vérifiée**. |
| ScanReader | Accueil ; fiche Killing Darling Baby-Ying ; liste AJAX ; chapitre 1 : 13 pages, images 1/7/13 décodées | Parcours HTTP accessible avec nonce frais. Un ancien nonce renvoyait 403 `-1`, puis une nouvelle requête de fiche et son nonce ont permis le POST 200. Autre chapitre testé : 169 pages, images 1/85/169 décodées. |
| Scan VF | Catalogue `/filterList` ; recherche `one` (3 résultats) et `a` (18) ; fiche One Piece ; chapitre 1194 : 12 pages, images 1/7/12 décodées | Parcours HTTP accessible. Correction du calcul de pagination de recherche pour ne plus omettre une dernière page incomplète. Le défaut de pagination n'affectait pas les deux recherches live ci-contre. |
| Soft Epsilon Scan | `https://epsilonsoft.to/` | HTTP 403 Cloudflare ; échange d'attestation et lecture non validés. |
| Sushiscan.net | Accueil et `/catalogue/?page=1&order=popular` | HTTP 200 mais corps « Site Unavailable » (195 octets), sans catalogue. Ne pas confondre HTTP 200 et fonctionnement. |

## Modifications de cette vérification

- Scan-Manga 1.4.26 : reconnaissance du wrapper `eval(/*EB*/function ...)` et des scripts multilignes ; recherche de `idc` dans tout le document ; espaces flexibles autour de cette déclaration. Ancien wrapper conservé.
- Scan-Manga : en-tête `Source` contenant l'URL du chapitre et `Referer` contenant l'origine, cohérents avec l'implémentation indépendante SushiDL. Cette adaptation est un correctif candidat : aucun échange API réussi n'a été obtenu ici pour confirmer son effet sur la 404. Le domaine `bqj.scan-manga.com` n'est pas remplacé par une adresse inventée.
- AnimeSama 1.4.18 : couverture de fiche extraite de `og:image`, avec ancien sélecteur conservé en repli.
- Scan VF 1.4.17 : pagination bornée et dernière page partielle conservée.
- Aucun changement du lecteur Japscan sans reproduction Android. Aucune source supprimée sur la seule base des blocages réseau.

Vérifications locales : `git diff --check` ; test des expressions régulières avec le moteur Java/JVM (ancien wrapper, wrapper commenté multiligne, absence de faux positif) ; sélecteur de couverture comparé au HTML réel ; cas limites de pagination 0, 1, 23, 24, 25, 47, 48, 49, 72 et 73 résultats. La validation Android de compilation/lint est exécutée séparément dans GitHub Actions.

## Sources techniques consultées

- [Proposition Scan-Manga #17584](https://github.com/keiyoushi/extensions-source/pull/17584), commit `21d19783694e55895975443f4279da6671117fa8` : adaptation du parseur. Proposition non fusionnée au moment de la consultation ; seuls les changements ciblés décrits ci-dessus ont été repris.
- [SushiDL](https://github.com/itanivalkyrie/SushiDL/blob/main/SushiDL.py), fonctions `build_scanmanga_api_headers` et `request_scanmanga_reader_api` : l'API reste sur `bqj.scan-manga.com`, avec en-tête `source`. Son téléchargement d'images utilise un navigateur ; recopier seulement l'URL ne suffit donc pas à garantir la lecture Android.
- [Proposition Japscan #18111](https://github.com/keiyoushi/extensions-source/pull/18111) : lecteur WebView déjà présent dans le dépôt ; proposition toujours ouverte à la consultation.

## Validation Android encore nécessaire

Pour conclure sur Scan-Manga/Japscan, il faut exécuter l'APK sur la version exacte de Mihon/TachiyomiSY utilisée, reproduire l'ouverture d'un chapitre identifié, puis examiner les journaux réseau et WebView. Les captures précédentes proviennent de TachiyomiSY : un test sur une autre application ne remplace pas ce contrôle. Ne pas réinstaller une extension ou effacer sa configuration au hasard pour contourner ce diagnostic.
