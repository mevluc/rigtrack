plugins { alias(libs.plugins.android.application); alias(libs.plugins.compose.compiler) }
// Windows JVM test workers can misdecode non-ASCII classpaths. Keep generated outputs ASCII.
if (System.getProperty("os.name").startsWith("Windows") && rootDir.path.any { it.code > 127 }) {
    layout.buildDirectory.set(file("${System.getProperty("java.io.tmpdir")}/RigTrack-build/app"))
}
android {
    namespace = "com.rigtrack"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.rigtrack"
        minSdk = 24
        targetSdk = 37
        versionCode = 5
        versionName = "1.3.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true; compose = true }
    bundle { language { enableSplit = false } }
    sourceSets.getByName("test").resources.srcDirs("src/main/assets", "src/main/res")
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "META-INF/DEPENDENCIES" }
}
dependencies {
    implementation(libs.arcore)
    implementation(libs.gson)
    implementation(libs.coroutines)
    implementation(libs.opencv)
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation(libs.junit)
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
tasks.withType<Test>().configureEach {
    providers.gradleProperty("fixtureOutput").orNull?.let { systemProperty("rigtrack.fixture", it) }
    providers.gradleProperty("fixtureOutputWithVideo").orNull?.let { systemProperty("rigtrack.fixture.withvideo", it) }
}
