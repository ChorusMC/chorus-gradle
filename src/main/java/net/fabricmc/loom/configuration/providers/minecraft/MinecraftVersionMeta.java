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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonObject;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Mirrors;
import net.fabricmc.loom.util.OperatingSystem;

@SuppressWarnings("unused")
public final class MinecraftVersionMeta {
	private JsonObject arguments;
	private AssetIndex assetIndex;
	private String assets;
	private int complianceLevel;
	private Map<String, Download> downloads;
	private String id;
	private List<Library> libraries;
	private JsonObject logging;
	private String mainClass;
	private int minimumLauncherVersion;
	private String releaseTime;
	private String time;
	private String type;

	public Download getDownload(String key) {
		return getDownloads().get(key);
	}

	public JsonObject getArguments() {
		return arguments;
	}

	public AssetIndex getAssetIndex() {
		return assetIndex;
	}

	public String getAssets() {
		return assets;
	}

	public int getComplianceLevel() {
		return complianceLevel;
	}

	public Map<String, Download> getDownloads() {
		return downloads;
	}

	public String getId() {
		return id;
	}

	public List<Library> getLibraries() {
		return libraries;
	}

	public JsonObject getLogging() {
		return logging;
	}

	public String getMainClass() {
		return mainClass;
	}

	public int getMinimumLauncherVersion() {
		return minimumLauncherVersion;
	}

	public String getReleaseTime() {
		return releaseTime;
	}

	public String getTime() {
		return time;
	}

	public String getType() {
		return type;
	}

	public final class AssetIndex extends Downloadable {
		private String id;
		private long totalSize;

		public String getFabricId(String version) {
			return id.equals(version) ? version : version + "-" + id;
		}

		public String getId() {
			return id;
		}

		public long getTotalSize() {
			return totalSize;
		}
	}

	public final class Download extends Downloadable {
	}

	public final class Library {
		private Downloads downloads;
		private String name;
		private Map<String, String> natives;
		private List<Rule> rules;

		public boolean isValidForOS() {
			if (rules == null || rules.isEmpty()) {
				return true;
			}

			for (Rule rule : rules) {
				if (rule.appliesToOS() && !rule.isAllowed()) {
					return false;
				}
			}

			return true;
		}

		public boolean hasNatives() {
			return this.natives != null;
		}

		public boolean hasNativesForOS() {
			if (!hasNatives()) {
				return false;
			}

			if (natives.get(OperatingSystem.getOS()) == null) {
				return false;
			}

			return isValidForOS();
		}

		public Classifier getClassifierForOS() {
			return getDownloads().getClassifier(natives.get(OperatingSystem.getOS()));
		}

		public Downloads getDownloads() {
			return downloads;
		}

		public Artifact getArtifact() {
			if (getDownloads() == null) {
				return null;
			}

			return getDownloads().getArtifact();
		}

		public String getName() {
			return name;
		}

		public Map<String, String> getNatives() {
			return natives;
		}

		public List<Rule> getRules() {
			return rules;
		}
	}

	public final class Downloads {
		private Artifact artifact;
		private Map<String, Classifier> classifiers;

		public Classifier getClassifier(String os) {
			return classifiers.get(os);
		}

		public Artifact getArtifact() {
			return artifact;
		}

		public Map<String, Classifier> getClassifiers() {
			return classifiers;
		}
	}

	public final class Artifact extends Downloadable {
		@Override
		public String getUrl() {
			return Mirrors.getLibrariesBase() + super.getPath();
		}
	}

	public final class Classifier extends Downloadable {
	}

	public final class Rule {
		private String action;
		private OS os;

		public boolean appliesToOS() {
			return getOS() == null || getOS().isValidForOS();
		}

		public boolean isAllowed() {
			return getAction().equals("allow");
		}

		public String getAction() {
			return action;
		}

		public OS getOS() {
			return os;
		}
	}

	public final class OS {
		private String name;

		public boolean isValidForOS() {
			return getName() == null || getName().equalsIgnoreCase(OperatingSystem.getOS());
		}

		public String getName() {
			return name;
		}
	}

	// A base class for everything that can be downloaded
	public abstract class Downloadable {
		private String path;
		private String sha1;
		private long size;
		private String url;

		public File getRelativeFile(File baseDirectory) {
			Objects.requireNonNull(getPath(), "Cannot get relative file from a null path");
			return new File(baseDirectory, getPath());
		}

		public String getPath() {
			return path;
		}

		public String getSha1() {
			return sha1;
		}

		public long getSize() {
			return size;
		}

		public String getUrl() {
			return url;
		}
	}
}
