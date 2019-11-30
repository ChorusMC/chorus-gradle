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

package net.fabricmc.loom.util;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.logging.Logger;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;

public final class SourceUtils {

    private SourceUtils() {
        throw new IllegalStateException("Tried to initialize: SourceUtils but this is a Utility class.");
    }

    public static File findSources(DependencyHandler dependencies, ResolvedArtifact artifact) {
        @SuppressWarnings("unchecked") ArtifactResolutionQuery query = dependencies.createArtifactResolutionQuery()//
                                                                                       .forComponents(artifact.getId().getComponentIdentifier())//
                                                                                       .withArtifacts(JvmLibrary.class, SourcesArtifact.class);

        for (ComponentArtifactsResult result : query.execute().getResolvedComponents()) {
            for (ArtifactResult srcArtifact : result.getArtifacts(SourcesArtifact.class)) {
                if (srcArtifact instanceof ResolvedArtifactResult) {
                    return ((ResolvedArtifactResult) srcArtifact).getFile();
                }
            }
        }

        return null;
    }

    public static File findSources(DependencyHandler dependencies, Dependency artifact) {
        @SuppressWarnings("unchecked") ArtifactResolutionQuery query = dependencies.createArtifactResolutionQuery()//
                                                                                       .forModule(artifact.getGroup(), artifact.getName(), artifact.getVersion())//
                                                                                       .withArtifacts(JvmLibrary.class, SourcesArtifact.class);

        for (ComponentArtifactsResult result : query.execute().getResolvedComponents()) {
            for (ArtifactResult srcArtifact : result.getArtifacts(SourcesArtifact.class)) {
                if (srcArtifact instanceof ResolvedArtifactResult) {
                    return ((ResolvedArtifactResult) srcArtifact).getFile();
                }
            }
        }

        return null;
    }
}
