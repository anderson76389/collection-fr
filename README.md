# Collection FR — sources indépendantes pour Mihon

Ce dépôt contient une copie indépendante du code source des extensions françaises. Il ne réutilise plus les APK signés par Keiyoushi.

## Copie du code source

Le workflow `.github/workflows/bootstrap-upstream.yml` a copié le dépôt `keiyoushi/extensions-source` une seule fois. Cette copie reste dans `main` même si une source disparaît de l’amont. Les changements personnels sont conservés dans ce dépôt.

Le code amont est sous licence GPL-3.0 ; ses fichiers de licence et mentions sont conservés.

## État des sources

Les extensions explicitement marquées adultes (`NSFW`) ont été retirées de `src/fr`. Les sources `Mixed` restent disponibles, conformément au choix de garder les sources pouvant contenir du contenu adulte non exclusif.

- **Astral-Manga** est incluse dans la publication en version 1.4.48 et son site répond avec des chapitres récents.
- **Japscan** reste incluse (https://www.japscan.foo). Les retours indiquent que la lecture des chapitres peut encore échouer après le CAPTCHA ; le site n’est donc pas classé comme fermé.
- Les erreurs 403 reçues par les runners GitHub ne suffisent pas à conclure qu’un site est hors service : elles peuvent venir de la protection anti-robot.

## Créer le dépôt Mihon

Le workflow `.github/workflows/build-and-publish.yml` compile les douze sources françaises, les signe avec ta clé privée, publie les APK comme une release et génère l’index Mihon dans la branche `repo`.

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

