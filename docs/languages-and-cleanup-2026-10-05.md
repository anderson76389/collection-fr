# Nettoyage et langues — 5 octobre 2026

Suppression des neuf modules demandés : `bigsolo`, `chaostrad`, `lesporoiniens`, `sushiscan`, `sirenscansfr`, `scanr`, `mangakawaii`, `mangahubfr`, `mangacorporation`.

Ces modules ne faisaient pas partie des 16 APK publiés. `sushiscanfr` (Sushiscan.net), `scanreader`, les bibliothèques partagées et les outils de compilation sont conservés. Une suppression du code GitHub ne désinstalle pas un ancien APK déjà présent sur le téléphone.

## Français et multilingue dans Mihon

L’index publié contient 15 extensions françaises et Manhuarm, qui fournit sept langues dont le français. Manhuarm reste dans `src/all/manhuarm` et n’est pas supprimé.

Dans Mihon 0.20.4, deux choses sont distinctes :

- Le groupe d’une extension devient `all` lorsque ses sources ont plusieurs langues (`NetworkExtensionStore.toAvailableExtensions`). Manhuarm est donc bien multilingue.
- Le filtre des langues énumère les langues des **sources**, pas les noms des dossiers du dépôt (`GetExtensionLanguages`). Il ne crée pas une case `all` pour chaque extension multilingue. Le français de Manhuarm est accessible en activant Français.

Il serait incorrect de remplacer artificiellement `fr` par `all` dans l’index : cela ne changerait pas la langue réelle de l’APK et pourrait désynchroniser l’affichage.

Actualiser la liste des extensions dans l’application puis vérifier son filtre de langues. Pour Manhuarm, installer l’extension, lui accorder la confiance, puis activer sa source française dans les sources. Si seules les sources déjà installées sont affichées, les langues proposées peuvent différer de celles du catalogue des extensions. Le dépôt ne peut pas modifier à distance les préférences du téléphone.

Le comportement de TachiyomiSY peut différer ; la version exacte et l’écran du filtre sont nécessaires pour diagnostiquer une différence persistante.

Références du code vérifié :

- https://github.com/mihonapp/mihon/blob/v0.20.4/data/src/main/java/mihon/data/extension/model/NetworkExtensionStore.kt
- https://github.com/mihonapp/mihon/blob/v0.20.4/app/src/main/java/eu/kanade/domain/extension/interactor/GetExtensionLanguages.kt
