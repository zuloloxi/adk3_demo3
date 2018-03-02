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
package com.axelor.meta;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.persistence.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.db.QueryBinder;
import com.axelor.meta.schema.actions.Action;
import com.axelor.meta.schema.actions.ActionGroup;
import com.axelor.meta.schema.actions.ActionMethod;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.axelor.rpc.Resource;
import com.axelor.script.GroovyScriptHelper;
import com.axelor.script.ScriptBindings;
import com.axelor.script.ScriptHelper;
import com.axelor.text.Templates;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.io.CharStreams;
import com.google.inject.Injector;
import com.google.inject.servlet.RequestScoped;

@RequestScoped
public class ActionHandler {

	private final Logger log = LoggerFactory.getLogger(ActionHandler.class);

	private Injector injector;

	private ActionRequest request;

	private Context context;

	private ScriptBindings bindings;

	private ScriptHelper scriptHelper;
	
	private Pattern pattern = Pattern.compile("^(select\\[\\]|select|action|call|eval):\\s*(.*)");
	
	private ActionHandler(Injector injector, ActionRequest request) {

		Context context = request.getContext();
		if (context == null) {
			log.debug("null context for action: {}", request.getAction());
			context = Context.create(null, request.getBeanClass());
		}

		this.injector = injector;
		this.request = request;

		this.context = context;

		this.scriptHelper = new GroovyScriptHelper(this.context);
		this.bindings = this.scriptHelper.getBindings();
		this.bindings.put("__me__", this);
	}

	@Inject
	ActionHandler(Injector injector) {
		this.injector = injector;
	}
	
	public ActionHandler forRequest(ActionRequest request) {
		return new ActionHandler(injector, request);
	}
	
	public Injector getInjector() {
		return injector;
	}

	public Context getContext() {
		return context;
	}

	public ActionRequest getRequest() {
		return request;
	}

	/**
	 * Evaluate the given <code>expression</code>.
	 *
	 * @param expression
	 * 					the expression to evaluate prefixed with action type
	 * 					followed by a <code>:</code>
	 * @param references
	 * @return
	 * 					expression result
	 */
	public Object evaluate(String expression) {

		if (Strings.isNullOrEmpty(expression)) {
			return null;
		}

		String kind = null;
		String expr = expression;
		Matcher matcher = pattern.matcher(expression);

		if (matcher.matches()) {
			kind = matcher.group(1);
			expr = matcher.group(2);
		} else {
			return expr;
		}

		if ("eval".equals(kind)) {
			return handleGroovy(expr);
		}

		if ("action".equals(kind)) {
			return handleAction(expr);
		}

		if ("call".equals(kind)) {
			return handleCall(expr);
		}

		if ("select".equals(kind)) {
			return handleSelectOne(expr);
		}

		if ("select[]".equals(kind)) {
			return handleSelectAll(expr);
		}

		return expr;
	}

	public Object call(String className, String method) {
		ActionResponse response = new ActionResponse();
		try {
			Class<?> klass = Class.forName(className);
			Method m = klass.getMethod(method,
					ActionRequest.class,
					ActionResponse.class);
			Object obj = injector.getInstance(klass);
			m.invoke(obj, new Object[] { request, response });
		} catch (Exception e) {
			e.printStackTrace();
			response.setException(e);
		}
		return response;
	}

