import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.net.URL
import java.time.Instant
import java.io.FileOutputStream
import kotlin.reflect.full.starProjectedType
import java.io.ByteArrayOutputStream

// the version of the plugin
val PLUGIN_VERSION = "0.0.1"
// the directory where josm core is checked out, set by github workflow
val JOSM_DIR = System.getenv("JOSM_DIR")
// the java language version to use for compilation
val JAVA_LANG_VERSION = 17

plugins {
  id("application")
  id("com.diffplug.spotless") version "6.20.0"
  id("com.github.ben-manes.versions") version "0.47.0"
  id("com.github.spotbugs") version "5.0.14"
  id("net.ltgt.errorprone") version "3.1.0"
  id("org.sonarqube") version "4.3.0.3225"

  eclipse
  jacoco
  java
  `maven-publish`
  pmd
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://josm.openstreetmap.de/nexus/content/repositories/releases/")
    }
}

// Set up ErrorProne
tasks.withType(JavaCompile::class).configureEach {
    options.release.set(JAVA_LANG_VERSION)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial"))
    options.errorprone {
        check("ClassCanBeStatic", CheckSeverity.ERROR)
        check("ConstantField", CheckSeverity.WARN)
        check("DefaultCharset", CheckSeverity.ERROR)
        check("FieldCanBeFinal", CheckSeverity.WARN)
        check("Finally", CheckSeverity.OFF)
        check("LambdaFunctionalInterface", CheckSeverity.WARN)
        check("MethodCanBeStatic", CheckSeverity.WARN)
        check("MultiVariableDeclaration", CheckSeverity.WARN)
        check("PrivateConstructorForUtilityClass", CheckSeverity.WARN)
        check("RemoveUnusedImports", CheckSeverity.WARN)
        check("ReferenceEquality", CheckSeverity.ERROR)
        check("UngroupedOverloads", CheckSeverity.WARN)
        check("UnnecessaryLambda", CheckSeverity.OFF)
        check("WildcardImport", CheckSeverity.ERROR)
    }
}

// java.sourceCompatibility = JavaVersion.VERSION_1_8
// java.targetCompatibility = JavaVersion.VERSION_1_8

val versions = mapOf(
  "awaitility" to "4.2.0",
  // Errorprone 2.11 requires Java 11+
  "errorprone" to if (JAVA_LANG_VERSION >= 11) "2.20.0" else "2.10.0",
  "jdatepicker" to "1.3.4",
  "jmockit" to "1.49.a",
  "junit" to "5.9.3",
  "pmd" to "6.42.0",
  "spotbugs" to "4.7.3",
  "wiremock" to "2.35.0"
)

val home = System.getProperty("user.home")

dependencies {
  errorprone("com.google.errorprone:error_prone_core:${versions["errorprone"]}")
  // testImplementation("org.openstreetmap.josm:josm-unittest:SNAPSHOT"){ isChanging = true }
  testImplementation("com.github.tomakehurst:wiremock-jre8:${versions["wiremock"]}")

  testImplementation("org.junit.jupiter:junit-jupiter-api:${versions["junit"]}")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${versions["junit"]}")
  // This can be removed once JOSM drops all JUnit4 support. Nothing remaining in Mapillary uses JUnit4.
  testImplementation("org.junit.jupiter:junit-jupiter-params:${versions["junit"]}")
  testImplementation("org.junit.vintage:junit-vintage-engine:${versions["junit"]}")

  testImplementation("org.awaitility:awaitility:${versions["awaitility"]}")
  testImplementation("org.jmockit:jmockit:${versions["jmockit"]}")
  testImplementation("com.github.spotbugs:spotbugs-annotations:${versions["spotbugs"]}")
  implementation("org.jdatepicker:jdatepicker:${versions["jdatepicker"]}")

  implementation("org.apache.commons:commons-imaging:1.0-alpha3")

  implementation(files("$home/prj/josm/build/libs/josm.jar"))
  // implementation(files("$home/prj/josm/build/classes/java/main"))
  testImplementation(files("$home/prj/josm/build/classes/java/test"))
  testImplementation(files("$home/prj/josm/test/unit"))
}

/**
 * Get a specific property, either from gradle or from the environment
 */
fun getProperty(key: String): Any? {
    if (hasProperty(key)) {
        return findProperty(key)
    }
    return System.getenv(key)
}

/**
 * Returns a manifest constructed from the file gradle.properties
 */
fun properties2manifest() : Map<String, Any?> {
    val attribs = mutableMapOf<String, Any?>()
    mapOf(
        "plugin.author"           to "Author",
        "plugin.canloadatruntime" to "Plugin-Canloadatruntime",
        "plugin.class"            to "Plugin-Class",
        "plugin.description"      to "Plugin-Description",
        "plugin.early"            to "Plugin-Early",
        "plugin.icon"             to "Plugin-Icon",
        "plugin.link"             to "Plugin-Link",
        "plugin.main.version"     to "Plugin-Mainversion",
        "plugin.compile.version"  to "Plugin-Compileversion",
        "plugin.requires"         to "Plugin-Requires",
        "plugin.version"          to "Plugin-Version"
    ).forEach {
        if (hasProperty(it.key)) {
            attribs.put(it.value, findProperty(it.key))
        }
    }
    return attribs
}

