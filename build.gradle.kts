plugins {
    id("org.openstreetmap.josm").version("0.8.2")
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")
    implementation("com.google.guava:guava:31.1-jre")
    //implementation("org.locationtech.jts:jts-core:1.19.0")
    //implementation("org.jgrapht:jgrapht-jdk1.5:0.7.3")
    packIntoJar("org.jgrapht:jgrapht-core:1.0.0")
    

}

// tasks.named<Test>("test") {
//     // Use JUnit Platform for unit tests.
//     useJUnitPlatform()
// }

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
  pluginName = "JOSM Missing Tools"
  debugPort = 3626
  josmCompileVersion = "19439"
  manifest {
    author = "Maratkuls Kosojevs"
    description = "Some Missing Tools for Working with Polygons and Polygon Relations. 1. Cuts Multipolygons by creating parallel offset from way connecting 2 nodes outside Multipolygon.2. Unglue Polygons from ways."
    iconPath = "images/mapmode/CutPolygon.svg"
    mainClass = "org.openstreetmap.josm.plugins.missingtools.MissingTools"
    minJosmVersion = "19017"
    canLoadAtRuntime = true
    pluginDependencies.add("utilsplugin2")
    minJavaVersion = 11
  }
}