	public Object rpc(String className, String methodCall) {

		Pattern p = Pattern.compile("(\\w+)\\((.*?)\\)");
		Matcher m = p.matcher(methodCall);

		if (!m.matches()) {
			return null;
		}

		try {
			Class<?> klass = Class.forName(className);
			Object object = injector.getInstance(klass);
			return scriptHelper.call(object, methodCall);
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	public String template(Templates engine, Reader template) throws IOException {
		return engine.fromText(CharStreams.toString(template)).make(bindings).render();
	}

	@SuppressWarnings("all")
	private Query select(String query, Object... params) {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(query));
		if (!query.toLowerCase().startsWith("select "))
			query = "SELECT " + query;

		Query q = JPA.em().createQuery(query);
		QueryBinder.of(q).bind(bindings, params);

		return q;
	}

	public Object selectOne(String query, Object... params) {
		Query q = select(query, params);
		q.setMaxResults(1);
		try {
			return q.getResultList().get(0);
		} catch (Exception e) {
		}
		return null;
	}

	public Object selectAll(String query, Object... params) {
		try {
			return select(query, params).getResultList();
		} catch (Exception e) {
		}
		return null;
	}

	@SuppressWarnings("all")
	public Object search(Class<?> entityClass, String filter, Map params) {
		filter = makeMethodCall("filter", filter);
		filter = String.format("__repo__.of(%s.class).all().%s", entityClass.getName(), filter);
		com.axelor.db.Query q = (com.axelor.db.Query) handleGroovy(filter);

		q = q.bind(bindings);
		q = q.bind(params);

		return q.fetchOne();
	}

	private String makeMethodCall(String method, String expression) {
		expression = expression.trim();
		// check if expression is parameterized
		if (!expression.startsWith("(")) {
			if (!expression.matches("('|\")")) {
				expression = "\"\"\"" + expression + "\"\"\"";
			}
			expression = "(" + expression + ")";
		}
		return method + expression;
	}

	private Object handleSelectOne(String expression) {
		expression = makeMethodCall("__me__.selectOne", expression);
		return handleGroovy(expression);
	}

	private Object handleSelectAll(String expression) {
		expression = makeMethodCall("__me__.selectAll", expression);
		return handleGroovy(expression);
	}

	private Object handleGroovy(String expression) {
		return scriptHelper.eval(expression);
	}

	private Object handleAction(String expression) {

		Action action = MetaStore.getAction(expression);
		if (action == null) {
			log.debug("no such action found: {}", expression);
			return null;
		}

		return action.evaluate(this);
	}

	private Object handleCall(String expression) {

		if (Strings.isNullOrEmpty(expression))
			return null;

		String[] parts = expression.split("\\:");
		if (parts.length != 2) {
			log.error("Invalid call expression: ", expression);
			return null;
		}

		ActionMethod action = new ActionMethod();
		ActionMethod.Call call = new ActionMethod.Call();

		call.setController(parts[0]);
		call.setMethod(parts[1]);
		action.setCall(call);

		return action.evaluate(this);
	}

	private static final String KEY_VALUES = "values";
	private static final String KEY_ATTRS = "attrs";
	private static final String KEY_VALUE = "value";

	private Object toCompact(final Object item) {
		if (item == null) return null;
		if (item instanceof Collection) {
			return Collections2.transform((Collection<?>) item, new Function<Object, Object>() {
				@Override
				public Object apply(Object input) {
					return toCompact(input);
				}
			});
		}
		if (item instanceof Model) {
			Model bean = (Model) item;
			if (bean.getId() != null && JPA.em().contains(bean)) {
				return Resource.toMapCompact(bean);
			}
		}
		return item;
	}

	/**
	 * This method finds m2o values which are managed instances and converts
	 * them to compact maps to avoid unnecessary data transmission and prevents
	 * object graph recreation issues.
	 */
	@SuppressWarnings("all")
	private Object process(Object data) {
		if (data == null) return data;
		if (data instanceof Collection) {
			final List items = new ArrayList<>();
			for (Object item : (Collection) data) {
				items.add(process(item));
			}
			return items;
		}
		if (data instanceof Map) {
			final Map<String, Object> item = new HashMap<>((Map) data);
			if (item.containsKey(KEY_VALUES) && item.get(KEY_VALUES) instanceof Map) {
				final Map<String, Object> values = (Map) item.get(KEY_VALUES);
				for (String key : values.keySet()) {
					Object value = values.get(key);
					if (value instanceof Model) {
						values.put(key, toCompact(value));
					}
				}
			}
			if (item.containsKey(KEY_ATTRS) && item.get(KEY_ATTRS) instanceof Map) {
				final Map<String, Object> values = (Map) item.get(KEY_ATTRS);
				for (String key : values.keySet()) {
					final Map<String, Object> attrs = (Map) values.get(key);
					if (attrs.containsKey(KEY_VALUE)) {
						attrs.put(KEY_VALUE, toCompact(attrs.get(KEY_VALUE)));
					}
				}
			}
			return item;
		}
		return data;
	}

	public ActionResponse execute() {

		ActionResponse response = new ActionResponse();

		String name = request.getAction();
		if (name == null) {
			throw new NullPointerException("no action provided");
		}

		String[] names = name.split(",");
		ActionGroup action = new ActionGroup();
		for(String item : names) {
			action.addAction(item);
		}

		Object data = action.wrap(this);

		if (data instanceof ActionResponse) {
			return (ActionResponse) data;
		}

		response.setData(process(data));
		response.setStatus(ActionResponse.STATUS_SUCCESS);

		return response;
	}

}
