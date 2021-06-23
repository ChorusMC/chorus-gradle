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

package net.fabricmc.loom.task;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.gradle.api.Project;
import org.gradle.api.tasks.JavaExec;

import net.fabricmc.loom.configuration.ide.RunConfig;

public abstract class AbstractRunTask extends JavaExec {
	private final RunConfig config;

	public AbstractRunTask(Function<Project, RunConfig> configProvider) {
		super();
		setGroup("fabric");
		this.config = configProvider.apply(getProject());

		setClasspath(config.sourceSet.getRuntimeClasspath());
	}

	@Override
	public void exec() {
		List<String> argsSplit = new ArrayList<>();
		String[] args = config.programArgs.split(" ");
		int partPos = -1;

		for (int i = 0; i < args.length; i++) {
			if (partPos < 0) {
				if (args[i].startsWith("\"")) {
					if (args[i].endsWith("\"")) {
						argsSplit.add(args[i].substring(1, args[i].length() - 1));
					} else {
						partPos = i;
					}
				} else {
					argsSplit.add(args[i]);
				}
			} else if (args[i].endsWith("\"")) {
				StringBuilder builder = new StringBuilder(args[partPos].substring(1));

				for (int j = partPos + 1; j < i; j++) {
					builder.append(" ").append(args[j]);
				}

				builder.append(" ").append(args[i], 0, args[i].length() - 1);
				argsSplit.add(builder.toString());
				partPos = -1;
			}
		}

		args(argsSplit);
		setWorkingDir(new File(getProject().getRootDir(), config.runDir));

		super.exec();
	}

	@Override
	public void setWorkingDir(File dir) {
		if (!dir.exists()) {
			dir.mkdirs();
		}

		super.setWorkingDir(dir);
	}

	@Override
	public String getMain() {
		return config.mainClass;
	}

	@Override
	public List<String> getJvmArgs() {
		List<String> superArgs = super.getJvmArgs();
		List<String> args = new ArrayList<>(superArgs != null ? superArgs : Collections.emptyList());
		args.addAll(Arrays.asList(config.vmArgs.split(" ")));
		return args;
	}
}
