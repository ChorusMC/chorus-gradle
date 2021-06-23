/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.test.util

import io.javalin.Javalin
import org.apache.commons.io.IOUtils

trait MockMavenServerTrait extends ProjectTestTrait {
    public final int mavenServerPort = 9876
    public final File testMavenDir = File.createTempDir()
    private Javalin server

    @SuppressWarnings('unused')
    def setupSpec() {
        println "Maven server path: ${testMavenDir.absolutePath}"

        server = Javalin.create { config ->
            config.enableDevLogging()
        }.start(mavenServerPort)

        /**
         * A very very basic maven server impl, DO NOT copy this and use in production as its not secure
         */
        server.get("*") { ctx ->
            println "GET: " + ctx.path()
            File file = getMavenPath(ctx.path())

            if (!file.exists()) {
                ctx.status(404)
                return
            }

            ctx.result(file.bytes)
        }

        server.put("*") { ctx ->
            println "PUT: " + ctx.path()
            File file = getMavenPath(ctx.path())
            file.parentFile.mkdirs()

            file.withOutputStream {
                IOUtils.copy(ctx.bodyAsInputStream(), it)
            }
        }
    }

    @SuppressWarnings('unused')
    def setup() {
        System.setProperty('loom.test.mavenPort', port())
    }

    @SuppressWarnings('unused')
    def cleanupSpec() {
        server.stop()
        super.cleanupSpec()
    }

    File getMavenDirectory() {
        new File(testMavenDir, "maven")
    }

    File getMavenPath(String path) {
        new File(getMavenDirectory(), path)
    }

    String port() {
        "${mavenServerPort}"
    }
}