<div align="center">

# 📚 Collection FR

### Extensions manga, manhwa et webtoon pour Mihon

**Ton dépôt personnel d'extensions : indépendant, organisé et orienté lecture en français.**

[![Mihon](https://img.shields.io/badge/Compatible-Mihon-6366F1?style=for-the-badge)](https://mihon.app/)
[![Langue](https://img.shields.io/badge/Langue-Fran%C3%A7ais-2563EB?style=for-the-badge)](#-les-extensions)
[![Extensions](https://img.shields.io/badge/Index-32%20extensions-16A34A?style=for-the-badge)](https://raw.githubusercontent.com/anderson76389/collection-fr/repo/index.json)
[![Android](https://img.shields.io/badge/Plateforme-Android-404040?style=for-the-badge&logo=android&logoColor=white)](https://github.com/anderson76389/collection-fr/releases)

[📲 Installer dans Mihon](#-installer-le-dépôt-dans-mihon) · [📖 Extensions](#-les-extensions) · [⚙️ Compilation](#-compiler-et-publier) · [🔎 Vérifications](#-suivi-des-correctifs-et-limitations)

</div>

---

## 🔗 Le lien essentiel : dépôt Mihon

> [!IMPORTANT]
> **Adresse officielle de ce dépôt personnel, à ajouter dans Mihon :**
>
> **https://raw.githubusercontent.com/anderson76389/collection-fr/repo/repo.json**
>
> [**📋 Ouvrir le fichier repo.json**](https://raw.githubusercontent.com/anderson76389/collection-fr/repo/repo.json)

~~~text
https://raw.githubusercontent.com/anderson76389/collection-fr/repo/repo.json
~~~

Le fichier est hébergé sur la branche **`repo`** et référence l'index des extensions publiées. Il faut conserver **cette URL exacte** : ce n'est ni le lien de la branche `main`, ni celui d'un APK individuel.

## 📲 Installer le dépôt dans Mihon

1. Ouvre **Mihon** sur ton téléphone Android.
2. Va dans **Parcourir → Extensions**, puis ouvre la gestion des **dépôts d'extensions** (le nom du menu peut varier selon ta version).
3. Ajoute l'URL `repo.json` affichée ci-dessus.
4. Retourne dans les extensions, actualise la liste, puis installe uniquement les sources qui t'intéressent.
5. Vérifie les avertissements de contenu et de confiance avant de procéder à l'installation d'APK.

> [!TIP]
> **Le dépôt n'est pas une application Android.** Il fournit un index que Mihon utilise pour afficher les extensions disponibles. Les APK sont publiés séparément dans les [Releases GitHub](https://github.com/anderson76389/collection-fr/releases).

---

## ✨ Pourquoi Collection FR ?

| Fonctionnalité | Description |
|:--|:--|
| 🇫🇷 **Priorité au français** | Accès à des sources de mangas, manhwas et webtoons en français. |
| 🧩 **Extensions personnalisées** | Modules sélectionnés, adaptés et maintenus dans une copie indépendante du code source. |
| 🌍 **Multilingue lorsque nécessaire** | Certaines extensions proposent plusieurs langues ou une version `all` en plus du français. |
| 📦 **APK et index propres au dépôt** | Publication des fichiers installables et génération d'un index Mihon dédié. |
| 🛠️ **Évolutions suivies** | Développement, correctifs et rapports de vérification conservés dans GitHub. |
| 🔐 **Signature indépendante** | Les APK compilés sont signés avec la clé de publication propre à Collection FR. |

## 📖 Les extensions

L'[index publié sur la branche `repo`](https://raw.githubusercontent.com/anderson76389/collection-fr/repo/index.json) répertorie **30 extensions**. Soft Epsilon Scan et les autres extensions sont conservées. La liste peut évoluer au fil des publications.

<details open>
<summary><strong>🇫🇷 Sources françaises et orientées lecture en français</strong></summary>

**Mangas et scans**

`AnimeSama` · `Astral-Manga` · `Scan-Manga` · `Scan Reader` · `Scan VF` · `Manga-Scantrad` · `Mangas-Origines.fr`

**Manhwas, webtoons et autres catalogues**

`Webtoons.com` · `Manhuarm` · `Manga Ball` · `SayManhwa` · `Blossom Scans` · `Pantheon Scan` · `Poseidon Scans` · `Raijin Scans` · `Rimu Scans` · `Dassou Scan` · `Harmony-Scan`

</details>

<details>
<summary><strong>🌍 Extensions multilingues et autres sources publiées</strong></summary>

`Asura Scans`

L'index peut inclure des sources classées `all` ainsi que d'autres extensions réservées à un public adulte. Certaines entrées portent un avertissement `MIXED` ou `NSFW` : respecte l'âge requis et vérifie les informations de chaque extension avant installation. **Collection FR ne garantit pas que tous les résultats soient disponibles en français.**

</details>

> [!NOTE]
> Une extension présente dans l'index ne signifie pas que **toutes** ses fonctions marchent en permanence. Les sites peuvent changer d'adresse, imposer un CAPTCHA, limiter leur accès ou modifier leurs lecteurs. Les résultats de compilation ne constituent pas une validation complète dans Mihon.

## 🏗️ Organisation du dépôt

Ce dépôt sépare le **code source** et les **fichiers distribués à Mihon**.

| Emplacement | Rôle |
|:--|:--|
| [`main`](https://github.com/anderson76389/collection-fr/tree/main) | Code des extensions, scripts et configuration de compilation |
| [`src/fr/`](src/fr) | Extensions francophones |
| [`src/all/`](src/all) | Extensions multilingues |
| [`lib-multisrc/`](lib-multisrc) | Bibliothèques partagées entre plusieurs sources |
| [`assets/source-logos/`](assets/source-logos) | Éléments graphiques de certaines sources |
| [`.github/workflows/`](.github/workflows) | Automatisation des vérifications et publications |
| [`repo`](https://github.com/anderson76389/collection-fr/tree/repo) | Branche utilisée pour l'index Mihon |
| [**Releases**](https://github.com/anderson76389/collection-fr/releases) | APK distribués et autres fichiers générés |

La copie du code de [Keiyoushi / extensions-source](https://github.com/keiyoushi/extensions-source) a été réalisée pour conserver une base de travail indépendante. L'historique d'import est indiqué dans [`.collection-fr-upstream-snapshot`](.collection-fr-upstream-snapshot).

Les deux versions exactes de l’API de compilation sont [conservées dans ce dépôt](vendor/extensions-lib), avec leurs sources et licences. Gradle les lit exclusivement depuis cette copie locale : la compilation ne dépend plus du téléchargement de `keiyoushi/extensions-lib`. Les autres outils et bibliothèques externes restent nécessaires.

---

## ⚙️ Compiler et publier

Le workflow [**Build and publish personal extensions**](.github/workflows/build-and-publish.yml) compile les modules sélectionnés, signe les APK, prépare les fichiers publiés et actualise l'index sur la branche `repo`.

### 🔑 Configuration de la signature Android

Si tu veux recompiler et publier toi-même les extensions, crée une clé Android **locale** :

~~~sh
keytool -genkeypair -v -keystore collection-fr.jks -alias collection-fr -keyalg RSA -keysize 2048 -validity 10000
~~~

Encode le keystore en Base64 :

<details>
<summary><strong>🐧 Linux</strong></summary>

~~~sh
base64 -w0 collection-fr.jks
~~~

</details>

<details>
<summary><strong>🪟 Windows PowerShell</strong></summary>

~~~powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("collection-fr.jks"))
~~~

</details>

Dans **Settings → Secrets and variables → Actions**, configure les quatre secrets :

| Nom | Valeur |
|:--|:--|
| `SIGNING_KEY` | Contenu Base64 de `collection-fr.jks` |
| `ALIAS` | Alias défini lors de la création de la clé |
| `KEY_STORE_PASSWORD` | Mot de passe du keystore |
| `KEY_PASSWORD` | Mot de passe de la clé de signature |

Lance ensuite **Actions → Build and publish personal extensions → Run workflow**.

> [!CAUTION]
> **Ne commite jamais le fichier `.jks` et ne partage aucun secret.** Les APK signés avec une clé différente de celle d'une extension déjà installée ne peuvent pas automatiquement remplacer celle-ci sous Android : il peut être nécessaire de désinstaller l'ancienne version. Assure-toi de protéger les données de lecture avant toute migration.

## 🔎 Suivi des correctifs et limitations

Le dépôt contient des rapports de tests et de nettoyage, notamment :

- [🔬 Audit des lecteurs Android — 4–5 octobre 2026](docs/reader-audit-2026-10-05.md) : vérifications du comportement de lecture, contraintes de CAPTCHA et diagnostic réseau.
- [🧪 Audit des sources — 29 septembre 2026](docs/source-audit-2026-09-29.md) : tests ciblés et suivi de plusieurs dysfonctionnements.
- [🧹 Nettoyage des sources et langues — 5 octobre 2026](docs/languages-and-cleanup-2026-10-05.md) : explications sur les changements de sélection et de langue.

**Points de vigilance :** Scan-Manga a connu des erreurs de chargement ou de lecture, et certains sites bloquent les accès automatisés avec des réponses HTTP 403. Une erreur dans un robot de test ne prouve pas à elle seule que la source est définitivement hors service.

Des sources ont été retirées ou remplacées au cours du projet. **L'index publié sur la branche `repo` fait foi** pour les APK actuellement proposés dans Mihon ; il peut différer des fichiers de référence conservés sur `main`.

---

## ⚖️ À propos et crédits

Collection FR est un **dépôt personnel et indépendant**, non officiel et non affilié à l'équipe Mihon ni aux plateformes référencées.

Le code repose en partie sur l'écosystème [Keiyoushi](https://github.com/keiyoushi/extensions-source) et sur des bibliothèques tierces. Consulte [LICENSE](LICENSE), les mentions de licence des fichiers concernés et les règles de contribution avant toute redistribution ou modification.

Les extensions facilitent l'accès à des sites externes, mais **n'hébergent pas elles-mêmes les mangas ou webtoons**. Les disponibilités, les droits et les conditions d'utilisation restent propres à chaque service.

---

<div align="center">

### 📲 Prêt à ajouter Collection FR ?

**[Ajouter l'adresse du dépôt à Mihon](https://raw.githubusercontent.com/anderson76389/collection-fr/repo/repo.json)**

`https://raw.githubusercontent.com/anderson76389/collection-fr/repo/repo.json`

*Un dépôt personnalisé pour retrouver tes lectures, tes sources et tes extensions au même endroit.*

[⬆️ Retour en haut](#-collection-fr)

</div>
