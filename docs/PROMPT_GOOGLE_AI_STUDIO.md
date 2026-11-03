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
