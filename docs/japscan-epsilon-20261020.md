# Japscan et Soft Epsilon Scan — 20 octobre 2026

- Japscan : https://www.japscan.foo — français, APK MAKOFF, identifiant historique 11 conservé, version 1.6.9.
- Soft Epsilon Scan : https://epsilonsoft.to — français, APK MAKOFF, identité historique conservée, version 1.6.11.
- Code lecteur et dépendances copiés depuis le commit amont Keiyoushi ff521c17bb3e503c01e658e8fac00b60a95829f3. Aucun téléchargement du code amont au lancement ou pendant la compilation.
- Pam, secretstream et ece sont nécessaires au lecteur Soft Epsilon. Les autres extensions restent inchangées.
- Le catalogue public Epsilon fournit ses données Inertia dans le HTML : extraction directe pour les pages simples, WebView conservée pour les appels interactifs et le lecteur.
- Japscan ne sert depuis son hôte de cache que ses propres fichiers temporaires.

## Contrôles réseau

Les deux accueils répondent HTTP 200. La bibliothèque Epsilon répond HTTP 200 et fournit 30 œuvres, avec une pagination annonçant 6 070 œuvres. Un chapitre Japscan testé répond HTTP 403 depuis l’environnement de vérification ; cette réponse seule ne permet pas d’attribuer la cause à Cloudflare.

## Limites de validation

La compilation, les tests automatisés et les catalogues accessibles ne prouvent pas la lecture sur Mihon. Les lecteurs et les vérifications des sites n’ont pas été validés de bout en bout sur un téléphone. Aucune promesse de contournement permanent ni de lecture garantie. Les vérifications interactives restent manuelles en WebView.

Sur Mihon : actualiser le dépôt, installer les deux APK français, puis tester catalogue, recherche, liste de chapitres et plusieurs pages de lecture. Si une vérification est demandée, ouvrir la source ou le chapitre en WebView. Signaler le message exact, le titre et le chapitre si la lecture échoue.
