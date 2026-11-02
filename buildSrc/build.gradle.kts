plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        register("javaConventions") {
            id = "mcpainter.java-conventions"
            implementationClass = "space.qu4nt.thirdparty.mcpainter.build.JavaConventionsPlugin"
        }
    }
}
