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

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import groovy.util.Node;
import groovy.xml.QName;

public final class GroovyXmlUtil {
	private GroovyXmlUtil() { }

	public static Node getOrCreateNode(Node parent, String name) {
		for (Object object : parent.children()) {
			if (object instanceof Node node && isSameName(node.name(), name)) {
				return node;
			}
		}

		return parent.appendNode(name);
	}

	public static Optional<Node> getNode(Node parent, String name) {
		for (Object object : parent.children()) {
			if (object instanceof Node node && isSameName(node.name(), name)) {
				return Optional.of(node);
			}
		}

		return Optional.empty();
	}

	private static boolean isSameName(Object nodeName, String givenName) {
		if (nodeName instanceof String) {
			return nodeName.equals(givenName);
		}

		if (nodeName instanceof QName qName) {
			return qName.matches(givenName);
		}

		// New groovy 3 (gradle 7) class
		if (nodeName instanceof groovy.namespace.QName qName) {
			return qName.matches(givenName);
		}

		throw new UnsupportedOperationException("Cannot determine if " + nodeName.getClass() + " is the same as a String");
	}

	public static Stream<Node> childrenNodesStream(Node node) {
		//noinspection unchecked
		return (Stream<Node>) (Stream) (((List<Object>) node.children()).stream().filter((i) -> i instanceof Node));
	}
}
