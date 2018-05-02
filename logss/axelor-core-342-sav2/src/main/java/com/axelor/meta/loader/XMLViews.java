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
package com.axelor.meta.loader;

import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.common.StringUtils;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaAction;
import com.axelor.meta.db.MetaView;
import com.axelor.meta.db.repo.MetaActionRepository;
import com.axelor.meta.db.repo.MetaViewRepository;
import com.axelor.meta.schema.ObjectViews;
import com.axelor.meta.schema.actions.Action;
import com.axelor.meta.schema.views.AbstractView;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;

public class XMLViews {
	
	private static final Logger log = LoggerFactory.getLogger(XMLViews.class);

	private static final String LOCAL_SCHEMA = "object-views.xsd";
	private static final String REMOTE_SCHEMA = "object-views_" + ObjectViews.VERSION + ".xsd";
	
	private static final String INDENT_STRING = "  ";
	private static final String[] INDENT_PROPERTIES = {
		"eclipselink.indent-string",
		"com.sun.xml.internal.bind.indentString",
		"com.sun.xml.bind.indentString"
	};

	private static Marshaller marshaller;
	private static Unmarshaller unmarshaller;

	static {
		try {
			init();
		} catch (JAXBException | SAXException e) {
			throw Throwables.propagate(e);
		}
	}

	private static void init() throws JAXBException, SAXException {
		if (unmarshaller != null) {
			return;
		}
		JAXBContext context = JAXBContext.newInstance(ObjectViews.class);
		unmarshaller = context.createUnmarshaller();
		marshaller = context.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, ObjectViews.NAMESPACE + " " + ObjectViews.NAMESPACE + "/" + REMOTE_SCHEMA);

		for (String name : INDENT_PROPERTIES) {
			try {
				marshaller.setProperty(name, INDENT_STRING);
				break;
			} catch (Exception e) {
				log.info("JAXB marshaller doesn't support property: " + name);
			}
		}

		SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		Schema schema = schemaFactory.newSchema(Resources.getResource(LOCAL_SCHEMA));

		unmarshaller.setSchema(schema);
		marshaller.setSchema(schema);
	}

	public static ObjectViews unmarshal(InputStream stream) throws JAXBException {
		synchronized (unmarshaller) {
			return (ObjectViews) unmarshaller.unmarshal(stream);
		}
	}
	
	public static ObjectViews unmarshal(String xml) throws JAXBException {
		synchronized (unmarshaller) {
			return (ObjectViews) unmarshaller.unmarshal(new StringReader(prepareXML(xml)));
		}
	}
	
	public static void marshal(ObjectViews views, Writer writer) throws JAXBException {
		synchronized (marshaller) {
			marshaller.marshal(views, writer);
		}
	}
	
	private static String prepareXML(String xml) {
		StringBuilder sb = new StringBuilder("<?xml version='1.0' encoding='UTF-8'?>\n");
		sb.append("<object-views")
		  .append(" xmlns='").append(ObjectViews.NAMESPACE).append("'")
		  .append(" xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'")
		  .append(" xsi:schemaLocation='").append(ObjectViews.NAMESPACE).append(" ")
		  .append(ObjectViews.NAMESPACE + "/" + REMOTE_SCHEMA).append("'")
		  .append(">\n")
		  .append(xml)
		  .append("\n</object-views>");
		return sb.toString();
	}

	private static String strip(String xml) {
		String[] lines = xml.split("\n");
		StringBuilder sb = new StringBuilder();
		for(int i = 2 ; i < lines.length - 1 ; i ++) {
			sb.append(lines[i] + "\n");
		}
		sb.deleteCharAt(sb.length()-1);
		return StringUtils.stripIndent(sb.toString());
	}
	
	@SuppressWarnings("all")
	public static String toXml(Object obj, boolean strip) {

		ObjectViews views = new ObjectViews();
		StringWriter writer = new StringWriter();

		if (obj instanceof Action) {
			views.setActions(ImmutableList.of((Action) obj));
		}
		if (obj instanceof AbstractView) {
			views.setViews(ImmutableList.of((AbstractView) obj));
		}
		if (obj instanceof List) {
			views.setViews((List) obj);
		}
		try {
			marshal(views, writer);
		} catch (JAXBException e) {
			log.error(e.getMessage(), e);
		}
		String text = writer.toString();
		if (strip) {
			text = strip(text);
		}
		return text;
	}
	
	public static ObjectViews fromXML(String xml) throws JAXBException {
		if (Strings.isNullOrEmpty(xml))
			return null;

		if (!xml.trim().startsWith("<?xml"))
			xml = prepareXML(xml);

		StringReader reader = new StringReader(xml);
		return (ObjectViews) unmarshaller.unmarshal(reader);
	}

	private static MetaView findMetaView(MetaViewRepository views,
			String name, String type, String model, String module, Long group) {
		final List<String> select = new ArrayList<>();
		if (name != null) {
			select.add("self.name = :name");
		}
		if (type != null) {
			select.add("self.type = :type");
		}
		if (model != null) {
			select.add("self.model = :model");
		}
		if (module != null) {
			select.add("self.module = :module");
		}
		if (group == null) {
			select.add("self.groups is empty");
		} else {
			select.add("self.groups[].id = :group");
		}
		return views.all().filter(Joiner.on(" AND ").join(select))
				.bind("name", name)
				.bind("type", type)
				.bind("model", model)
				.bind("module", module)
				.bind("group", group)
				.cacheable()
				.order("-priority")
				.fetchOne();
	}

	public static AbstractView findView(String name, String type, String model, String module) {

		final MetaViewRepository views = Beans.get(MetaViewRepository.class);

		User user = AuthUtils.getUser();
		Long group = user != null && user.getGroup() != null ? user.getGroup().getId() : null;

		MetaView view = null;

		// first find by name
		if (name != null) {
			// with group
			view = findMetaView(views, name, null, model, module, group);
			view = view == null ? findMetaView(views, name, null, null, module, group) : view;

			// without group
			view = view == null ? findMetaView(views, name, null, model, module, null) : view;
			view = view == null ? findMetaView(views, name, null, null, module, null) : view;
		}

		// next find by type
		if (type != null && model != null) {
			view = view == null ? findMetaView(views, null, type, model, module, group) : view;
			view = view == null ? findMetaView(views, null, type, model, module, null) : view;
		}

		try {
			final AbstractView xmlView = ((ObjectViews) XMLViews.unmarshal(view.getXml())).getViews().get(0);
			if (view.getHelpLink() != null) {
				xmlView.setHelpLink(view.getHelpLink());
			}
			return xmlView;
		} catch (Exception e) {
		}
		return null;
	}

	public static Map<String, Object> findViews(String model, Map<String, String> views) {
		final Map<String, Object> result = Maps.newHashMap();
		if (views == null || views.isEmpty()) {
			views = ImmutableMap.of("grid", "", "form", "");
		}
		for(String type : views.keySet()) {
			final String name = views.get(type);
			final AbstractView view = findView(model, name, type);
			try {
				result.put(type, view);
			} catch (Exception e) {
			}
		}
		return result;
	}

	public static AbstractView findView(String model, String name, String type) {
		return findView(name, type, model, null);
	}

	public static AbstractView findView(String name, String module) {
		return findView(name, null, null, module);
	}

	public static Action findAction(String name) {
		MetaAction action = Beans.get(MetaActionRepository.class).findByName(name);
		try {
			return ((ObjectViews) XMLViews.unmarshal(action.getXml())).getActions().get(0);
		} catch (Exception e) {
		}
		return null;
	}
}
