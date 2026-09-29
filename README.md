# Collection FR — sources indépendantes pour Mihon

Ce dépôt contient une copie indépendante du code source des extensions françaises. Il ne réutilise plus les APK signés par Keiyoushi.

## Copie du code source

Le workflow `.github/workflows/bootstrap-upstream.yml` a copié le dépôt `keiyoushi/extensions-source` une seule fois. Cette copie reste dans `main` même si une source disparaît de l’amont. Les changements personnels sont conservés dans ce dépôt.

Le code amont est sous licence GPL-3.0 ; ses fichiers de licence et mentions sont conservés.

## Correctifs en cours

- **Scan-Manga** : l’extension utilisait `m.scan-manga.com` et construisait sa recherche sur `bqj.m.scan-manga.com`, un hôte qui ne résout pas. Le code utilise maintenant `www.scan-manga.com` et `/search/quick.json` sans le sous-domaine `bqj.`.
- **Japscan** : l’extension utilise le domaine demandé `japscan.lol`. Le lecteur a été adapté avec le correctif de rendu par WebView/canvas proposé dans [le PR amont #18111](https://github.com/keiyoushi/extensions-source/pull/18111), qui traite l’échec de lecture après vérification humaine. Le filtre de chapitres plus récent du dépôt a été gardé.

La compilation et le lint Release de Scan-Manga et Japscan passent dans GitHub Actions. Les requêtes HTTP lancées depuis les runners GitHub reçoivent une réponse 403 des protections anti-bot des sites ; elles ne reproduisent pas une lecture depuis ton téléphone. Le correctif doit donc encore être essayé dans Mihon sur ton réseau. Pour Japscan, le lecteur peut afficher le CAPTCHA du site.

## Créer le dépôt Mihon

Le workflow `.github/workflows/build-and-publish.yml` compile les onze sources françaises, les signe avec ta clé privée, publie les APK comme une release et génère l’index Mihon dans la branche `repo`.

Avant son premier lancement, crée une clé Android locale et ajoute ces secrets dans **Settings → Secrets and variables → Actions** du dépôt :

- `SIGNING_KEY` : contenu de `collection-fr.jks` encodé en Base64
- `ALIAS` : alias donné à la clé
- `KEY_STORE_PASSWORD` : mot de passe du keystore
- `KEY_PASSWORD` : mot de passe de la clé

Exemple pour créer la clé :

```sh
keytool -genkeypair -v -keystore collection-fr.jks -alias collection-fr -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 collection-fr.jks
```

Sur PowerShell, encode le keystore avec :

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("collection-fr.jks"))
```

Garde le fichier `.jks` hors de GitHub et ne partage pas ces valeurs dans une conversation. Après avoir enregistré les secrets, lance **Actions → Build and publish personal extensions → Run workflow**.

Une fois la première publication terminée, ajoute cette adresse comme dépôt dans Mihon :

```
https://raw.githubusercontent.com/anderson76389/collection-fr/repo/repo.json
```

La clé de signature est propre à ce dépôt. Si des extensions signées par Keiyoushi sont déjà installées, Android ne les mettra pas à jour par-dessus celles-ci ; il faudra les remplacer par les versions de Collection FR.

