# Comix et MangaKawaii — 16 octobre 2026

## Comix 1.6.52

Un même document de titre peut servir aux détails et aux recommandations au lieu de déclencher plusieurs téléchargements rapprochés. Le cache contient au plus six documents valides pendant vingt secondes. Les chargements simultanés d'un même titre partagent un verrou, sans bloquer les autres titres.

Les listes complètes de chapitres obtenues sans arrêt sur un chapitre connu sont réutilisées pendant vingt secondes, avec une limite de six listes. Les réponses partielles ne sont pas mises en cache et invalident le document associé. Le tri, la déduplication, les équipes exclues, le nombre de chapitres et la pagination existants restent conservés. Aucune accélération des requêtes au-delà de la cadence du lecteur n'est introduite.

La première récupération d'une grande liste peut donc rester longue : le lecteur charge des lots de vingt chapitres avec un espacement de 2,5 secondes. Les caches réduisent les téléchargements doublons et les réouvertures rapprochées ; ils ne garantissent pas un chargement instantané de toute nouvelle œuvre. Le site Comix renvoie HTTP 403 aux sondes de cet environnement, ce qui empêche une mesure fiable du délai final dans Mihon.

## MangaKawaii 1.6.4

Les requêtes normales et les demandes de navigateur ne donnent pas toujours le même résultat : le lecteur et la pagination ont d'abord répondu HTTP 403, puis HTTP 200 lors de contrôles avec une session et des en-têtes de navigation. Une erreur 403 ne permet donc pas de conclure que le chapitre a disparu.

Les demandes HTML utilisent des en-têtes de navigation explicites. Si une demande normale reçoit 403, le document est chargé par la WebView Android réelle, avec ses cookies et son user-agent. La WebView ne reçoit pas un simple copier-coller de la page d'erreur. L'attente est limitée à trente secondes ; une vérification interactive non validée reste à effectuer par l'utilisateur dans la WebView. Les ressources externes inutiles et le chargement des images sont bloqués pendant cette récupération de HTML. Aucun proxy distant n'est ajouté.

Pour les longues listes, l'extension utilise le sélecteur complet des chapitres présent dans le lecteur. Sur Martial Peak 1533, les données du lecteur contiennent 1556 adresses de chapitre uniques et quinze images. Les cinquante adresses de la fiche et les cinquante adresses de la deuxième page AJAX figurent toutes dans cet index. La liste initiale apporte ses titres et dates ; les dates déjà connues sont conservées pour les autres chapitres. La pagination antérieure reste disponible si l'index ne correspond pas à la fiche.

Les documents lecteur et leurs URL d'images signées sont récupérés sans utiliser une ancienne réponse HTTP en cache, puis réutilisés au plus vingt secondes, pour trois lecteurs maximum. Les images portent le Referer du chapitre et des en-têtes adaptés aux images. Une première image réelle a répondu HTTP 200 image/jpeg (189184 octets) dans les contrôles de navigateur. Les réponses 403 observées restent possibles selon la session et les contrôles du site ; la WebView Android et la lecture intégrale doivent être confirmées sur téléphone. Les faux liens `__mk_trap__` restent exclus.

Le filtrage des œuvres licenciées introduit en 1.6.3 est conservé. Les autres sources, leurs réglages et toutes les bibliothèques partagées restent inchangés. Compilation, lint et publication des APK signés sont contrôlés par GitHub Actions.
