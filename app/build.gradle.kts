import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val appleMusicProps = Properties().apply {
    val propsFile = rootProject.file("apple_music.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

fun prop(name: String, default: String = ""): String =
    appleMusicProps.getProperty(name, default).trim()

fun escapeBuildConfig(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

/**
 * Firma el Developer Token (ES256) AL COMPILAR. Dentro del APK solo va el token (caduca en 180 días),
 * nunca la llave privada .p8. Para renovarlo basta con volver a compilar.
 */
fun signDeveloperToken(teamId: String, keyId: String, pem: String): String {
    fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    val now = System.currentTimeMillis() / 1000
    val header = b64("""{"alg":"ES256","kid":"$keyId"}""".toByteArray())
    val payload = b64("""{"iss":"$teamId","iat":$now,"exp":${now + 15_552_000}}""".toByteArray())
    val keyBytes = Base64.getDecoder().decode(
        pem.replace(Regex("-----[A-Z ]+-----"), "").replace(Regex("\\s"), "")
    )
    val key = KeyFactory.getInstance("EC")
        .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    val der = Signature.getInstance("SHA256withECDSA").run {
        initSign(key); update("$header.$payload".toByteArray()); sign()
    }
    // DER (SEQUENCE{INTEGER r, INTEGER s}) -> r||s de 32 bytes cada uno
    var i = if ((der[1].toInt() and 0x80) != 0) 2 + (der[1].toInt() and 0x7f) else 2
    fun readInt(): ByteArray {
        val len = der[i + 1].toInt() and 0xff
        val v = der.copyOfRange(i + 2, i + 2 + len)
        i += 2 + len
        val trimmed = v.dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(32 - trimmed.size) + trimmed
    }
    val r = readInt()
    val s = readInt()
    return "$header.$payload.${b64(r + s)}"
}

val p8Content: String = run {
    val path = prop("APPLE_P8_PATH")
    if (path.isNotBlank()) {
        val candidate = file(path).takeIf { it.exists() } ?: rootProject.file(path).takeIf { it.exists() }
        candidate?.readText()?.trim() ?: ""
    } else {
        ""
    }
}

android {
    namespace = "com.applemusic.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.applemusic.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Solo el token firmado entra al APK (nunca el .p8)
        val developerToken = prop("APPLE_DEVELOPER_TOKEN").ifBlank {
            if (prop("APPLE_TEAM_ID").isNotBlank() && prop("APPLE_KEY_ID").isNotBlank() && p8Content.isNotBlank()) {
                signDeveloperToken(prop("APPLE_TEAM_ID"), prop("APPLE_KEY_ID"), p8Content)
            } else ""
        }
        buildConfigField("String", "APPLE_DEVELOPER_TOKEN", "\"${escapeBuildConfig(developerToken)}\"")
        buildConfigField("String", "APPLE_MUSIC_USER_TOKEN", "\"${escapeBuildConfig(prop("APPLE_MUSIC_USER_TOKEN"))}\"")
        buildConfigField("String", "APPLE_STOREFRONT", "\"${escapeBuildConfig(prop("APPLE_STOREFRONT", "mx"))}\"")
        // ID de la app receptora de Chromecast (Google Cast SDK Developer Console). Vacío = Default Media Receiver.
        buildConfigField("String", "CAST_RECEIVER_APP_ID", "\"${escapeBuildConfig(prop("CAST_RECEIVER_APP_ID"))}\"")
    }

    signingConfigs {
        val ksPropsFile = rootProject.file("keystore.properties")
        if (ksPropsFile.exists()) {
            val ksProps = Properties().apply { ksPropsFile.inputStream().use { load(it) } }
            val storePath = ksProps.getProperty("storeFile", "").trim()
            val storeFileObj = rootProject.file(storePath)
            if (storePath.isNotBlank() && storeFileObj.exists()) {
                create("release") {
                    storeFile = storeFileObj
                    storePassword = ksProps.getProperty("storePassword", "")
                    keyAlias = ksProps.getProperty("keyAlias", "")
                    keyPassword = ksProps.getProperty("keyPassword", "")
                    enableV1Signing = true
                    enableV2Signing = true
                    enableV3Signing = true
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.haze)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.play.services.cast.framework)
    implementation(libs.androidx.mediarouter)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
