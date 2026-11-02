plugins {
    id("mcpainter.java-conventions")
    application
}

dependencies {
    implementation(project(":io"))
}

application {
    mainModule = "space.qu4nt.thirdparty.mcpainter"
    mainClass = "space.qu4nt.thirdparty.mcpainter.Main"
}

tasks.named<Test>("test") {
    systemProperty("java.awt.headless", "true")
}
