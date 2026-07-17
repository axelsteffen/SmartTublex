plugins {
    id("com.android.library")
}

android {
    namespace = "de.developerleipzig.plexapi"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main").java.srcDirs("src/main/java")

    // Drop legacy stbeta/ststable/stfdroid flavors from submodule build.gradle
    packaging {
        resources.excludes += setOf("META-INF/**")
    }
}

dependencies {
    compileOnly("com.liskovsoft.smarttubetv:smarttube:latest")
    api(project(":plexserviceinterfaces"))

    implementation("com.squareup.retrofit2:retrofit:2.5.0")
    implementation("com.squareup.retrofit2:converter-gson:2.5.0")
    implementation("com.google.code.gson:gson:2.8.2")
    implementation("com.squareup.okhttp3:okhttp:3.12.13")

    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("androidx.annotation:annotation:1.1.0")
}
