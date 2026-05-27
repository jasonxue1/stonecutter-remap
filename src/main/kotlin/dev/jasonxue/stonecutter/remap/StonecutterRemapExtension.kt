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

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class StonecutterRemapExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        val baseVersion: Property<String> = objects.property(String::class.java)
        val manageImports: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
        val enableMessageCollector: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

        internal val links: MutableList<LinkSpec> = mutableListOf()

        fun link(
            from: String,
            to: String,
            file: String,
        ) {
            links += LinkSpec(from, to, file)
        }
    }

data class LinkSpec(
    val from: String,
    val to: String,
    val file: String,
)
