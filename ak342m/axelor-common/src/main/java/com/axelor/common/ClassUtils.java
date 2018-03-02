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
package com.axelor.common;

import java.io.InputStream;
import java.net.URL;

/**
 * The class provides static helper methods to deal with classes & resource.
 * 
 */
public final class ClassUtils {

	/**
	 * This is same as {@link Class#forName(String)} but throws
	 * {@link IllegalArgumentException} if class is not found.
	 * 
	 * @param name
	 *            name of the class to look for
	 * @return the class found
	 */
	public static Class<?> findClass(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException(name, e);
		}
	}

	/**
	 * Finds the resource with the given name.
	 * 
	 * @param name
	 *            The resource name
	 * @return resource an {@link URL} for reading the resource or null
	 * @see ClassLoader#getResource(String)
	 */
	public static URL getResource(String name) {
		return Thread.currentThread().getContextClassLoader().getResource(name);
	}

	/**
	 * Returns an input stream for reading the specified resource.
	 * 
	 * @param name
	 *            The resource name
	 * @return An input stream for reading the resource or null
	 * @see ClassLoader#getResourceAsStream(String)
	 */
	public static InputStream getResourceStream(String name) {
		return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
	}
}
