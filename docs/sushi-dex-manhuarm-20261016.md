# Sushiscan, MangaDex et Manhuarm — 16 octobre 2026

## Sushiscan : vérification sans changement de domaine

Le module sushiscanfr expose déjà Sushiscan.net, vers https://sushiscan.net, avec son identité conservée. Les sources .net et .fr sont listées séparément par des projets de lecteurs, notamment https://github.com/Moomooo95/moomooo95-extensions/blob/master/README.md. Le signalement historique https://github.com/keiyoushi/extensions-source/issues/10363 distingue également les deux sites.

Cela ne constitue pas une authentification des propriétaires actuels. La sonde HTTP directe reçoit ici une petite page Site Unavailable, alors que la récupération web trouve le catalogue et que l’utilisateur confirme la lecture fonctionnelle. Ces éléments ne prouvent ni une contrefaçon ni un changement d’équipe. Aucun remplacement, suppression ou changement d’APK Sushiscan n’est effectué sur la base de cette incertitude.

## MangaDex 1.6.7

La recherche globale transmet une liste de filtres vide. L’extension recrée alors ses filtres, dont Chapitres disponibles était désactivé par défaut ; cela permettait aux fiches de catalogue sans contenu lisible de remonter. Le filtre est désormais activé par défaut, via hasAvailableChapters=true. Le choix de langue est conservé, et availableTranslatedLanguage n’est ajouté qu’une fois. Le filtre reste désactivable dans une recherche interne explicite, et les recherches directes par identifiant restent inchangées.

Test direct sur l’API MangaDex, recherche solo : 115 fiches sans le filtre, 86 avec le filtre. Une fiche exclue (85276f64-4446-4472-8aa5-fc84c0d24969) renvoie effectivement zéro chapitre dans son feed avec includeEmptyPages=0 et includeFuturePublishAt=0 ; une fiche conservée en renvoie un. Avec la langue fr, l’API renvoie 23 résultats et toutes les entrées de la page contrôlée déclarent une traduction française disponible. Ce filtrage ne peut pas créer des chapitres supprimés, ni annuler les exclusions de groupes ou utilisateurs définies dans les préférences.

## Manhuarm 1.6.5

Le script OCR du lecteur est autonome dans le chapitre testé. La WebView conserve ce script et le DOM du lecteur, mais retire les autres scripts, les iframes et les feuilles externes inutiles à son exécution ; les requêtes restent limitées au domaine du lecteur et à Cloudflare. Le code de session et de déchiffrement propre au site est conservé.

Le résultat déjà décodé d’un chapitre est réutilisé pendant 60 secondes pour au plus trois chapitres, avec verrou par URL et nouvelles instances de Page à chaque appel. Les clés et identifiants OCR à usage unique ne sont pas mis en cache. Les échecs et textes vides ne sont pas mis en cache.

La composition réutilise son TextPaint, analyse le texte HTML une seule fois par bulle et recherche la plus grande taille de texte qui tient par dichotomie, sur les mêmes pas de 0,5 pixel. Les dimensions inutilisables sont ignorées, et la taille reste positive. Les formats et la qualité de compression des images, les réglages de police, la traduction et les limites de requêtes sont conservés.

Tests : le HTML de Nightwatcher 688 répond HTTP 200 et contient 18 images de lecture. Une image réelle répond HTTP 200 image/webp (70 688 octets). La sonde a pris environ 6,3 secondes dans cet environnement ; cela ne mesure pas le délai sur téléphone. Un test DOM isolé exécute le script réel du site, avec une réponse réseau chiffrée simulée, et vérifie qu’il déchiffre et transmet les dialogues français sans jQuery, scripts WordPress, polices, outils d’analyse ou téléchargement des images. Ce test valide l’autonomie du script et la récupération du texte, sans prétendre valider une session réelle du lecteur Android.

## Validation et portée

Les deux modules sont formatés avec ktlint et git diff --check est contrôlé. Le téléchargement de Gradle local échoue avec Network is unreachable ; la compilation des 34 modules et le lint Android des deux modules sont exécutés par GitHub Actions avant fusion. La publication des APK signés et de l’index Mihon est ensuite contrôlée. Toutes les autres extensions, les bibliothèques partagées, les identités de sources, les langues et la signature sont conservées. La latence réseau et le rendu visuel sur téléphone restent à vérifier après mise à jour.
