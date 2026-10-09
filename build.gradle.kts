plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
}

subprojects {
    group = "com.apols"
    version = "0.0.1"
}
