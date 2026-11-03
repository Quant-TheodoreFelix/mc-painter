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
    if (System.getProperty("os.name").lowercase().startsWith("mac")) {
        applicationDefaultJvmArgs = listOf("--add-exports=java.desktop/com.apple.eawt.event=space.qu4nt.thirdparty.mcpainter")
    }
}

tasks.named<Test>("test") {
    systemProperty("java.awt.headless", "true")
}
