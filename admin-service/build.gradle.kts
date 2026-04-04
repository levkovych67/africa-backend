dependencies {
    implementation(project(":common"))
    implementation(project(":product-service"))
    implementation(project(":order-service"))
    implementation(project(":auth-service"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("software.amazon.awssdk:s3:2.29.51")
}
