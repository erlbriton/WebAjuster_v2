import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // Поддержка обычного JS
    js {
        browser()
        binaries.executable()
    }

    // Поддержка Wasm
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
        }

        // ДОБАВЛЯЕМ ЭТУ СЕКЦИЮ:
        val wasmJsMain by getting {
            dependencies {
                // Это добавит те самые document и window
                implementation(kotlin("stdlib-wasm-js"))
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}