# R8 rules (release minification). The libraries in use ship their own rules.

# Kuromoji loads its dictionary through reflection and Java resources.
-keep class com.atilika.kuromoji.** { *; }
