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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.meta.ActionHandler;
import com.axelor.meta.MetaStore;
import com.axelor.rpc.Response;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@XmlType
public class ActionGroup extends ActionResumable {

	@XmlElement(name = "action")
	private List<ActionItem> actions;

	public List<ActionItem> getActions() {
		return actions;
	}

	public void setActions(List<ActionItem> actions) {
		this.actions = actions;
	}

	public void addAction(String name) {
		if (this.actions == null) {
			this.actions = Lists.newArrayList();
		}
		ActionItem item = new ActionItem();
		item.setName(name);
		this.actions.add(item);
	}

	private String getPending(int index, String... prepend) {
		final List<String> pending = Lists.newArrayList(prepend);
		if ((index + 1) < actions.size()) {
			String name = getName();
			if (name == null) { // dummy group
				for (int i = index + 1; i < actions.size(); i++) {
					pending.add(actions.get(i).getName());
				}
			} else {
				pending.add(name + "[" + (index + 1) + "]");
			}
    	}
    	return Joiner.on(",").skipNulls().join(pending);
	}

	private Action findAction(String name) {

		if (name == null || "".equals(name.trim())) {
			return null;
		}

		String actionName = name.trim();

		if (actionName.contains(":")) {

			Action action;
			String[] parts = name.split("\\:", 2);

			if (parts[0].matches("grid|form|tree|portal|calendar|chart|search|html")) {
				ActionView.View view = new ActionView.View();
				view.setType(parts[0]);
				view.setName(parts[1]);
				action = new ActionView();
				((ActionView) action).setViews(ImmutableList.of(view));
			} else {
				ActionMethod.Call method = new ActionMethod.Call();
				method.setController(parts[0]);
				method.setMethod(parts[1]);
				action = new ActionMethod();
				((ActionMethod) action).setCall(method);
			}
			return action;
		} else if (actionName.indexOf("[") > -1 && actionName.endsWith("]")) {
				String idx = actionName.substring(actionName.lastIndexOf('[') + 1, actionName.lastIndexOf(']'));
				actionName = actionName.substring(0, actionName.lastIndexOf('['));
				int index = Integer.parseInt(idx);
				log.debug("continue action: {}", actionName);
				log.debug("continue at: {}", index);
				Action action = MetaStore.getAction(actionName);
				if (action instanceof ActionResumable) {
					return ((ActionResumable) action).resumeAt(index);
				}
				return action;
		}

		return MetaStore.getAction(actionName);
	}

