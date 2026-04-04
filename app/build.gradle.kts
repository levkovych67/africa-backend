plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":auth-service"))
    implementation(project(":product-service"))
    implementation(project(":order-service"))
    implementation(project(":admin-service"))
    implementation(project(":telegram-service"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("com.github.ben-manes.caffeine:caffeine")

    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}
