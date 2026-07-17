plugins {
    id("com.android.library")
}

android {
    namespace = "com.liskovsoft.immichserviceinterfaces"
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
}

dependencies {
    compileOnly("com.liskovsoft.smarttubetv:smarttube:latest")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("androidx.annotation:annotation:1.1.0")
}
