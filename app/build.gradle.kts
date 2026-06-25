plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.appname.dqra"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.appname.dqra"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.volley)
    implementation(libs.firebase.functions)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.database)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.osmdroid)
    implementation(libs.play.services.location)
    implementation(libs.okhttp)
    runtimeOnly("androidx.cardview:cardview:1.0.0")
    implementation("com.google.maps.android:android-maps-utils:3.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.firebaseui:firebase-ui-firestore:8.0.2")
    implementation ("androidx.work:work-runtime:2.7.1")
    implementation ("androidx.concurrent:concurrent-futures:1.1.0")
    implementation ("com.google.guava:guava:31.0.1-android")
    implementation ("com.google.android.material:material:1.11.0")
    implementation ("com.google.android.gms:play-services-auth:21.2.0")
    implementation ("com.google.firebase:firebase-auth:23.0.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation ("com.google.firebase:firebase-auth-ktx:22.3.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.android.material:material:1.9.0")
}
//plugins {
//    alias(libs.plugins.android.application)
//    alias(libs.plugins.google.gms.google. services)
//
//}
//
//android {
//    namespace = "com.appname.dqra"
//    compileSdk = 35
//
//    defaultConfig {
//        applicationId = "com.appname.dqra"
//        minSdk = 24
//        targetSdk = 35
//        versionCode = 1
//        versionName = "1.0"
//
//        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
//    }
//
//    buildTypes {
//        release {
//            isMinifyEnabled = false
//            proguardFiles(
//                getDefaultProguardFile("proguard-android-optimize.txt"),
//                "proguard-rules.pro"
//            )
//        }
//    }
//    compileOptions {
//        sourceCompatibility = JavaVersion.VERSION_11
//        targetCompatibility = JavaVersion.VERSION_11
//    }
//}
//
//dependencies {
//    implementation(libs.volley)
//    implementation(libs.firebase.functions)
//    implementation(libs.appcompat)
//    implementation(libs.material)
//    implementation(libs.activity)
//    implementation(libs.constraintlayout)
//    implementation(libs.firebase.firestore)
//    implementation(libs.firebase.auth)
//    implementation(libs.firebase.messaging)
//    implementation(libs.firebase.database)
//    testImplementation(libs.junit)
//    androidTestImplementation(libs.ext.junit)
//    androidTestImplementation(libs.espresso.core)
//    implementation(libs.osmdroid)
//    implementation(libs.play.services.location)
//    implementation(libs.okhttp)
//    runtimeOnly("androidx.cardview:cardview:1.0.0")
//    implementation("com.google.maps.android:android-maps-utils:3.9.0")
//    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
//    implementation ("com.firebaseui:firebase-ui-firestore:8.0.2")
//    implementation ("com.google.firebase:firebase-admin:
//
//}
