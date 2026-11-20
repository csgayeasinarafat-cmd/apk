plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.mylife.agent"; compileSdk = 35
 defaultConfig { applicationId = "com.mylife.agent"; minSdk = 29; targetSdk = 34; versionCode = 13; versionName = "1.3-dev" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
