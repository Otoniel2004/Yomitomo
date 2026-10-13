// Plugins declared here are available to modules; each module applies the ones it uses.
// kotlin-android is only put on the classpath: AGP 9 compiles Kotlin by itself ("built-in Kotlin"),
// but it uses the Kotlin version declared here.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
