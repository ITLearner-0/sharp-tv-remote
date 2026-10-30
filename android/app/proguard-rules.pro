# Bibliothèque ADB et protocole de la TV : conservés tels quels par précaution.
-keep class dadb.** { *; }
-keep class io.github.itlearner0.tvremote.protocol.** { *; }

# Dépendances optionnelles référencées par okio / dadb mais absentes sur Android.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
