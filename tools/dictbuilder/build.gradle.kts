// Command-line tool that turns JMdict (the free Japanese→English dictionary, CC BY-SA 4.0)
// into the compact SQLite database bundled in the app.
//
//   ./gradlew :tools:dictbuilder:buildDictionary
//
// It runs automatically before the app build when the database is missing.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.sqlite.jdbc)
    testImplementation(libs.junit)
}

val downloadCache = layout.buildDirectory.dir("jmdict")
val assetDir = rootProject.layout.projectDirectory.dir("app/src/main/assets/dictionary")

tasks.register<JavaExec>("buildDictionary") {
    group = "build"
    description = "Downloads JMdict and builds app/src/main/assets/dictionary/jmdict.db"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.github.otoniel2004.dictbuilder.MainKt")
    args(downloadCache.get().asFile.absolutePath, assetDir.asFile.absolutePath)
    // JMdict declares hundreds of XML entities (&n; &v1; ...); lift the JDK's default limits.
    jvmArgs(
        "-Xmx2g",
        "-Djdk.xml.entityExpansionLimit=0",
        "-Djdk.xml.totalEntitySizeLimit=0",
        "-Djdk.xml.maxGeneralEntitySizeLimit=0",
    )
    outputs.file(assetDir.file("jmdict.db"))
    outputs.file(assetDir.file("jmdict.version"))
}
