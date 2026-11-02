plugins {
    id("mcpainter.java-conventions")
}

dependencies {
    api(project(":nbt"))
}

tasks.test {
    providers.environmentVariable("MCPAINTER_FIXTURES").orNull?.let {
        systemProperty("mcpainter.fixtures", it)
        testLogging.showStandardStreams = true
    }
}
