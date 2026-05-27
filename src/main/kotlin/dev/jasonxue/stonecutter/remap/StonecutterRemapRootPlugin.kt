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

import dev.kikugie.stonecutter.controller.StonecutterControllerExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

class StonecutterRemapRootPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.requireStonecutter()
        val extension = project.extensions.create<StonecutterRemapExtension>("stonecutterRemap")
        extension.baseVersion.convention(project.providers.gradleProperty("baseVersion").orElse("1.0"))
        val stonecutter = project.extensions.getByType(StonecutterControllerExtension::class.java)

        stonecutter.tasks.switch.values.forEach { taskProvider ->
            taskProvider.configure { enabled = false }
        }

        project.afterEvaluate {
            project.subprojects.forEach { subproject ->
                subproject.pluginManager.apply("dev.jasonxue.stonecutter.remap.worker")
                subproject.extensions.getByType(StonecutterRemapExtension::class.java).apply {
                    this.baseVersion.set(extension.baseVersion.get())
                    manageImports.set(extension.manageImports.get())
                    enableMessageCollector.set(extension.enableMessageCollector.get())
                    links.addAll(extension.links)
                }
            }
        }
    }
}
