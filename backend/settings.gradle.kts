pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "summa-backend"

// Grunnmur: felles bibliotek (Docker kopierer til /app/grunnmur/, lokalt i ../../grunnmur/)
val grunnmurDocker = file("grunnmur")
val grunnmurLocal = file("../../grunnmur")
if (grunnmurDocker.exists()) {
    includeBuild("grunnmur")
} else if (grunnmurLocal.exists()) {
    includeBuild("../../grunnmur")
}
