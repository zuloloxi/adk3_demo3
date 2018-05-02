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
package com.axelor.db;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;

import org.hibernate.proxy.HibernateProxy;

import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.db.mapper.PropertyType;
import com.axelor.inject.Beans;

public class JpaRepository<T extends Model> implements Repository<T> {

	protected Class<T> modelClass;

	protected JpaRepository(Class<T> modelClass) {
		this.modelClass = modelClass;
	}

	@Override
	public List<Property> fields() {
		final Property[] fields = JPA.fields(modelClass);
		if (fields == null) {
			return null;
		}
		return Arrays.asList(fields);
	}

	@Override
	public Query<T> all() {
		return JPA.all(modelClass);
	}

	/**
	 * Get the {@link Query} instance of the given type.
	 *
	 * @param type
	 *            the subtype of the managed model class.
	 * @return instance of {@link Query}
	 */
	public <U extends T> Query<U> all(Class<U> type) {
		return JPA.all(type);
	}

	@Override
	public T create(Map<String, Object> values) {
		return Mapper.toBean(modelClass, values);
	}

	@Override
	public T copy(T entity, boolean deep) {
		return JPA.copy(entity, deep);
	}

	@Override
	public T find(Long id) {
		return JPA.find(modelClass, id);
	}

	@Override
	public T save(T entity) {
		return JPA.save(entity);
	}

	/**
	 * Make an entity managed and persistent.
	 * 
	 * @see EntityManager#persist(Object)
	 */
	public void persist(T entity) {
		JPA.persist(entity);
	}

	/**
	 * Merge the state of the given entity into the current persistence context.
	 * 
	 * @see EntityManager#merge(Object)
	 */
	public T merge(T entity) {
		return JPA.merge(entity);
	}

	@Override
	public void remove(T entity) {
		// detach orphan o2m records
		detachChildren(entity);
		JPA.remove(entity);
	}

	@SuppressWarnings("all")
	private void detachChildren(T entity) {
		if (!JPA.em().contains(entity)) {
			return;
		}
		Class<?> entityClass = entity.getClass();
		if (entity instanceof HibernateProxy) {
			entityClass = ((HibernateProxy) entity).getHibernateLazyInitializer().getPersistentClass();
		}
		final Mapper mapper = Mapper.of(entityClass);
		for (Property property : mapper.getProperties()) {
			if (property.getType() != PropertyType.ONE_TO_MANY || !property.isOrphan()) {
				continue;
			}
			final Property mappedBy = mapper.of(property.getTarget()).getProperty(property.getMappedBy());
			if (mappedBy != null && mappedBy.isRequired()) {
				continue;
			}
			final Collection<? extends Model> items = (Collection) property.get(entity);
			if (items == null || items.size() == 0) {
				continue;
			}
			for (Model item : items) {
				property.setAssociation(item, null);
			}
		}
	}

	/**
	 * Refresh the state of the instance from the database, overwriting changes
	 * made to the entity, if any.
	 *
	 * @see EntityManager#refresh(Object)
	 */
	@Override
	public void refresh(T entity) {
		JPA.refresh(entity);
	}

	/**
	 * Synchronize the persistence context to the underlying database.
	 *
	 * @see EntityManager#flush()
	 */
	@Override
	public void flush() {
		JPA.flush();
	}

	@SuppressWarnings("unchecked")
	public static  <U extends Model>  JpaRepository<U> of(Class<U> type) {
		final Class<?> klass = JpaScanner.findRepository(type.getSimpleName() + "Repository");
		if (klass == null) {
			return null;
		}
		return (JpaRepository<U>) Beans.get(klass);
	}
}

