/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2012-2014 Axelor (<http://axelor.com>).
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
package com.axelor.data.xml;

import java.util.List;

import com.axelor.data.adapter.DataAdapter;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

@XStreamAlias("input")
public class XMLInput {
	
	@XStreamAlias("file")
	@XStreamAsAttribute
	private String fileName;
	
	@XStreamAsAttribute
	private String root;

	@XStreamImplicit(itemFieldName = "adapter")
	private List<DataAdapter> adapters = Lists.newArrayList();
	
	@XStreamImplicit(itemFieldName = "bind")
	private List<XMLBind> bindings = Lists.newArrayList();

	public String getFileName() {
		return fileName;
	}
	
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getRoot() {
		return root;
	}
	
	public List<DataAdapter> getAdapters() {
		if (adapters == null) {
			adapters = Lists.newArrayList();
		}
		return adapters;
	}
	
	public List<XMLBind> getBindings() {
		return bindings;
	}
	
	@Override
	public String toString() {
		return Objects.toStringHelper(this)
				.add("file", fileName)
				.add("bindings", bindings).toString();
	}
}
