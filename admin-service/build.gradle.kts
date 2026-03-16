dependencies {
    implementation(project(":common"))
    implementation(project(":auth-service"))
    implementation(project(":product-service"))
    implementation(project(":order-service"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("software.amazon.awssdk:s3:2.29.45")
    implementation("software.amazon.awssdk:s3-transfer-manager:2.29.45")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
