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

package net.fabricmc.loom.configuration.providers.mappings.parchment;

import java.util.Locale;

import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;

public final class ParchmentPrefixStripingMappingVisitor extends ForwardingMappingVisitor {
	protected ParchmentPrefixStripingMappingVisitor(MappingVisitor next) {
		super(next);
	}

	@Override
	public boolean visitMethodArg(int argPosition, int lvIndex, String srcName) {
		return super.visitMethodArg(argPosition, lvIndex, stripMethodArg(srcName));
	}

	public static String stripMethodArg(String arg) {
		if (arg.length() > 1 && arg.startsWith("p") && Character.isUpperCase(arg.charAt(1))) {
			String a2 = arg.substring(1); // Remove p
			return a2.substring(0, 1).toLowerCase(Locale.ROOT) + a2.substring(1); // Make first char lowercase
		}

		return arg;
	}
}
