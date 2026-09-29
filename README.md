# Collection FR — sources indépendantes pour Mihon

Ce dépôt contient une copie indépendante du code source des extensions françaises. Il ne réutilise plus les APK signés par Keiyoushi.

## Copie du code source

Le workflow `.github/workflows/bootstrap-upstream.yml` a copié le dépôt `keiyoushi/extensions-source` une seule fois. Cette copie reste dans `main` même si une source disparaît de l’amont. Les changements personnels sont conservés dans ce dépôt.

Le code amont est sous licence GPL-3.0 ; ses fichiers de licence et mentions sont conservés.

## État des sources

Les extensions explicitement marquées adultes (`NSFW`) ont été retirées de `src/fr`, sauf Scan-Manga réintégrée à la demande de l’utilisateur avec son avertissement conservé. Les sources `Mixed` restent disponibles, conformément au choix de garder les sources pouvant contenir du contenu adulte non exclusif.

- **Astral-Manga** est incluse dans la publication en version 1.4.48 et son site répond avec des chapitres récents.
- **Japscan** reste incluse (https://www.japscan.foo). Les retours indiquent que la lecture des chapitres peut encore échouer après le CAPTCHA ; le site n’est donc pas classé comme fermé.
- Les erreurs 403 reçues par les runners GitHub ne suffisent pas à conclure qu’un site est hors service : elles peuvent venir de la protection anti-robot.

## Ajouts et limites de vérification

Blossom Scans (`blossom-scans.com`), Pantheon Scan (`pantheon-scan.com`), Soft Epsilon Scan (`epsilonsoft.to`) et Manhuarm (`manhuarmtl.com`) utilisent les modules présents dans cette copie de Keiyoushi. Manhuarm conserve ses variantes linguistiques, dont le français.

La compilation et le lint ne constituent pas un test de lecture dans Mihon. Manhuarm a des signalements amont « No results found » et NullPointerException. Soft Epsilon inclut le correctif PAM du lecteur v2, mais un signalement « Attestation refused » reste ouvert. Scan-Manga est restaurée dans sa dernière version locale, sans prétendre résoudre l’erreur de lecture 404.

## Créer le dépôt Mihon

Le workflow `.github/workflows/build-and-publish.yml` compile seize extensions françaises et Manhuarm (multilingue), les signe avec ta clé privée, publie les APK comme une release et génère l’index Mihon dans la branche `repo`.

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

