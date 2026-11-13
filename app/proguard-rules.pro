# O widget (Glance) roda pelo WorkManager e não carrega se o R8 remover ou renomear essas classes.
-keep class androidx.glance.** { *; }
-keep class com.wenes.escala.widget.** { *; }
-keep class androidx.work.** { *; }
-keep class androidx.startup.** { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
# Receivers e serviço declarados no manifest.
-keep class com.wenes.escala.automacao.** { *; }
