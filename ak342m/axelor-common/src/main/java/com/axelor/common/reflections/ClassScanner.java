/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2017 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.common.reflections;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.axelor.internal.asm.AnnotationVisitor;
import com.axelor.internal.asm.ClassReader;
import com.axelor.internal.asm.ClassVisitor;
import com.axelor.internal.asm.Opcodes;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;

/**
 * The {@link ClassScanner} uses ASM and guava's ClassPath API to search for
 * types based on super type or annotations.
 * 
 */
final class ClassScanner {

	private static final int ASM_FLAGS = ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;
	private static final String IGNORE_OBJECT = "java/lang/Object";
	
	private ClassLoader loader;
	
	private Map<String, Collector> collectors = Maps.newConcurrentMap();
	private Set<String> packages = Sets.newHashSet();

	/**
	 * Create a new instance of {@link ClassScanner} using the given
	 * {@link ClassLoader}. <br>
	 * <br>
	 * The optional package names can be provided to restrict the scan within
	 * those packages.
	 * 
	 * @param loader
	 *            the {@link ClassLoader} to use for scanning
	 * @param packages
	 *            the package names to restrict the scan within
	 */
	public ClassScanner(ClassLoader loader, String... packages) {
		this.loader = loader;
		if (packages != null) {
			for (String name : packages) {
				this.packages.add(name);
			}
		}
	}
	
	@SuppressWarnings("all")
	public <T> ImmutableSet<Class<? extends T>> getSubTypesOf(Class<T> type) {
		ImmutableSet.Builder<Class<? extends T>> builder = ImmutableSet.builder();
		
		Set<String> types;
		try {
			types = getSubTypesOf(type.getName());
		} catch (IOException e) {
			throw Throwables.propagate(e);
		}
		
		for (String sub : types) {
			try {
				Class<?> found = loader.loadClass(sub);
				builder.add((Class) found);
			} catch (Throwable e) {
			}
		}
		return builder.build();
	}
	
	public ImmutableSet<Class<?>> getTypesAnnotatedWith(Class<?> annotation) {
		ImmutableSet.Builder<Class<?>> builder = ImmutableSet.builder();
		
		if (collectors.isEmpty()) {
			try {
				scan();
			} catch (IOException e) {
				throw Throwables.propagate(e);
			}
		}

		for (String klass : collectors.keySet()) {
			Set<String> my = collectors.get(klass).annotations;
			if (my == null) {
				continue;
			}
			if (my.contains(annotation.getName())) {
				try {
					builder.add(loader.loadClass(klass));
				} catch (Throwable e) {
				}
			}
		}
		return builder.build();
	}
	
	private Set<String> getSubTypesOf(String type) throws IOException {

		Set<String> all = Sets.newHashSet();
		Set<String> types = Sets.newHashSet();

		if (collectors.isEmpty()) {
			scan();
		}
		
		for (String klass : collectors.keySet()) {
			Set<String> my = collectors.get(klass).superNames;
			if (my == null) {
				continue;
			}
			if (my.contains(type)) {
				types.add(klass);
			}
		}

		all.addAll(types);
		
		for (String klass : types) {
			all.addAll(getSubTypesOf(klass));
		}
		
		return all;
	}
	
	private void scan() throws IOException {
		ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		List<Future<?>> futures = Lists.newArrayList();
		Set<ClassInfo> infos = Sets.newHashSet();
		
		if (packages.isEmpty()) {
			infos = ClassPath.from(loader).getTopLevelClasses();
		} else {
			for (String pkg : packages) {
				infos.addAll(ClassPath.from(loader).getTopLevelClassesRecursive(pkg));
			}
		}
		
		try {
			for (final ClassInfo info : infos) {
				futures.add(executor.submit(new Callable<Object>() {
					@Override
					public Object call() throws Exception {
						scan(info.getName());
						return info.getName();
					}
				}));
			}
			
			for (Future<?> future : futures) {
				try {
					future.get();
				} catch (Exception e) {
				}
			}
		} finally {
			executor.shutdown();
		}
	}

	private void scan(final String type) throws ClassNotFoundException {
		Collector collector = collectors.get(type);
		if (collector != null) {
			return;
		}
		
		String resouce = type.replace('.', '/') + ".class";
		try {
			InputStream stream = loader.getResourceAsStream(resouce);
			try {
				BufferedInputStream in = new BufferedInputStream(stream);
				ClassReader reader = new ClassReader(in);
				collector = new Collector();
				reader.accept(collector, ASM_FLAGS);
				collectors.put(type, collector);
			} finally {
				stream.close();
			}
		} catch (NullPointerException | IOException e) {
			throw new ClassNotFoundException(type);
		}
	}
	
	static class Collector extends ClassVisitor {
		
		private Set<String> superNames;
		private Set<String> annotations;
		
		public Collector() {
			super(Opcodes.ASM4);
		}
		
		private void acceptSuper(String name) {
			if (name == null) {
				return;
			}
			if (superNames == null) {
				superNames = Sets.newHashSet();
			}
			superNames.add(name.replace("/", "."));
		}
		
		private void acceptAnnotation(String name) {
			if (name == null || IGNORE_OBJECT.equals(name)) {
				return;
			}
			if (annotations == null) {
				annotations = Sets.newHashSet();
			}
			annotations.add(name.replace("/", ".").substring(1, name.length() - 1));
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			acceptSuper(superName);
			if (interfaces != null) {
				for (String iface : interfaces) {
					acceptSuper(iface);
				}
			}
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			acceptAnnotation(desc);
			return null;
		}
	}
}