fun translations2manifest() : Map<String, Any?> {
    val outputStream = ByteArrayOutputStream()
    project.exec {
        executable("${JOSM_DIR}/scripts/gettext_translate.py")
        args(listOf("--dir", "src/main/po/", findProperty("plugin.description")))
        standardOutput = outputStream
    }

    val regex = """^([-_\w]+)\.po\s+(.*)$""".toRegex()
    val attribs = mutableMapOf<String, Any?>()
    outputStream.toString("UTF-8").split("\\R".toRegex()).forEach {
        val match = regex.matchEntire(it)
        if (match != null) {
            val (lang, text) = match.destructured
            attribs.put(lang + "_Plugin-Description", text)
        }
    }
    return attribs
}


project.afterEvaluate {
    // That pig-headed josm plugin won't let me use my own josm.jar.
    // Now I'll teach it!
    val mainConfiguration = project.configurations.getByName("implementation")
    mainConfiguration.dependencies.forEach {
        logger.lifecycle("${it.getName()}:${it.getVersion()}");
        if (it.getName() == "josm") {
            mainConfiguration.dependencies.remove(it)
            logger.lifecycle("poof!")
        }
    }
}

sourceSets {
    test {
        java {
            setSrcDirs(listOf("test/unit"))
        }
        resources {
            setSrcDirs(listOf("test/data"))
        }
    }
}

java {
    withJavadocJar()
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(JAVA_LANG_VERSION))
    }
}

// val md2html by tasks.registering(MarkdownToHtml::class) {
//   destDir = File(buildDir, "md2html")
//   source(projectDir)
//   include("README.md", "LICENSE.md")
// }

// Required by the 'shadowJar' task
// project.setProperty("mainClassName", "org.openstreetmap.josm.plugins.mapillary.MapillaryPlugin")

tasks {
	compileJava {
        options.release.set(JAVA_LANG_VERSION)
		options.errorprone.isEnabled.set(false) // takes forever
	}
	compileTestJava {
        options.release.set(JAVA_LANG_VERSION)
		options.errorprone.isEnabled.set(false) // takes forever
    }
    processResources {
        from(project.projectDir) {
            include("LICENSE")
            include("LICENSE_*")
            include("README.md", "LICENSE.md")
        }
    }
    jar {
        archiveBaseName.set("Mapillary")
        manifest {
            attributes(
                "Manifest-Version"            to "1.0",
                "Created-By"                  to JAVA_LANG_VERSION,
                "Plugin-Version"              to PLUGIN_VERSION,
                "Plugin-Date"                 to Instant.now().toString(),
                "Plugin-Minimum-Java-Version" to JAVA_LANG_VERSION
            )
            attributes(properties2manifest())
            attributes(translations2manifest())
        }
        // unzip and add the JDatePicker jar
        from({ configurations.runtimeClasspath.get()
            .filter { it.getName().startsWith("jdatepicker") }
            .map { getProject().zipTree(it) }
        })
    }
}

// tasks.register("generateApiKeyFile") {
//     val apiKeyFileDir = "$buildDir/resources/main"
//     val apiKeyFileName = "mapillary_api_keys.json"
//     doLast {
//         val jsonEncoder = kotlinx.serialization.json.Json { encodeDefaults = true }
//         file(apiKeyFileDir).mkdirs()
//         val environmentToMap = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
//         for (environment in listOf("MAPILLARY_CLIENT_TOKEN", "MAPILLARY_CLIENT_SECRET", "MAPILLARY_CLIENT_ID")) {
//             if (getProperty(environment) != null) {
//                 val prop = getProperty(environment)
//                 environmentToMap[environment] = jsonEncoder.encodeToJsonElement(serializer(prop!!::class.starProjectedType), prop)
//             } else {
//                 logger.warn("$environment not set in environment. Some functionality may not work.")
//             }
//         }
//         //val apiJson = StringBuilder().append("{")
//         //apiJson.append("}")
//         file("$apiKeyFileDir/$apiKeyFileName").writeText(kotlinx.serialization.json.JsonObject(environmentToMap).toString())
//     }
// }
//
// if (getProperty("MAPILLARY_CLIENT_TOKEN") != null) {
//     tasks["processResources"].dependsOn("generateApiKeyFile")
//     tasks["assemble"].dependsOn("generateApiKeyFile")
// } else {
//     logger.warn("MAPILLARY_CLIENT_TOKEN not set in the environment. Build only usable for tests.")
// }

spotless {
  format("misc") {
    target("**/*.gradle", "**.*.md", "**/.gitignore")

    trimTrailingWhitespace()
    indentWithSpaces(4)
    endWithNewline()
  }
  java {
    eclipse().configFile("config/format/code_format.xml")
    endWithNewline()
    importOrder("java", "javax", "")
    indentWithSpaces(4)
    licenseHeader("// License: GPL. For details, see LICENSE file.")
    // Avoid large formatting commits.
    ratchetFrom("origin/master")
    removeUnusedImports()
    trimTrailingWhitespace()
  }
}

