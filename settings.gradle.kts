// A new Kraft app starts compliant, which is cheaper than bringing one into compliance later.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "chesskraft-app"
include(":app")
include(":engine")

// The foundation. Required: `build.uses-shared-library` fails the build without it.
includeBuild("../kraft-foundation") {
    dependencySubstitution {
        substitute(module("com.kraft:kraft-ui")).using(project(":kraft-ui"))
        substitute(module("com.kraft:kraft-core")).using(project(":kraft-core"))
    }
}
