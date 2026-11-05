# Installer Mikayala depuis GitHub, sans PC

## A. Secrets GitHub à configurer (une seule fois)
Chrome Android → github.com/koneelise258-oss/Mikayla-V2 → menu « … » → **Version pour ordinateur** (sinon l'onglet Settings est caché) → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.

| Nom exact | Valeur |
|---|---|
| `GOOGLE_SERVICES_JSON` | contenu complet de google-services.json (déjà fait) |
| `SUPABASE_URL` | `https://yrcnneyejqlkumjbetfh.supabase.co` |
| `SUPABASE_ANON_KEY` | clé « anon public » (Supabase → Project Settings → API) |
| `ANDROID_KEYSTORE_BASE64` | contenu du fichier ANDROID_KEYSTORE_BASE64.txt (une seule ligne, tout copier) |
| `ANDROID_KEYSTORE_PASSWORD` | contenu de ANDROID_KEYSTORE_PASSWORD.txt |
| `ANDROID_KEY_ALIAS` | `mikayala` |
| `ANDROID_KEY_PASSWORD` | contenu de ANDROID_KEY_PASSWORD.txt (identique au mot de passe du keystore) |

Gardez une copie de `mikayala-backup.jks` + mot de passe en lieu sûr (Google Drive privé, gestionnaire de mots de passe). **Si la clé est perdue, aucune mise à jour ne pourra s'installer par-dessus : il faudra désinstaller.** Ne mettez jamais ces fichiers dans le dépôt.

## B. Télécharger l'APK
1. Chrome → github.com/koneelise258-oss/Mikayla-V2 → onglet **Actions** (connecté à votre compte GitHub).
2. Workflow **Android build** → le run le plus récent avec une coche verte sur la branche `claude/mikayala-v2-audit-dev-ryddh8`.
3. Vérifier le résumé du run : « Firebase réel : true » et « Signature stable : true ».
4. En bas, section **Artifacts** → **mikayala-apk-test-fcm** → téléchargement d'un ZIP.
5. Ouvrir l'application **Fichiers** → Téléchargements → toucher le ZIP → **Extraire** → on obtient `app-debug.apk`.

## C. Installer
1. Toucher `app-debug.apk`.
2. Si Android demande : **Paramètres** → autoriser « Installer des applis inconnues » pour Fichiers (ou Chrome) → retour → **Installer**.
3. Play Protect peut avertir (application hors Play Store) → « Installer quand même ».
4. Ouvrir Mikayala, accepter les notifications (Android 13+), se connecter.
5. Pour la partenaire : envoyer le même `app-debug.apk` (ou le même artifact) et répéter C.

Mise à jour : installer le nouvel APK par-dessus, sans désinstaller (possible uniquement si « Signature stable : true » pour l'ancien ET le nouveau APK, empreintes identiques dans le résumé).
Première installation de la version signée : si une version précédente signée avec une autre clé est installée, Android refusera → la désinstaller une fois.

## D. Tests de notifications à faire sur les deux téléphones
1. Les deux connectés → envoyer un message → notification avec nom et aperçu.
2. Application en arrière-plan, puis fermée (balayée), puis écran verrouillé.
3. Appel → sonnerie ; raccrocher avant réponse → la sonnerie doit disparaître chez l'autre.
4. Paramètres → Notifications → désactiver « Messages » → plus de notification de message, les appels continuent.
5. Mode silencieux → notification sans son ni vibration.
6. Déconnexion → plus aucune notification. Reconnexion → elles reviennent.
Limite Android : après « Forcer l'arrêt » ou avec une économie de batterie agressive (Xiaomi, Oppo, Huawei…), la livraison peut être bloquée.
