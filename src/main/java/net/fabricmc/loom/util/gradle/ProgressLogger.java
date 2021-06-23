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

package net.fabricmc.loom.util.gradle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.gradle.api.Project;

/**
 * Wrapper to ProgressLogger internal API.
 */
public class ProgressLogger {
	private final Object logger;
	private final Method getDescription, setDescription, getShortDescription, setShortDescription, getLoggingHeader, setLoggingHeader, start, started, startedArg, progress, completed, completedArg;

	private ProgressLogger(Object logger) {
		this.logger = logger;
		this.getDescription = getMethod("getDescription");
		this.setDescription = getMethod("setDescription", String.class);
		this.getShortDescription = getMethod("getShortDescription");
		this.setShortDescription = getMethod("setShortDescription", String.class);
		this.getLoggingHeader = getMethod("getLoggingHeader");
		this.setLoggingHeader = getMethod("setLoggingHeader", String.class);
		this.start = getMethod("start", String.class, String.class);
		this.started = getMethod("started");
		this.startedArg = getMethod("started", String.class);
		this.progress = getMethod("progress", String.class);
		this.completed = getMethod("completed");
		this.completedArg = getMethod("completed", String.class);
	}

	private static Class<?> getFactoryClass() {
		Class<?> progressLoggerFactoryClass = null;

		try {
			// Gradle 2.14 and higher
			progressLoggerFactoryClass = Class.forName("org.gradle.internal.logging.progress.ProgressLoggerFactory");
		} catch (ClassNotFoundException e) {
			// prior to Gradle 2.14
			try {
				progressLoggerFactoryClass = Class.forName("org.gradle.logging.ProgressLoggerFactory");
			} catch (ClassNotFoundException ignored) {
				// Unsupported Gradle version
			}
		}

		return progressLoggerFactoryClass;
	}

	private Method getMethod(String methodName, Class<?>... args) {
		if (logger != null) {
			try {
				return logger.getClass().getMethod(methodName, args);
			} catch (NoSuchMethodException ignored) {
				// Nope
			}
		}

		return null;
	}

	private Object invoke(Method method, Object... args) {
		if (logger != null) {
			try {
				method.setAccessible(true);
				return method.invoke(logger, args);
			} catch (IllegalAccessException | InvocationTargetException ignored) {
				// Nope
			}
		}

		return null;
	}

	/**
	 * Get a Progress logger from the Gradle internal API.
	 *
	 * @param project The project
	 * @param category The logger category
	 * @return In any case a progress logger
	 */
	public static ProgressLogger getProgressFactory(Project project, String category) {
		try {
			Method getServices = project.getClass().getMethod("getServices");
			Object serviceFactory = getServices.invoke(project);
			Method get = serviceFactory.getClass().getMethod("get", Class.class);
			Object progressLoggerFactory = get.invoke(serviceFactory, getFactoryClass());
			Method newOperation = progressLoggerFactory.getClass().getMethod("newOperation", String.class);
			return new ProgressLogger(newOperation.invoke(progressLoggerFactory, category));
		} catch (Exception e) {
			project.getLogger().error("Unable to get progress logger. Download progress will not be displayed.");
			return new ProgressLogger(null);
		}
	}

	/**
	 * Returns the description of the operation.
	 *
	 * @return the description, must not be empty.
	 */
	public String getDescription() {
		return (String) invoke(getDescription);
	}

	/**
	 * Sets the description of the operation. This should be a full, stand-alone description of the operation.
	 *
	 * <p>This must be called before {@link #started()}
	 *
	 * @param description The description.
	 */
	public ProgressLogger setDescription(String description) {
		invoke(setDescription, description);
		return this;
	}

	/**
	 * Returns the short description of the operation. This is used in place of the full description when display space is limited.
	 *
	 * @return The short description, must not be empty.
	 */
	public String getShortDescription() {
		return (String) invoke(getShortDescription);
	}

	/**
	 * Sets the short description of the operation. This is used in place of the full description when display space is limited.
	 *
	 * <p>This must be called before {@link #started()}
	 *
	 * @param description The short description.
	 */
	public ProgressLogger setShortDescription(String description) {
		invoke(setShortDescription, description);
		return this;
	}

	/**
	 * Returns the logging header for the operation. This is logged before any other log messages for this operation are logged. It is usually
	 * also logged at the end of the operation, along with the final status message. Defaults to null.
	 *
	 * <p>If not specified, no logging header is logged.
	 *
	 * @return The logging header, possibly empty.
	 */
	public String getLoggingHeader() {
		return (String) invoke(getLoggingHeader);
	}

	/**
	 * Sets the logging header for the operation. This is logged before any other log messages for this operation are logged. It is usually
	 * also logged at the end of the operation, along with the final status message. Defaults to null.
	 *
	 * @param header The header. May be empty or null.
	 */
	public ProgressLogger setLoggingHeader(String header) {
		invoke(setLoggingHeader, header);
		return this;
	}

	/**
	 * Convenience method that sets descriptions and logs started() event.
	 *
	 * @return this logger instance
	 */
	public ProgressLogger start(String description, String shortDescription) {
		invoke(start, description, shortDescription);
		return this;
	}

	/**
	 * Logs the start of the operation, with no initial status.
	 */
	public void started() {
		invoke(started);
	}

	/**
	 * Logs the start of the operation, with the given status.
	 *
	 * @param status The initial status message. Can be null or empty.
	 */
	public void started(String status) {
		invoke(started, status);
	}

	/**
	 * Logs some progress, indicated by a new status.
	 *
	 * @param status The new status message. Can be null or empty.
	 */
	public void progress(String status) {
		invoke(progress, status);
	}

	/**
	 * Logs the completion of the operation, with no final status.
	 */
	public void completed() {
		invoke(completed);
	}

	/**
	 * Logs the completion of the operation, with a final status. This is generally logged along with the description.
	 *
	 * @param status The final status message. Can be null or empty.
	 */
	public void completed(String status) {
		invoke(completed, status);
	}
}
