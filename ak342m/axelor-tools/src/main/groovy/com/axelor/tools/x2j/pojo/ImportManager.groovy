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
package com.axelor.tools.x2j.pojo

class ImportManager {

	private String base

	private Set<String> imports = new HashSet()

	private Map<String, String> names = new HashMap()

	private boolean groovy

	def GROOVY_PAT = ~/(java\.(io|net|lang|util))|(groovy\.(lang|util))/

	ImportManager(String base, boolean groovy) {
		this.base = base
		this.groovy = groovy
	}

	String importType(String fqn) {

		List<String> parts = fqn.split("\\.")
		if (parts.size() == 1)
			return fqn

		String simpleName = parts.pop()
		def name = simpleName

		if (name ==~ /[A-Z_]+/ && parts.last() ==~ /[A-Z_].*/) {
			simpleName = parts.pop()
			name = simpleName + "." + name
		}

		simpleName = simpleName.replaceAll("\\<.*", "")

		def pkg = parts.join(".")

		if (pkg == base || pkg == "java.lang") {
			return name
		}

		if (groovy && (pkg ==~ GROOVY_PAT || simpleName == 'BigDecimal')) {
			return name
		}

		def newFqn = pkg + "." + simpleName
		def canBeSimple = true

		if (names.containsKey(simpleName)) {
			def existing = names.get(simpleName)
			canBeSimple = existing == newFqn
		} else {
			names.put(simpleName, newFqn)
			imports.add(newFqn)
		}

		if (canBeSimple)
			return name
		return fqn
	}

	List<String> getImports() {
		return new ArrayList<String>(imports).sort()
	}

	List<String> getImportStatements() {

		def all = new ArrayList<String>()
		def groups = imports.groupBy {
			it.split('\\.')[0]
		}

		def coms = groups.remove("com")

		try {
			all.addAll(groups.remove("java").sort())
			all.add(null)
		} catch (NullPointerException e){}

		try {
			all.addAll(groups.remove("javax").sort())
			all.add(null)
		} catch (NullPointerException e){}

		groups.each {
			all.addAll(it.value.sort())
			all.add(null)
		}

		if (coms) {
			all.addAll(coms.sort())
			all.add(null)
		}

		all.pop()

		return all.collect {
			it == null ? "" : "import " + it + ";"
		}
	}
}