// josm {
//   pluginName = "Mapillary"
//   debugPort = 7051
//   i18n {
//     pathTransformer = getPathTransformer(project.projectDir, "gitlab.com/JOSM/plugin/Mapillary/blob")
//   }
// }

eclipse {
  project {
    name = "JOSM-Mapillary"
    // comment = josm.manifest.description
    natures("org.sonarlint.eclipse.core.sonarlintNature", "ch.acanda.eclipse.pmd.builder.PMDNature", "org.eclipse.buildship.core.gradleprojectnature")
    buildCommand("org.sonarlint.eclipse.core.sonarlintBuilder")
    buildCommand("ch.acanda.eclipse.pmd.builder.PMDBuilder")
    buildCommand("org.eclipse.buildship.core.gradleprojectbuilder")
  }
}
tasks["eclipseClasspath"].dependsOn("cleanEclipseClasspath")
tasks["eclipseProject"].dependsOn("cleanEclipseProject")
tasks["eclipse"].setDependsOn(setOf("eclipseClasspath", "eclipseProject"))

tasks.withType(JavaCompile::class) {
  // Character encoding of Java files
  options.encoding = "UTF-8"
}
tasks.withType(Javadoc::class) {
  isFailOnError = false
}

tasks.withType(Test::class).getByName("test") {
  project.afterEvaluate {
    jvmArgs("-javaagent:${classpath.find { it.name.contains("jmockit") }!!.absolutePath}")
    jvmArgs("-Djunit.jupiter.extensions.autodetection.enabled=true")
    jvmArgs("-Djava.awt.headless=true")
  }
  useJUnitPlatform()
  testLogging {
    exceptionFormat = TestExceptionFormat.FULL
    events = setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
    showCauses = true

    info {
      events = setOf(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED, TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR)
      showStandardStreams = true
    }
  }
}

// project.afterEvaluate {
//   publishing.publications.getByName("josmPlugin", MavenPublication::class) {
//     pom {
//       name.set("JOSM-Mapillary")
//       description.set("The Mapillary plugin for JOSM")
//       url.set("https://gitlab.com/JOSM/plugin/Mapillary")
//       licenses {
//         license {
//           name.set("GNU General Public License Version 2")
//           url.set("https://www.gnu.org/licenses/old-licenses/gpl-2.0")
//         }
//       }
//       scm {
//         connection.set("scm:git:git://gitlab.com/JOSM/plugin/Mapillary.git")
//         developerConnection.set("scm:git:ssh://gitlab.com/JOSM/plugin/Mapillary.git")
//         url.set("https://gitlab.com/JOSM/plugin/Mapillary")
//       }
//       issueManagement {
//         system.set("Trac")
//         url.set("https://josm.openstreetmap.de/query?component=Plugin+mapillary&status=assigned&status=needinfo&status=new&status=reopened")
//       }
//     }
//   }
// }

// Spotbugs config
spotbugs {
  toolVersion.set("4.0.6")
  ignoreFailures.set(true)
  effort.set(Effort.MAX)
  reportLevel.set(Confidence.LOW)
  reportsDir.set(File(buildDir, "reports/spotbugs"))
}
tasks.withType(SpotBugsTask::class) {
  reports.create("html") {
    outputLocation.set(File(spotbugs.reportsDir.get().asFile, "$baseName.html"))
    setStylesheet("color.xsl")
  }
}


// JaCoCo config
jacoco {
  toolVersion = "0.8.10"
}

tasks.jacocoTestReport {
  reports {
    xml.required.set(true)
    html.outputLocation.set(file("$buildDir/reports/jacoco"))
  }
}

tasks.test {
  extensions.configure(JacocoTaskExtension::class) {
    // We need to excluse ObjectDetections from coverage -- it is too large for instrumentation, which means that it will always have 0% coverage, despite having tests.
    excludes = listOf("org/openstreetmap/josm/plugins/mapillary/data/mapillary/ObjectDetections.class")
  }
}

// PMD config
pmd {
  toolVersion = versions.getValue("pmd")
  isIgnoreFailures = true
  ruleSetConfig = resources.text.fromFile("$projectDir/config/pmd/ruleset.xml")
  ruleSets = listOf()
  sourceSets = listOf(project.sourceSets.main.get(), project.sourceSets.test.get())
}

// SonarQube config
sonarqube {
  properties {
    property("sonar.forceAuthentication", "true")
    property("sonar.host.url", "https://sonarcloud.io")
    property("sonar.projectKey", "Mapillary")
    property("sonar.organization", "josm")
    property("sonar.projectVersion", project.version)
    //property("sonar.projectDescription", "Allows the user to work with pictures hosted at mapillary.com")
    findProperty("plugin.description")?.let { property("sonar.projectDescription", it) }
    property("sonar.sources", listOf("src"))
  }
}
