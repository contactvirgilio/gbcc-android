@file:Suppress("UnstableApiUsage")

plugins {
	id("com.android.application") apply true
}

base {
	archivesName.set("gbcc")
}

android {
	compileSdk = 37
	defaultConfig {
		applicationId = "com.philj56.gbcc"
		// AndroidX and Material Components now require API 23 or higher
		minSdk = 23
		targetSdk = 36
		versionCode = 41
		versionName = "beta41"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		externalNativeBuild {
			cmake {
				targets.add("gbcc")
			}
		}
		ndk {
			debugSymbolLevel = "FULL"
		}
	}
	splits {
		abi {
			isEnable = true
			// Only ABIs supported by current NDKs (armeabi and mips were removed)
			reset()
			include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
			isUniversalApk = true
		}
	}
	buildTypes {
		release {
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
			isMinifyEnabled = true
			isShrinkResources = true
		}
		debug {
			isMinifyEnabled = false
			applicationIdSuffix = ".debug"
		}
	}
	buildFeatures {
		viewBinding = true
		buildConfig = true
	}
	externalNativeBuild {
		cmake {
			path("src/main/cpp/CMakeLists.txt")
		}
	}
	
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	// NDK r28+ builds 16 KB page-aligned libraries, required on Android 15+ devices
	ndkVersion = "28.2.13676358"
	namespace = "com.philj56.gbcc"
}

dependencies {
	implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
	implementation("androidx.camera:camera-core:1.6.2")
	implementation("androidx.camera:camera-camera2:1.6.2")
	implementation("androidx.camera:camera-lifecycle:1.6.2")
	implementation("androidx.core:core-ktx:1.19.1")
	implementation("androidx.activity:activity-ktx:1.13.0")
	implementation("androidx.appcompat:appcompat:1.8.0")
	implementation("androidx.constraintlayout:constraintlayout:2.2.2")
	implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
	implementation("androidx.fragment:fragment-ktx:1.9.1")
	implementation("androidx.preference:preference-ktx:1.2.1")
	implementation("androidx.recyclerview:recyclerview:1.4.0")
	implementation("com.google.android.material:material:1.14.0")

	testImplementation("junit:junit:4.13.2")
	androidTestImplementation("androidx.test:runner:1.7.0")
	androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

tasks.register<Copy>("copyAssets") {
	from("libs/gbcc/tileset.png")
	from("libs/gbcc/camera.png")
	into("src/main/assets")
}

tasks.register<Copy>("copyResources") {
	from("libs/gbcc/print.wav")
	into("src/main/res/raw")
}

tasks["preBuild"].dependsOn("copyAssets")
tasks["preBuild"].dependsOn("copyResources")
