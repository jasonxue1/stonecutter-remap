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

import com.replaymod.gradle.remap.Transformer
import com.replaymod.gradle.remap.legacy.LegacyMapping
import com.replaymod.gradle.remap.legacy.LegacyMappingsReader
import org.cadixdev.lorenz.MappingSet
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

@DisableCachingByDefault(
    because = "ReplayMod remap uses external compiler infrastructure and has not been verified as safely cacheable yet.",
)
abstract class RemapSourcesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classpathFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val remappedClasspathFiles: ConfigurableFileCollection

    @get:Input
    abstract val enableMessageCollector: Property<Boolean>

    @get:Input
    abstract val manageImports: Property<Boolean>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:Input
    abstract val mappingSteps: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val inputRoot = inputDir.get().asFile.toPath()
        val outputRoot = outputDir.get().asFile.toPath()
        val outputRootFile = outputRoot.toFile()
        if (outputRootFile.exists()) {
            outputRootFile.deleteRecursively()
        }
        outputRootFile.mkdirs()

        val sources = linkedMapOf<String, String>()
        Files.walk(inputRoot).use { pathStream ->
            pathStream.forEach { path ->
                if (!path.isRegularFile()) return@forEach
                val name = path.fileName.toString()
                if (!name.endsWith(".java") && !name.endsWith(".kt")) return@forEach
                val relative = path.relativeTo(inputRoot).invariantSeparatorsPathString
                sources[relative] = path.readText()
            }
        }

        val mappingSet =
            if (mappingSteps.get().isNotEmpty()) {
                buildMappingSet(mappingSteps.get())
            } else {
                MappingSet.create()
            }

        val transformer =
            Transformer(mappingSet).apply {
                classpath = classpathFiles.files.map { it.absolutePath }.toTypedArray()
                remappedClasspath = remappedClasspathFiles.files.map { it.absolutePath }.toTypedArray()
                manageImports = this@RemapSourcesTask.manageImports.get()
                enableMessageCollector = this@RemapSourcesTask.enableMessageCollector.get()
            }

        val results = transformer.remap(sources, sources)
        for ((relative, result) in results) {
            val outputFile = outputRoot.resolve(relative)
            outputFile.parent.toFile().mkdirs()
            outputFile.writeText(result.first, StandardCharsets.UTF_8)
        }
    }

    private fun buildMappingSet(steps: List<String>): MappingSet {
        val iterator = steps.iterator()
        val first =
            readMappings(iterator.next())
                .mapValues { (_, mapping) -> mapping.copyShallow() }
                .toMutableMap()

        while (iterator.hasNext()) {
            val nextMappings = readMappings(iterator.next())
            for (mapping in first.values) {
                val next = nextMappings[mapping.newName] ?: continue
                mapping.newName = next.newName
                mapping.fields =
                    mapping.fields.mapValuesTo(mutableMapOf()) { (_, value) ->
                        next.fields[value] ?: value
                    }
                mapping.methods =
                    mapping.methods.mapValuesTo(mutableMapOf()) { (_, value) ->
                        next.methods[value] ?: value
                    }
            }
        }
        return LegacyMappingsReader(first).read()
    }

    private fun readMappings(step: String): Map<String, LegacyMapping> {
        val separator = step.indexOf(':')
        require(separator > 0) { "Invalid mapping step: $step" }
        val direction = step.substring(0, separator)
        val path = project.rootProject.file(step.substring(separator + 1)).toPath()
        return LegacyMapping.readMappings(path, direction == "reverse")
    }

    private fun LegacyMapping.copyShallow(): LegacyMapping {
        val copy = LegacyMapping(oldName, newName)
        copy.fields.putAll(fields)
        copy.methods.putAll(methods)
        return copy
    }
}
