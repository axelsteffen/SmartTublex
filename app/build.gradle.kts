plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val androidSdk: String =
    System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: "${System.getProperty("user.home")}/Library/Android/sdk"

val buildToolsDir = file("$androidSdk/build-tools").listFiles()
    ?.filter { it.isDirectory }
    ?.maxByOrNull { it.name }
    ?: error("No Android build-tools under $androidSdk")

val d8 = file("$buildToolsDir/d8")
val zipalign = file("$buildToolsDir/zipalign")
val apksigner = file("$buildToolsDir/apksigner")
val androidJar = file("$androidSdk/platforms/android-34/android.jar")

val smarttubeVersion = "latest"
val smarttubeJar = file(
    "${System.getProperty("user.home")}/.m2/repository/com/liskovsoft/smarttubetv/smarttube/" +
        "$smarttubeVersion/smarttube-$smarttubeVersion.jar"
)
val smarttubeApk = file(
    "${System.getProperty("user.home")}/.m2/repository/com/liskovsoft/smarttubetv/smarttube/" +
        "$smarttubeVersion/smarttube-$smarttubeVersion-apk.apk"
)

/**
 * Gradle daemons often omit Homebrew from PATH; resolve tools by absolute path.
 */
fun resolveExecutable(name: String, vararg extraDirs: String): File {
    val envOverride = System.getenv(name.uppercase())
    val candidates = buildList {
        if (!envOverride.isNullOrBlank()) add(file(envOverride))
        val pathDirs = (System.getenv("PATH") ?: "").split(File.pathSeparator)
        for (dir in pathDirs) {
            if (dir.isNotBlank()) add(file("$dir/$name"))
        }
        add(file("/opt/homebrew/bin/$name"))
        add(file("/usr/local/bin/$name"))
        for (dir in extraDirs) {
            add(file("$dir/$name"))
        }
    }
    return candidates.firstOrNull { it.isFile && it.canExecute() }
        ?: error(
            "Missing executable '$name' (set ${name.uppercase()}=… or install via Homebrew). " +
                "Searched PATH plus /opt/homebrew/bin and /usr/local/bin."
        )
}

val apktoolExe = resolveExecutable("apktool")

