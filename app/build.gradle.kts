plugins {
    id("org.openstreetmap.josm").version("0.8.2")
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")

    // This dependency is used by the application.
    implementation("com.google.guava:guava:31.1-jre")

    //implementation("org.locationtech.jts:jts-core:1.19.0")

    implementation("org.jgrapht:jgrapht-jdk1.5:0.7.3")
}

// application {
//     // Define the main class for the application.
//     mainClass.set("josm_missingtools.App")
// }

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

tasks.runJosm {
  jvmArgs(
    "--add-exports=java.base/sun.security.action=ALL-UNNAMED",
    "--add-exports=java.desktop/com.sun.imageio.plugins.jpeg=ALL-UNNAMED",
    "--add-exports=java.desktop/com.sun.imageio.spi=ALL-UNNAMED"
  )
}

tasks.debugJosm {
  jvmArgs(
    "--add-exports=java.base/sun.security.action=ALL-UNNAMED",
    "--add-exports=java.desktop/com.sun.imageio.plugins.jpeg=ALL-UNNAMED",
    "--add-exports=java.desktop/com.sun.imageio.spi=ALL-UNNAMED"
  )
}

tasks.compileJava_testedJosm{
  val compDirPath = layout.buildDirectory.dir("comp")
  destinationDirectory = compDirPath
}

josm {
  pluginName = "josm_missingtools"
   debugPort = 3626 // choose a random port for your project (to avoid clashes with other projects)
  josmCompileVersion = "19094"
  manifest {
    description = "Missing Tools for Working with Polygons"
    mainClass = "org.openstreetmap.josm.plugins.missingtools.MissingTools"
    minJosmVersion = "19017"
    author = "Maratkuls Kosojevs"
    // canLoadAtRuntime = true
    // iconPath = "path/to/my/icon.svg"
    // loadEarly = false
    // loadPriority = 50
    // pluginDependencies += setOf("apache-commons", "apache-http")
    // website = java.net.URL("https://example.org")
    // oldVersionDownloadLink(123, "v1.2.0", java.net.URL("https://example.org/download/v1.2.0/MissingTools.jar"))
    // oldVersionDownloadLink( 42, "v1.0.0", java.net.URL("https://example.org/download/v1.0.0/MissingTools.jar"))

    // to populate the 'Class-Path' attribute in the JOSM plugin manifest invoke
    // the function 'classpath', i.e.
    //   classpath "foo.jar"
    //   classpath "sub/dir/bar.jar"
    // This results in 'Class-Path: foo.jar sub/dir/bar.jar' in the
    // manifest file. Added class path entries must not contain blanks.
  }
  // i18n {
  //   bugReportEmail = "me@example.com"
  //   copyrightHolder = "John Doe"
  // }
}


