plugins {
    id("de.developer-leipzig.gradle.apk2maven")
}

group = "de.developer-leipzig.smarttublex"
version = "0.1.0"

apk2maven {
    url.set("https://github.com/yuliskov/SmartTube/releases/download/latest/smarttube_beta.apk")
    groupId.set("com.liskovsoft.smarttubetv")
    artifactId.set("smarttube")
    version.set("latest")
}
