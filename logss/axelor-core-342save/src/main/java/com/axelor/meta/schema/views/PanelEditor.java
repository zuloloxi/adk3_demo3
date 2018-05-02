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
package com.axelor.meta.schema.views;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;

import com.axelor.meta.MetaStore;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonTypeName;

@XmlType
@JsonTypeName("editor")
public class PanelEditor extends AbstractPanel {

	transient PanelField forField;
	transient List<Object> targetFields;

	@XmlAttribute
	private String layout;

	@XmlAttribute(name = "x-viewer")
	private Boolean viewer;

	@XmlAttribute(name = "x-show-on-new")
	private Boolean showOnNew;

	@XmlAttribute
	private String onNew;

	@XmlElements({
		@XmlElement(name = "field", type = PanelField.class),
		@XmlElement(name = "button", type = Button.class),
		@XmlElement(name = "spacer", type = Spacer.class),
		@XmlElement(name = "label", type = Label.class),
		@XmlElement(name = "panel", type = Panel.class)
	})
	private List<AbstractWidget> items;

	public String getLayout() {
		return layout;
	}

	public Boolean getViewer() {
		return viewer;
	}

	public Boolean getShowOnNew() {
		return showOnNew;
	}

	public String getOnNew() {
		return onNew;
	}

	public List<AbstractWidget> getItems() {
		// process target fields
		getTargetFields();
		return process(items);
	}

	public void setItems(List<AbstractWidget> items) {
		this.items = items;
	}

	private List<String> findFields(AbstractWidget widget) {

		final List<String> all = new ArrayList<>();

		if (widget instanceof Field) {
			all.add(((Field) widget).getName());
			return all;
		}

		if (widget instanceof PanelEditor) {
			for (AbstractWidget item : ((PanelEditor) widget).getItems()) {
				all.addAll(findFields(item));
			}
		} else if (widget instanceof Panel) {
			for (AbstractWidget item : ((Panel) widget).getItems()) {
				all.addAll(findFields(item));
			}
		}

		return all;
	}

	@JsonGetter("fields")
	public List<Object> getTargetFields() {
		if (targetFields != null || items == null || forField == null || forField.getTarget() == null) {
			return targetFields;
		}
		this.targetFields = new ArrayList<>();
		final Class<?> target;
		try {
			target = Class.forName(forField.getTarget());
		} catch (ClassNotFoundException e) {
			return null;
		}
		final Map<String, Object> fields = MetaStore.findFields(target, findFields(this));
		this.targetFields.addAll((Collection<?>)fields.get("fields"));

		return targetFields;
	}
}
