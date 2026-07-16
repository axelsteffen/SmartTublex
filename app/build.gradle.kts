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
    description = "Patch upstream SmartTube APK with SmartTublex Application/Splash and sign debug APK"
    dependsOn(
        "compileDebugKotlin",
        "processDebugResources",
        ":plexapi:bundleLibRuntimeToJarDebug",
        ":plexserviceinterfaces:bundleLibRuntimeToJarDebug"
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

        val plexApiJar = project(":plexapi").layout.buildDirectory
            .file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar")
            .get().asFile
        val plexIfJar = project(":plexserviceinterfaces").layout.buildDirectory
            .file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar")
            .get().asFile
        check(plexApiJar.isFile) { "Missing plexapi runtime jar: $plexApiJar" }
        check(plexIfJar.isFile) { "Missing plexserviceinterfaces runtime jar: $plexIfJar" }

        val mergeDir = work.resolve("merge-classes")
        mergeDir.mkdirs()
        project.exec {
            commandLine("cp", "-R", "${classesDir.absolutePath}/.", mergeDir.absolutePath)
        }
        project.exec {
            workingDir = mergeDir
            commandLine("jar", "xf", plexIfJar.absolutePath)
        }
        project.exec {
            workingDir = mergeDir
            commandLine("jar", "xf", plexApiJar.absolutePath)
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
                "apktool", "d",
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

        producedDex.copyTo(decoded.resolve("classes4.dex"), overwrite = true)

        val unsigned = work.resolve("unsigned.apk")
        project.exec {
            commandLine("apktool", "b", decoded.absolutePath, "-o", unsigned.absolutePath)
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
