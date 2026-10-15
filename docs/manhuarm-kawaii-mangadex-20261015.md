# Manhuarm, Mangakawaii et MangaDex — 15 octobre 2026

## Manhuarm 1.6.4

Le module est déplacé vers `src/fr/manhuarm`, en conservant le paquet Android `all.manhuarm`, l'identifiant français `8537659881437820380`, les polices, licences et réglages. La source Multiple est retirée et la langue cible reste le français.

Les dialogues français natifs sont désormais prioritaires ; seuls les dialogues sans français passent par le traducteur. Les textes ne déclenchent plus « Dialog not found » lorsqu'une traduction native est présente sans champ `text`. Le dessin et la traduction des bulles reconnaissent les fragments de données indépendamment du suffixe et des paramètres de l'URL de l'image. Les fragments sont construits avec HttpUrl afin de préserver les caractères du texte. Les mises à jour respectent les demandes de détails et de chapitres ; les chapitres sans date connue ne sont plus éliminés à tort. Les lecteurs sans texte exploitable ou sans correspondance entre texte et images produisent un message explicite au lieu d'afficher silencieusement des bulles vides.

Vérification actuelle : catalogue, fiche Nightwatcher avec 698 liens de chapitres, chapitre 688 avec 18 images et script lecteur reconnu. Le service OCR renvoie HTTP 403 dans la sonde HTTP de cet environnement. Le lecteur conserve l'exécution du JavaScript du site dans la WebView Android ; aucune nouvelle méthode de contournement du service n'est ajoutée. La récupération du texte et sa composition complète dans Mihon ne sont pas validables sans téléphone ou émulateur.

## Mangakawaii 1.6.3

Le filtrage des œuvres licenciées est possible à partir des badges du catalogue : l'extension est conservée. Le catalogue populaire testé contient 24 cartes, dont 16 marquées LICENCIÉ à masquer. Les récents testés contiennent 24 cartes, dont 8 licenciées.

La recherche générale `/recherche` omet ces badges, y compris sur Solo Leveling : la recherche de l'extension passe donc par `/mangas?q=…`, qui les expose. La recherche « solo » testée contient 24 cartes, dont 6 licenciées. Les pages entièrement masquées sont parcourues jusqu'à trouver des cartes conservées ou atteindre la fin, avec mémorisation des correspondances de pagination. Une fiche licenciée déjà enregistrée ou ouverte par lien est refusée explicitement.

Les œuvres non marquées licenciées restent dans les résultats. Cela ne garantit pas l'accès permanent à chaque chapitre : le lecteur et les chapitres supplémentaires de Martial Peak répondent HTTP 403 à la sonde actuelle. Les restrictions du site ne sont pas contournées et les œuvres de la bibliothèque du téléphone ne sont pas supprimées automatiquement.

## MangaDex 1.6.6

Un réglage « Langue des œuvres et des chapitres » propose Toutes les langues et les langues du catalogue MangaDex. La sélection filtre populaires, récents, recherche, listes, flux d'utilisateurs, agrégats et chapitres. Les langues régionales utilisent les codes API déjà prévus par l'extension. Toutes les langues reste la valeur par défaut. Le paquet, l'identifiant de source, la catégorie Multiple et les clés des autres réglages sont conservés.

Vérification API : dix résultats de catalogue avec français disponible ; dix chapitres français ; dix chapitres anglais. Les flux interrogés annoncent respectivement 100235 chapitres français, 838032 anglais et 1986927 sans filtre au moment du contrôle. Les résultats réels peuvent évoluer.

Toutes les autres extensions et bibliothèques partagées sont conservées. La compilation, la signature et le lint sont contrôlés par GitHub Actions ; le test final dans Mihon nécessite le téléphone.
