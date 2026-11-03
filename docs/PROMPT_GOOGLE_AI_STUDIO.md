Tu reprends le projet Android « Mikayala V2 » (Kotlin, Compose, Supabase, FCM) du dépôt koneelise258-oss/Mikayla-V2, branche `claude/mikayala-v2-audit-dev-ryddh8`. Lis d'abord `docs/AUDIT_MIKAYALA_V2.md`.

Règles : ne repars pas de zéro ; ne supprime aucune fonctionnalité ; pas de simulation ni de données fictives ; aucun secret dans le code ; ne modifie pas la production Supabase sans me présenter la migration ; ne dis jamais « testé » sans test réel.

Déjà fait (non compilé) : défaut de notification « romantic », réglages par catégorie (`util/NotificationPrefs.kt`), persistance son/vibration/alertes, ignore des push sans session, liste blanche de types dans `send-fcm`.

À faire dans cet ordre, en gardant le projet compilable à chaque étape :
1. Compiler, corriger les erreurs éventuelles, exécuter `./gradlew test`.
2. Notifications : canaux Android par catégorie (messages/appels/jeux/calendrier) avec son et vibration, type `call_declined` et `call_cancelled`, écran d'explication si `POST_NOTIFICATIONS` refusée, test des états app ouverte/fond/fermée.
3. Refaire l'écran Paramètres en une page à sections (Compte, Notifications, Confidentialité, Discussions, Appels, Stockage, Apparence), style WhatsApp, chaque réglage persisté, aucun bouton sans action.
4. Refaire l'UI/UX (inspiration Pinterest, cartes, navigation claire), supprimer les éléments inutiles.
5. Ensuite seulement : WebRTC, P2P Wi-Fi Direct, durcissement Supabase (buckets privés, RLS), jeux/calendrier/cycle.
Après chaque étape : rapport de ce qui est vérifié et de ce qui exige un test sur les deux téléphones.
Contrainte : appareils réels Android 7 et Android 10 ; compatibilité API 24 → 36 obligatoire (pas d'API récente sans garde Build.VERSION).

---
## Mise à jour — refonte native / UI (à lire en premier)

Déjà fait dans cette branche (non compilé, à vérifier dans le prévisualiseur) :
- `SettingsScreen.kt` : l'écran Paramètres est désormais une liste de catégories style WhatsApp (Compte & couple, Apparence & discussions, Discussions, Notifications, Confidentialité & sécurité, Stockage & données, Mode de connexion) avec sous-pages et bouton retour. Tous les anciens réglages et boîtes de dialogue sont conservés.

À faire, dans cet ordre :
1. Compiler ; corriger toute erreur dans `SettingsScreen.kt` (icônes `Rounded.Chat/Storage/WifiTethering/ArrowBack`, `BackHandler`).
2. Performance sur téléphones modestes (Android 7 et 10) : dans `ChatScreen.kt` et `MainDashboardScreen.kt`, utiliser `LazyColumn` avec `key`, `remember`/`derivedStateOf`, éviter flous/ombres/dégradés animés coûteux, limiter les recompositions (états stables), charger les images avec Coil à taille réduite.
3. Chat style WhatsApp : en-tête avec avatar et présence, bulles simples avec heure et coches, séparateurs de date, barre de saisie arrondie avec micro/pièce jointe, réponse par glissement, sélection multiple.
4. Navigation : barre du bas simple (Discussions, Appels, Espace couple, Coffre, Paramètres) ; retirer `GlassmorphicFloatingNavBar` si lourde ; supprimer boutons sans action et données de démo.
5. Thème clair/sombre réellement appliqué partout (`isDarkMode`).
6. Supabase : ne rien modifier (géré à part avec ChatGPT).
Contraintes : Android 7 (API 24) → 16, aucune simulation, aucune fonctionnalité supprimée.
