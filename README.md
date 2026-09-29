# Collection FR — sources Mihon

Ce dépôt est maintenant destiné à contenir une copie indépendante du code source des extensions, et non un catalogue qui renvoie vers les APK signés par Keiyoushi.

## Copie locale du projet source

Le workflow `.github/workflows/bootstrap-upstream.yml` copie le dépôt source public de Keiyoushi dans ce dépôt une seule fois. Une fois copié, le code reste ici même si une source est retirée en amont. Les sources françaises incluses couvrent les modules présents dans le dépôt amont, dont Scan-Manga et Japscan.

Le dépôt amont est sous licence GPL-3.0 ; ses avis de licence sont conservés dans la copie.

## État Mihon

Une copie du code ne suffit pas pour publier des extensions installables. Il faut compiler les APK, les signer avec une clé qui appartient à ce dépôt, puis générer et héberger l'index Mihon correspondant. Les APK Keiyoushi ne sont donc pas présentées comme tes propres builds. La clé de signature n'est pas stockée dans le dépôt public.

## Sources à corriger

- **Scan-Manga** : l'extension actuelle construit l'URL de recherche avec un sous-domaine `bqj.` invalide et utilise l'ancien hôte mobile. Le correctif doit aussi vérifier les URL et sélecteurs du site en ligne.
- **Japscan** : le code actuel est attaché à `japscan.foo`. Il faut confronter l'hôte exact et les URL des chapitres à ceux que Mihon reçoit, car une 404 peut venir d'une URL/site déplacé ou d'une route obsolète.

Les APK ne seront marquées comme fonctionnelles qu'après compilation et vérification de leurs requêtes réelles.