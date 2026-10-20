# Correction des affiches — 20 octobre 2026

Cette correction remplace le traitement AnimeSama décrit dans `loading-covers-20261020.md` : le cadre portrait a donné un mauvais affichage dans Mihon d'après le retour de l'utilisateur.

## AnimeSama 1.4.23

Supprimer complètement l'intercepteur de dessin, son cadre, son redimensionnement et son réencodage JPEG. Conserver les URL originales `data-img`, préférées aux miniatures du site, et laisser Mihon afficher directement l'image reçue. Supprimer également le marqueur d'URL utilisé par ce traitement ; les nouvelles URL diffèrent des versions encadrées mises en cache. Les sélecteurs, listes, chapitres et optimisations des autres sources restent inchangés.

Les images AnimeSama échantillonnées sont horizontales à l'origine. Cette correction n'invente pas de nouvelle affiche portrait et ne change pas le mode de recadrage propre à Mihon.

## Pantheon Scan et Harmony-Scan 1.6.59

Le thème Madara sélectionne `data-src` avant les autres variantes. Dans les listes, cette URL désigne fréquemment une petite miniature WordPress.

Les deux extensions remplacent, pour leurs couvertures uniquement, le suffixe de variante `-175x238`, `-193x278`, etc., par l'URL originale. Les noms réels comme `cover_250x350-1.jpg` sont conservés tels quels ; l'hôte, le chemin d'envoi et les paramètres sont préservés. Si l'original renvoie 404 ou 410, le client reprend l'URL exacte de la miniature. Les images reçues passent directement au lecteur sans transformation ni réencodage. Aucun comportement du thème partagé n'est modifié et les images de chapitres ne sont pas réécrites.

Mesures sur les fichiers réels, réponses HTTP 200 :

| Source / exemple | Miniature de liste | Original disponible |
| --- | --- | --- |
| Harmony, My Gift | 175 × 238 | 1 046 × 1 504 |
| Harmony, My Girlfriend | 175 × 238 dans le HTML | 628 × 885 |
| Pantheon, Tomb Raider King | 175 × 238 dans les populaires | 400 × 600 |
| Pantheon, Amaama Cinderella | 193 × 278 dans la recherche | 360 × 505 |
| Pantheon, Tales of Demons and Gods | 175 × 238 dans les populaires | 193 × 278 |

L'original Pantheon peut donc rester peu défini. L'extension récupère le fichier disponible ; agrandir artificiellement ce fichier ne créerait pas de détails supplémentaires. Le HTML des listes populaires et de recherche Pantheon répondait aussi en HTTP 200. Les originaux ont été contrôlés par décodage des images ; deux affiches portrait ont été inspectées visuellement.

## Contrôles

Tests JVM : priorité de l'original AnimeSama, conservation des URL/proportions par absence de transformation, génération d'URL WordPress, conservation des noms et paramètres, retour à la miniature en 404/410, passage des octets originaux sans réencodage, absence de nouvelle tentative sur un chapitre ou une panne serveur. Les autres tests de sources restent exécutés par le workflow existant ; compilation et lint de tous les APK conservés.

Aucun test visuel sur le téléphone de l'utilisateur n'est revendiqué. Aucun autre code source, bibliothèque partagée, identité, langue ou clé de signature n'est modifié.
