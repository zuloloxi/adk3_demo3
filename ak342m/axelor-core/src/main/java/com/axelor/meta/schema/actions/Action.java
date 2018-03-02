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
package com.axelor.meta.schema.actions;

import java.util.regex.Pattern;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.meta.ActionHandler;
import com.google.common.base.Objects;
import com.google.common.base.Strings;

@XmlType(name = "AbstractAction")
public abstract class Action {
	
	protected final transient Logger log = LoggerFactory.getLogger(getClass());
	
	@XmlAttribute
	private String name;
	
	@XmlAttribute
	private String model;
	
	@XmlAttribute(name = "if-module")
	private String moduleToCheck;

	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getModel() {
		return model;
	}
	
	public void setModel(String model) {
		this.model = model;
	}
	
	public String getModuleToCheck() {
		return moduleToCheck;
	}

	public void setModuleToCheck(String moduleToCheck) {
		this.moduleToCheck = moduleToCheck;
	}

	public abstract Object wrap(ActionHandler handler);
	
	public abstract Object evaluate(ActionHandler handler);
	
	@Override
	public String toString() {
		return Objects.toStringHelper(getClass()).add("name", getName()).toString();
	}
	
	static boolean test(ActionHandler handler, String expression) {
		if (Strings.isNullOrEmpty(expression)) // if expression is not given always return true
			return true;
		if (expression.matches("true"))
			return true;
		if (expression.equals("false"))
			return false;
		Pattern pattern = Pattern.compile("^(eval|select|action):");
		if (expression != null && !pattern.matcher(expression).find()) {
			expression = "eval:" + expression;
		}
		Object result = handler.evaluate(expression);
		if (result == null)
			return false;
		if (result instanceof Number && result.equals(0))
			return false;
		if (result instanceof Boolean)
			return (Boolean) result;
		return true;
	}
	
	@XmlType
	public static abstract class Element {
		
		@XmlAttribute(name = "if")
		private String condition;

		@XmlAttribute
		private String name;
		
		@XmlAttribute(name = "expr")
		private String expression;
		
		public String getCondition() {
			return condition;
		}
		
		public void setCondition(String condition) {
			this.condition = condition;
		}
		
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public String getExpression() {
			return expression;
		}
		
		public void setExpression(String expression) {
			this.expression = expression;
		}

		boolean test(ActionHandler handler) {
			return Action.test(handler, getCondition());
		}
	}
}
