pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "summa-backend"

// Grunnmur: felles bibliotek
// Docker: /app/grunnmur/, lokalt: ../../grunnmur/, CI: ../grunnmur/
val grunnmurDocker = file("grunnmur")
val grunnmurLocal = file("../../grunnmur")
val grunnmurCi = file("../grunnmur")
if (grunnmurDocker.exists()) {
    includeBuild("grunnmur")
} else if (grunnmurLocal.exists()) {
    includeBuild("../../grunnmur")
} else if (grunnmurCi.exists()) {
    includeBuild("../grunnmur")
}
