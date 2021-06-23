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

package net.fabricmc.loom.configuration.enumwidener;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

public class EnumWidenerMethodVisitor extends MethodVisitor {
	private final boolean constructor;

	public EnumWidenerMethodVisitor(int api, MethodVisitor methodVisitor, boolean constructor) {
		super(api, methodVisitor);

		this.constructor = constructor;
	}

	@Override
	public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
		super.visitAnnotableParameterCount(!this.constructor || parameterCount == 0 ? parameterCount : parameterCount + 2, visible);
	}

	@Override
	public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
		return super.visitParameterAnnotation(this.constructor ? parameter + 2 : parameter, descriptor, visible);
	}
}
