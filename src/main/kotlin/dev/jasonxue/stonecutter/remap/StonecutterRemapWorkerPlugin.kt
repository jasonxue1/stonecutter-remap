/*
 * Copyright (C) 2026  stonecutter-remap contributors
 * https://github.com/jasonxue1/stonecutter-remap

 * stonecutter-remap is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * stonecutter-remap is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with stonecutter-remap.  If not, see <https://www.gnu.org/licenses/>.
 */
package dev.jasonxue.stonecutter.remap

import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the

class StonecutterRemapWorkerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.requireStonecutter()
        project.pluginManager.apply("java")

        val extension = project.extensions.create<StonecutterRemapExtension>("stonecutterRemap")
        extension.baseVersion.convention(project.providers.gradleProperty("baseVersion").orElse("1.0"))
        val stonecutter = project.extensions.getByType(StonecutterBuildExtension::class.java)
        val currentVersion = project.name

        project.the<SourceSetContainer>().named("main") {
            if (currentVersion == extension.baseVersion.get()) {
                java.setSrcDirs(listOf(project.rootProject.file("src/main/java")))
            } else {
                java.setSrcDirs(listOf(project.layout.buildDirectory.dir("remapped/main")))
            }
        }

        val remapTask =
            project.tasks.register<RemapSourcesTask>("stonecutterRemapSources") {
                group = "build"
                description = "Runs ReplayMod remap on Stonecutter-generated sources"
                inputDir.set(stonecutter.tasks.generatedSourcesDir.dir("main"))
                outputDir.set(project.layout.buildDirectory.dir("remapped/main"))
                dependsOn(stonecutter.tasks.generate.getOrThrow("main"))
            }

        project.afterEvaluate {
            val baseVersion = extension.baseVersion.get()
            val currentSourceSets = project.the<SourceSetContainer>()
            val currentApi = currentSourceSets.named("main").get().compileClasspath

            if (currentVersion == baseVersion) {
                tasks.named("compileJava")
                tasks.register("showRemappedSource") {
                    group = "demo"
                    description = "Prints the base-version App.java from root sources"
                    doLast {
                        val sourceFile = project.rootProject.file("src/main/java/demo/App.java")
                        println("===== ${project.path} =====")
                        println(sourceFile.readText())
                    }
                }
                return@afterEvaluate
            }

            val baseProject = project.rootProject.project(":$baseVersion")
            val baseApi =
                baseProject
                    .the<SourceSetContainer>()
                    .named("main")
                    .get()
                    .compileClasspath

            remapTask.configure {
                classpathFiles.from(baseApi)
                remappedClasspathFiles.from(currentApi)
                manageImports.set(extension.manageImports)
                enableMessageCollector.set(extension.enableMessageCollector)
                mappingSteps.set(resolveMappingSteps(baseVersion, currentVersion, extension.links))
            }

            tasks.named("compileJava") {
                dependsOn(remapTask)
            }

            tasks.register("showRemappedSource") {
                group = "demo"
                description = "Prints the final remapped App.java for this version"
                dependsOn(remapTask)
                doLast {
                    val sourceFile =
                        layout.buildDirectory
                            .file("remapped/main/main/java/demo/App.java")
                            .get()
                            .asFile
                    println("===== ${project.path} =====")
                    println(sourceFile.readText())
                }
            }
        }
    }

    private fun resolveMappingSteps(
        baseVersion: String,
        targetVersion: String,
        links: List<LinkSpec>,
    ): List<MappingStep> {
        if (baseVersion == targetVersion) return emptyList()

        data class Step(
            val version: String,
            val steps: List<MappingStep>,
        )

        val queue = ArrayDeque<Step>()
        val visited = mutableSetOf<String>()
        queue += Step(baseVersion, emptyList())
        visited += baseVersion

        while (queue.isNotEmpty()) {
            val (version, steps) = queue.removeFirst()
            if (version == targetVersion) return steps

            for (link in links) {
                if (link.from == version && visited.add(link.to)) {
                    queue += Step(link.to, link.appendMappingStep(steps, reverse = false))
                }
                if (link.to == version && visited.add(link.from)) {
                    queue += Step(link.from, link.appendMappingStep(steps, reverse = true))
                }
            }
        }
        error("Cannot find mapping path from $baseVersion to $targetVersion")
    }
}
