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
package com.axelor.text;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * The {@link Template} renderer provides ways to render an encapsulated
 * {@link Template} instance.
 * 
 */
public abstract class Renderer {

	/**
	 * Render the template.
	 * 
	 * @return the template output
	 */
	public String render() {
		StringWriter out = new StringWriter();
		try {
			render(out);
		} catch (IOException e) {
		}
		return out.toString();
	}

	/**
	 * Render the template.
	 * 
	 * @param out
	 *            the writer instance to write the output
	 * @throws IOException
	 *             if writer throws any {@link IOException}
	 */
	public abstract void render(Writer out) throws IOException;
}
