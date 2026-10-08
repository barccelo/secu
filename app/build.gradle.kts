import java.util.Properties

plugins { id("com.android.application") }

val ciVersionCode = System.getenv("SECU_VERSION_CODE")?.toIntOrNull() ?: 5
val signingPropertiesPath = System.getenv("SECU_SIGNING_PROPERTIES")
val signingKeystorePath = System.getenv("SECU_KEYSTORE_PATH")
val signingProperties = Properties()
val persistentSigningAvailable = !signingPropertiesPath.isNullOrBlank() && !signingKeystorePath.isNullOrBlank() && file(signingPropertiesPath).isFile && file(signingKeystorePath).isFile
if (persistentSigningAvailable) file(signingPropertiesPath!!).inputStream().use(signingProperties::load)

android {
    namespace = "com.barccelo.secu"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.barccelo.secu"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = "0.4.1"
    }
    signingConfigs {
        if (persistentSigningAvailable) create("persistent") {
            storeFile = file(signingKeystorePath!!)
            storePassword = signingProperties.getProperty("storePassword")
            keyAlias = signingProperties.getProperty("keyAlias")
            keyPassword = signingProperties.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("debug") { if (persistentSigningAvailable) signingConfig = signingConfigs.getByName("persistent") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