	@Override
	protected ActionGroup copy() {
		final ActionGroup action = new ActionGroup();
		final List<ActionItem> items = new ArrayList<>(actions);
		action.setName(getName());
		action.setModel(getModel());
		action.setActions(items);
		return action;
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Object evaluate(ActionHandler handler) {

		List<Object> result = Lists.newArrayList();
		Iterator<ActionItem> iter = actions.iterator();

		if (getName() != null) {
			log.debug("action-group: {}", getName());
		}
		
		for (int i = getIndex(); i < actions.size(); i++) {

			Element element = actions.get(i);
			String name = element.getName().trim();

			log.debug("action: {}", name);

			if ("save".equals(name) || "validate".equals(name) || "close".equals(name) || "new".equals(name)) {
				if (!element.test(handler)) {
					log.debug("action '{}' doesn't meet the condition: {}", name, element.getCondition());
					continue;
				}
				String pending = this.getPending(i);
				Map<String, Object> res = Maps.newHashMap();
				res.put(name, true);
				res.put("pending", pending);
				result.add(res);
				if (!StringUtils.isBlank(pending)) {
					log.debug("wait for '{}', pending actions: {}", name, pending);
				}
				break;
			}

			Action action = this.findAction(name);
			if (action == null) {
				log.warn("action doesn't exist or module dependency is not resolved: {}", name);
                continue;
			}

			if (!element.test(handler)) {
				log.debug("action '{}' doesn't meet the condition: {}", element.getName(), element.getCondition());
				continue;
			}

			Object value = action.wrap(handler);
            if (value instanceof Response) {
            	Response res = (Response) value;
            	// if this is the only action then return the response
            	if (res.getStatus() != Response.STATUS_SUCCESS || actions.size() == 1) {
            		return res;
            	}
            	
            	value = res.getItem(0);
            	
            	// if error then concat the response result with result of previous actions and quit
            	if(!ObjectUtils.isEmpty(res.getErrors())) {
            		Map<String, Object> resValues = Maps.newHashMap();
            		resValues.put("data", res.getItem(0));
            		resValues.put("errors", res.getErrors());
            		
                	// skip next actions if contains errors
                	if(hasErrors(resValues)) {
                		result.add(resValues);
                    	break;
                	} else {
                		value = resValues;
                	}
            	}
            	
            }
            if (value == null) {
            	continue;
            }

            // update the context if required
            if (value instanceof Map) {
            	updateContext(handler, (Map) value);
            }
            
            if (action instanceof ActionGroup && value instanceof Collection) {
            	result.addAll((Collection<?>) value);
            } else {
            	result.add(value);
            }

            // stop for reload
            if (value instanceof Map && Objects.equal(Boolean.TRUE, ((Map) value).get("reload"))) {
            	String pending = this.getPending(i);
            	log.debug("wait for 'reload', pending actions: {}", pending);
				((Map<String, Object>) value).put("pending", pending);
				break;
            }

            if (action instanceof ActionValidate && value instanceof Map) {
            	String validate = (String) ((Map) value).get("pending");
            	String pending = this.getPending(i, validate);
            	log.debug("wait for validation: {}, {}", name, value);
            	log.debug("pending actions: {}", pending);
            	((Map<String, Object>) value).put("pending", pending);
                break;
            }

            if (action instanceof ActionCondition) {
            	if(Objects.equal(value, Boolean.FALSE) || 
        			(value instanceof Map && hasErrors((Map) value))) {
            		break;
            	}
            }

            if ((action instanceof ActionGroup || action instanceof ActionMethod)
            	&& !result.isEmpty() && iter.hasNext()) {
            	Map<String, Object> last = null;
            	try {
            		last = (Map) result.get(result.size() - 1);
            	} catch (ClassCastException e) {
            	}
            	if (last == null) continue;
            	if (Objects.equal(Boolean.TRUE, last.get("reload")) ||
            		last.containsKey("info") ||
                	last.containsKey("alert") ||
                	last.containsKey("error") ||
                	last.containsKey("save") ||
                	last.containsKey("validate") ||
                	last.containsKey("close") ||
                	last.containsKey("new")) {
            		String previous = (String) last.get("pending");
            		String pending = this.getPending(i, previous);
            		last.put("pending", pending);
            		log.debug("wait for group: {}", action.getName());
            		log.debug("pending actions: {}", pending);
            		break;
            	}
            }
		}
		return result;
	}
	
	@SuppressWarnings("rawtypes")
	private Boolean hasErrors(Map<String, Object> value) {
		if (ObjectUtils.isEmpty(value)) return Boolean.FALSE;
		
		Object errors = value.get("errors");
		if(errors instanceof Map) {
			for (Object key : ((Map) errors).keySet()) {
				String error = (String) ((Map) errors).get(key);
				if(!StringUtils.isEmpty(error)) {
					return Boolean.TRUE;
				}
			}
		}
		
		return Boolean.FALSE;
	}
	
	@SuppressWarnings("all")
	private void updateContext(ActionHandler handler, Map<String, Object> value) {
		if (value == null) return;
		
		Object values = value.get("values");
		Map<String, Object> map = Maps.newHashMap();
		
    	if (values instanceof Model) {
    		map = Mapper.toMap(value);
    	}
    	if (values instanceof Map) {
    		map = Maps.newHashMap((Map) values);
    	}
    	
    	values = value.get("attrs");
    	
    	if (values instanceof Map) {
    		for (Object key : ((Map) values).keySet()) {
    			String name = key.toString();
    			Map attrs = (Map) ((Map) values).get(key);
    			if (name.indexOf('$') == 0) name = name.substring(1);
    			if (attrs.containsKey("value")) {
    				map.put(name, attrs.get("value"));
    			}
    			if (attrs.containsKey("value:set")) {
    				map.put(name, attrs.get("value:set"));
    			}
    		}
    	}
    	
    	handler.getContext().update(map);
	}

	@Override
	public Object wrap(ActionHandler handler) {
		return evaluate(handler);
	}

	public static class ActionItem extends Element {

	}

}
