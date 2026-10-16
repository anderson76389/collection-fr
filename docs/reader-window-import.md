# Lecture WebView : Japscan et Soft Epsilon

Le chapitre est ouvert dans la WebView habituelle de Mihon. Terminez manuellement les vérifications du site, attendez que les images du chapitre soient visibles, puis appuyez sur **Importer ce chapitre dans Mihon**. Gardez cette fenêtre au premier plan jusqu’au retour au lecteur.

L’import utilise le rendu visible de cette même fenêtre Android. Il ne recharge pas son HTML dans une seconde WebView et n’utilise pas les exports canvas que le lecteur Epsilon bloque explicitement (`SecurityError: Canvas is blocked.`). Aucun CAPTCHA n’est résolu automatiquement, aucune restriction de fenêtre sécurisée n’est désactivée.

Limites : Android/Mihon est nécessaire, l’import est interactif et les téléchargements sans interface ne sont pas pris en charge. Les PNG ont la résolution du rendu à l’écran, pas nécessairement celle des fichiers originaux. Les pages très longues sont divisées en parties consécutives pour limiter la mémoire. Les fichiers temporaires expirent après 24 h ; les téléchargements enregistrés par Mihon restent gérés par Mihon. Si le site refuse lui-même de rendre une page, l’import ne peut pas la reconstruire.

Validation : tests de géométrie/états des lecteurs et test Android de la copie d’un canvas rendu par un Worker avec export JavaScript interdit. Ce test local ne prouve pas la compatibilité de toutes les versions de Mihon ni le fonctionnement réel des deux sites ; un essai sur appareil reste nécessaire.
