# ProGuard Rules — Wise Maestro
# ─────────────────────────────────────────────────────────────────────────────

# ── Java-WebSocket ────────────────────────────────────────────────────────────
# Conserver toutes les classes WebSocket (nécessaire pour la réflexion interne)
-keep class org.java_websocket.** { *; }
-dontwarn org.java_websocket.**

# ── Room Database ─────────────────────────────────────────────────────────────
# Conserver les entités Room (les champs sont accédés par réflexion)
-keep class com.wisedesign.maestro.db.entity.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.**

# ── Gson (Sérialisation JSON du protocole LiveCommand) ────────────────────────
# Conserver les champs du modèle LiveCommand pour que Gson fonctionne
-keep class com.wisedesign.maestro.model.LiveCommand { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.gson.**

# ── Règles générales Android ──────────────────────────────────────────────────
-keep class androidx.localbroadcastmanager.** { *; }
-dontwarn sun.misc.**
-dontwarn java.lang.invoke.**