android {
    namespace = "de.developerleipzig.smarttublex"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.developerleipzig.smarttublex"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources.excludes += setOf("**/*.kotlin_builtins", "META-INF/**")
    }
}

dependencies {
    // Fat JAR from apk2maven (DEX→JAR). Upstream types for wrappers; not packaged by AGP.
    compileOnly("com.liskovsoft.smarttubetv:smarttube:$smarttubeVersion")
    // Official AARs so IDE/Kotlin can resolve AndroidX supertypes of SplashActivity /
    // MainApplication (fat JAR alone often yields "Cannot access KeyEventDispatcher$Component").
    // Runtime AndroidX remains in the wrapped SmartTube APK (packageWrapperApk).
    compileOnly("androidx.core:core:1.13.1")
    compileOnly("androidx.activity:activity:1.9.2")
    compileOnly("androidx.fragment:fragment:1.8.5")
    compileOnly("androidx.appcompat:appcompat:1.7.0")
    compileOnly("androidx.lifecycle:lifecycle-runtime:2.8.7")
    compileOnly("androidx.multidex:multidex:2.0.1")
    implementation(project(":plexapi"))
    implementation(project(":immichapi"))
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
}

// Do not depend on :apk-base:installApkArtifact in the same Gradle invocation as AGP —
// AGP's ASM breaks dex2jar. Refresh the artifact via `./gradlew :apk-base:installApkArtifact` (sync).
tasks.named("preBuild") {
    doFirst {
        check(smarttubeJar.isFile) {
            "Missing $smarttubeJar — run: ./gradlew :apk-base:installApkArtifact"
        }
    }
}

val packageWrapperApk by tasks.registering {
    group = "build"
    description = "Patch upstream SmartTube APK with SmartTublex Application/Splash, branding, and sign debug APK"
    dependsOn(
        "compileDebugKotlin",
        "compileDebugJavaWithJavac",
        "processDebugResources",
        ":plexapi:bundleLibRuntimeToJarDebug",
        ":plexserviceinterfaces:bundleLibRuntimeToJarDebug",
        ":immichapi:bundleLibRuntimeToJarDebug",
        ":immichserviceinterfaces:bundleLibRuntimeToJarDebug"
    )

    val outDir = layout.buildDirectory.dir("outputs/apk/debug")
    val workDir = layout.buildDirectory.dir("wrapper-apk")
    outputs.file(outDir.map { it.file("app-debug.apk") })

    doLast {
        check(smarttubeApk.isFile) { "Missing base APK: $smarttubeApk — run :apk-base:installApkArtifact" }
        check(smarttubeJar.isFile) { "Missing base JAR: $smarttubeJar" }
        check(androidJar.isFile) { "Missing android.jar: $androidJar" }
        check(d8.isFile) { "Missing d8: $d8" }

        val work = workDir.get().asFile
        work.deleteRecursively()
        work.mkdirs()

        val classesDir = layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile
        check(classesDir.isDirectory) { "Missing compiled classes: $classesDir" }
        // Java sources (e.g. SidebarServiceBridge) land under javac/, not kotlin-classes/.
        val javaClassesDir = layout.buildDirectory
            .dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")
            .get().asFile

        fun runtimeLibJar(projectName: String): java.io.File {
            val jar = project(projectName).layout.buildDirectory
                .file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar")
                .get().asFile
            check(jar.isFile) { "Missing $projectName runtime jar: $jar" }
            return jar
        }
        val plexApiJar = runtimeLibJar(":plexapi")
        val plexIfJar = runtimeLibJar(":plexserviceinterfaces")
        val immichApiJar = runtimeLibJar(":immichapi")
        val immichIfJar = runtimeLibJar(":immichserviceinterfaces")

        val mergeDir = work.resolve("merge-classes")
        mergeDir.mkdirs()
        project.exec {
            commandLine("cp", "-R", "${classesDir.absolutePath}/.", mergeDir.absolutePath)
        }
        if (javaClassesDir.isDirectory) {
            project.exec {
                commandLine("cp", "-R", "${javaClassesDir.absolutePath}/.", mergeDir.absolutePath)
            }
        }
        check(
            mergeDir.resolve("de/developerleipzig/smarttublex/browse/SidebarServiceBridge.class").isFile
        ) {
            "Missing SidebarServiceBridge.class in merge — Java compile output not packed"
        }
        for (libJar in listOf(plexIfJar, plexApiJar, immichIfJar, immichApiJar)) {
            project.exec {
                workingDir = mergeDir
                commandLine("jar", "xf", libJar.absolutePath)
            }
        }

        val wrapperJar = work.resolve("wrapper-classes.jar")
        project.exec {
            commandLine("jar", "cf", wrapperJar.absolutePath, "-C", mergeDir.absolutePath, ".")
        }
        check(wrapperJar.isFile) { "Failed to jar wrapper classes" }

        val dexDir = work.resolve("dex")
        dexDir.mkdirs()
        project.exec {
            commandLine(
                d8.absolutePath,
                "--lib", androidJar.absolutePath,
                "--classpath", smarttubeJar.absolutePath,
                "--output", dexDir.absolutePath,
                "--min-api", "21",
                wrapperJar.absolutePath
            )
        }
        val producedDex = dexDir.resolve("classes.dex")
        check(producedDex.isFile) { "d8 did not produce classes.dex" }

        val decoded = work.resolve("decoded")
        // Single-threaded decode avoids apktool 3.x races writing values-*/ XML under -j > 1
        project.exec {
            commandLine(
                apktoolExe.absolutePath, "d",
                "-j", "1",
                smarttubeApk.absolutePath,
                "-o", decoded.absolutePath,
                "-f"
            )
        }

        val manifest = decoded.resolve("AndroidManifest.xml")
        var xml = manifest.readText()
        xml = xml.replace(
            """android:name="com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication"""",
            """android:name="de.developerleipzig.smarttublex.SmartTublexApplication""""
        )
        // Rename the activity declaration and any activity-alias targetActivity refs.
        // replaceFirst alone left SplashActivityAlt targeting the old class → INSTALL_PARSE_FAILED_MANIFEST_MALFORMED.
        xml = xml.replace(
            """android:name="com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity"""",
            """android:name="de.developerleipzig.smarttublex.SmartTublexSplashActivity""""
        )
        xml = xml.replace(
            """android:targetActivity="com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity"""",
            """android:targetActivity="de.developerleipzig.smarttublex.SmartTublexSplashActivity""""
        )
        check(xml.contains("de.developerleipzig.smarttublex.SmartTublexApplication")) {
            "Failed to patch Application class in AndroidManifest.xml"
        }
        check(xml.contains("de.developerleipzig.smarttublex.SmartTublexSplashActivity")) {
            "Failed to patch SplashActivity in AndroidManifest.xml"
        }
        check(!xml.contains("""android:targetActivity="com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity"""")) {
            "activity-alias still targets upstream SplashActivity"
        }
        manifest.writeText(xml)

        // SmartTublex branding: overwrite upstream mipmaps + display name
        val brandingDir = rootProject.projectDir.resolve("images/logo/generated")
        check(brandingDir.isDirectory) { "Missing branding dir: $brandingDir — run images/logo/generate_branding.py" }
        val nodpi = decoded.resolve("res/mipmap-nodpi")
        check(nodpi.isDirectory) { "Missing decoded mipmap-nodpi: $nodpi" }
        val brandingFiles = listOf(
            "app_icon.png",
            "app_icon_alt.png",
            "app_banner.png",
            "app_logo.png",
            "app_logo_semi_red.png",
            "app_logo_semi_grey.png",
        )
        for (name in brandingFiles) {
            val src = brandingDir.resolve(name)
            check(src.isFile) { "Missing branding asset: $src" }
            src.copyTo(nodpi.resolve(name), overwrite = true)
        }
        val launcherDensities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi")
        for (density in launcherDensities) {
            val src = brandingDir.resolve("ic_launcher_$density.png")
            val dest = decoded.resolve("res/mipmap-$density/ic_launcher.png")
            if (src.isFile && dest.parentFile?.isDirectory == true) {
                src.copyTo(dest, overwrite = true)
            }
        }

        // Plex "All Movies" / "All TV Shows" library browse card thumbnails
        val thumbnailDir = rootProject.projectDir.resolve("images/thumbnails/generated")
        check(thumbnailDir.isDirectory) {
            "Missing thumbnail dir: $thumbnailDir — run images/thumbnails/generate_thumbnails.py"
        }
        val drawableNodpi = decoded.resolve("res/drawable-nodpi")
        drawableNodpi.mkdirs()
        for (name in listOf("all_movies.png", "all_tv_shows.png")) {
            val src = thumbnailDir.resolve(name)
            check(src.isFile) { "Missing thumbnail asset: $src" }
            src.copyTo(drawableNodpi.resolve(name), overwrite = true)
        }

        val stringsFile = decoded.resolve("res/values/strings.xml")
        check(stringsFile.isFile) { "Missing decoded strings.xml: $stringsFile" }
        var stringsXml = stringsFile.readText()
        fun replaceStringResource(name: String, value: String) {
            val pattern = Regex("""(<string\s+name="$name">)[^<]*(</string>)""")
            check(pattern.containsMatchIn(stringsXml)) {
                "string resource '$name' not found in strings.xml"
            }
            stringsXml = pattern.replace(stringsXml, "$1$value$2")
        }
        replaceStringResource("app_name", "SmartTublex")
        if (Regex("""<string\s+name="browse_title">""").containsMatchIn(stringsXml)) {
            replaceStringResource("browse_title", "SmartTublex")
        }
        stringsFile.writeText(stringsXml)

        producedDex.copyTo(decoded.resolve("classes4.dex"), overwrite = true)

        val unsigned = work.resolve("unsigned.apk")
        project.exec {
            commandLine(
                apktoolExe.absolutePath, "b",
                decoded.absolutePath,
                "-o", unsigned.absolutePath
            )
        }

        val aligned = work.resolve("aligned.apk")
        project.exec {
            commandLine(
                zipalign.absolutePath,
                "-f", "4",
                unsigned.absolutePath,
                aligned.absolutePath
            )
        }

        val debugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
        if (!debugKeystore.isFile) {
            project.exec {
                commandLine(
                    "keytool", "-genkeypair",
                    "-keystore", debugKeystore.absolutePath,
                    "-storepass", "android",
                    "-alias", "androiddebugkey",
                    "-keypass", "android",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-validity", "10000",
                    "-dname", "CN=Android Debug,O=Android,C=US"
                )
            }
        }

        val outApkDir = outDir.get().asFile
        outApkDir.mkdirs()
        val signed = outApkDir.resolve("app-debug.apk")
        if (signed.exists()) signed.delete()
        aligned.copyTo(signed)

        project.exec {
            commandLine(
                apksigner.absolutePath,
                "sign",
                "--ks", debugKeystore.absolutePath,
                "--ks-pass", "pass:android",
                "--key-pass", "pass:android",
                "--ks-key-alias", "androiddebugkey",
                signed.absolutePath
            )
        }

        project.exec {
            commandLine(apksigner.absolutePath, "verify", signed.absolutePath)
        }

        // AGP installDebug / Android Studio Run use intermediates, not outputs/.
        // Without this overwrite, the device gets the ~2MB stub (applicationId
        // de.developerleipzig.smarttublex) that cannot resolve MainApplication → ClassNotFoundException.
        val agpInstallApk = layout.buildDirectory
            .file("intermediates/apk/debug/app-debug.apk")
            .get().asFile
        agpInstallApk.parentFile?.mkdirs()
        signed.copyTo(agpInstallApk, overwrite = true)

        logger.lifecycle("TV debug APK: ${signed.absolutePath}")
        logger.lifecycle(
            "Also wrote AGP install path: ${agpInstallApk.absolutePath} " +
                "(package org.smarttube.beta — not de.developerleipzig.smarttublex)"
        )
    }
}

afterEvaluate {
    tasks.named("assembleDebug").configure {
        dependsOn(packageWrapperApk)
    }
    tasks.named("packageDebug").configure {
        finalizedBy(packageWrapperApk)
    }
    // Ensure Run/Debug and :app:installDebug never install the AGP stub APK.
    tasks.findByName("installDebug")?.dependsOn(packageWrapperApk)
}
