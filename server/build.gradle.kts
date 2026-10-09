plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

application {
    mainClass = "com.apols.ApplicationKt"
}


dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.h2)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.kotlinx.html)
    implementation(libs.ktor.server.htmx)
    implementation(libs.ktor.htmx.html)
    implementation(libs.kotlin.css)
    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.micrometer.registry.prometheus)
    implementation("org.imgscalr:imgscalr-lib:4.2")
    implementation("commons-io:commons-io:2.13.0")
    implementation("io.github.smiley4:ktor-openapi:5.3.0")
    implementation("io.github.smiley4:ktor-swagger-ui:5.3.0")
    implementation("io.ktor:ktor-server-websockets:3.0.0")
    implementation("io.ktor:ktor-server-routing-openapi:3.4.1")
    implementation("io.ktor:ktor-server-openapi:3.4.1")
    implementation("io.ktor:ktor-server-swagger:3.4.1")
    implementation("io.ktor:ktor-server-auth-jwt:3.0.0")
    implementation("io.ktor:ktor-server-websockets:3.0.0")
    implementation("org.jetbrains.exposed:exposed-java-time:1.0.0-rc-4")
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.webjars)
    implementation(libs.jquery)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.apache)
    implementation("io.ktor:ktor-client-content-negotiation:3.3.2")
    implementation("org.postgresql:postgresql:42.7.2")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("io.ktor:ktor-server-rate-limit: 3.3.0")
    implementation("io.ktor:ktor-server-compression: 3.3.0")
    // Redis
    implementation("redis.clients:jedis:5.1.0")

    // DI
    implementation("io.insert-koin:koin-ktor:3.5.3")
    implementation("io.insert-koin:koin-logger-slf4j:3.5.3")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")

    // Email
    implementation("com.sun.mail:jakarta.mail:2.0.2")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    // Validation
    implementation("org.valiktor:valiktor-core:0.12.0")

    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}
