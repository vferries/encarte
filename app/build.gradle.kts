import com.android.build.api.artifact.SingleArtifact
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "io.github.vferries.encarte"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.vferries.encarte"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
        // Robolectric on JDK 17+: https://robolectric.org/getting-started/
        unitTests.all {
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            )
            // DemoCardsTest reads the store-screenshot wallet: editing it must re-run the tests.
            it.inputs.file(rootProject.file("branding/demo/cards.csv"))
        }
    }
}

kotlin {
    jvmToolchain(21)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)

    implementation(libs.zxing.core)
    implementation(libs.zxing.cpp.android)
    implementation(libs.androidx.biometric)
    // biometric 1.1.0 drags fragment 1.2.5, whose FragmentActivity rejects Activity Result API request codes.
    implementation(libs.androidx.fragment)
    implementation(libs.zip4j)
    implementation(libs.commons.csv)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // ui-test pulls espresso-core 3.5.0, which reflects on InputManager.getInstance() (gone on SDK 37).
    testImplementation(libs.androidx.test.espresso.core)
}

/**
 * Fails if the merged release manifest still declares a network permission.
 * Guards the "offline only" promise against transitive dependencies sneaking one in.
 */
abstract class VerifyNoNetworkPermissionTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val forbiddenPermissions: SetProperty<String>

    @TaskAction
    fun verify() {
        val androidNs = "http://schemas.android.com/apk/res/android"
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(mergedManifest.get().asFile)
        val declared = listOf("uses-permission", "uses-permission-sdk-23").flatMap { tag ->
            val nodes = document.getElementsByTagName(tag)
            (0 until nodes.length).map { i ->
                (nodes.item(i) as Element).getAttributeNS(androidNs, "name")
            }
        }
        val offending = declared.filter { it in forbiddenPermissions.get() }
        if (offending.isNotEmpty()) {
            throw GradleException(
                "Merged manifest ${mergedManifest.get().asFile} declares forbidden permissions: $offending"
            )
        }
        logger.lifecycle("OK: no network permission among $declared in ${mergedManifest.get().asFile}")
    }
}

val verifyNoNetworkPermission = tasks.register<VerifyNoNetworkPermissionTask>("verifyNoNetworkPermission") {
    group = "verification"
    description = "Fails if the merged release manifest declares INTERNET or ACCESS_NETWORK_STATE."
    forbiddenPermissions.set(
        setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE")
    )
}

androidComponents {
    // Robolectric reads the tested variant's merged assets, and Room's plugin only feeds schemas to
    // androidTest. Exposing them to debug lets MigrationTestHelper run in unit tests (never in release).
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.sources.assets?.addStaticSourceDirectory("$projectDir/schemas")
    }
    onVariants(selector().withBuildType("release")) { variant ->
        verifyNoNetworkPermission.configure {
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
    }
}
