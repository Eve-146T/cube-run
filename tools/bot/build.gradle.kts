plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}
kotlin { jvmToolchain(17) }
application { mainClass.set("cube.run.bot.SurveyKt") }
tasks.register<JavaExec>("selfTest") {
    group = "verification"
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("cube.run.bot.SurveyKt")
    args("--self-test")
}
tasks.named("check") { dependsOn("selfTest") }
