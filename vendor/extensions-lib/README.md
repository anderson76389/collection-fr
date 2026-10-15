# API de compilation conservées

Ces copies permettent de compiler Collection FR si le dépôt amont
`keiyoushi/extensions-lib` ou ses publications JitPack deviennent indisponibles.

| API | Révision publiée | Commit source complet |
| --- | --- | --- |
| 1.4 | `18a8e26be2` | `18a8e26be2320b48bdaa11840170479b62989e23` |
| 1.6 | `42255ee5fa` | `42255ee5fa96d9425697b7c143587483207308d1` |

- `maven/` contient les AAR, POM et métadonnées Gradle publiés, sans modification.
- `sources/` contient les archives complètes des deux commits amont.
- `licenses/` conserve leur licence Apache 2.0.
- `provenance.json` indique les origines et révisions.
- `SHA256SUMS` permet de contrôler l'intégrité des copies.

Les empreintes des AAR ont été comparées à celles des métadonnées publiées.
Les noms de paquet et coordonnées d'origine sont conservés pour la compatibilité
et l'attribution ; ils ne provoquent pas de téléchargement chez Keiyoushi.

`settings.gradle.kts` réserve exclusivement ce module au dépôt Maven local.
Il n'existe aucun repli vers JitPack pour ces deux API. Une copie manquante ou
une nouvelle version non archivée fait échouer la compilation explicitement.

Les classes de ces API sont des stubs fournis par Mihon à l'exécution. Leur
utilisation reste en `compileOnly` dans les extensions, comme avant ; cette
copie ne remplace pas les classes de l'application.

Pour vérifier la résolution locale des deux AAR :

```sh
(cd vendor/extensions-lib && sha256sum -c SHA256SUMS)
./gradlew --init-script .github/scripts/verify_local_api.init.gradle.kts verifyLocalExtensionApis
```

Les archives source permettent également de consulter ou reconstruire les API.
La chaîne de compilation Android et les autres bibliothèques tierces restent
des dépendances externes. Les métadonnées Maven amont contiennent leurs liens
d'attribution et des variantes de documentation ; les archives source complètes
sont conservées séparément ici pour leur consultation.
