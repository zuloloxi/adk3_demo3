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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.axelor.JpaTest;
import com.axelor.test.db.Circle;
import com.axelor.test.db.Contact;
import com.google.common.collect.Maps;
import com.google.inject.persist.Transactional;

public class QueryTest extends JpaTest {

	@Test
	public void testCount() {
		Assert.assertTrue(all(Circle.class).count() > 0);
		Assert.assertTrue(all(Contact.class).count() > 0);
	}

	@Test
	public void testSimpleFilter() {

		String filter = "self.firstName < ? AND self.lastName LIKE ?";
		String expected = "SELECT self FROM Contact self WHERE self.firstName < ? AND self.lastName LIKE ?";

		Query<Contact> q = all(Contact.class).filter(filter, "some", "thing");

		Assert.assertEquals(expected, q.toString());
	}

	@Test
	public void testAdaptInt() {
		all(Contact.class).filter("self.id = ?", 1).fetchOne();
		all(Contact.class).filter("self.id IN (?, ?, ?)", 1, 2, 3).fetch();
	}

	@Test
	public void testAutoJoin() {

		String filter = "(self.addresses[].country.code = ?1 AND self.title.code = ?2) OR self.firstName = ?3";
		String expected = "SELECT self FROM Contact self "
				+ "LEFT JOIN self.addresses _addresses "
				+ "LEFT JOIN _addresses.country _addresses_country "
				+ "LEFT JOIN self.title _title "
				+ "WHERE (_addresses_country.code = ?1 AND _title.code = ?2) OR self.firstName = ?3 "
				+ "ORDER BY _addresses_country.name DESC";

		Query<Contact> q = all(Contact.class).filter(filter, "FR", "MR", "John").order("-addresses[].country.name");
		Assert.assertEquals(expected, q.toString());

		List<?> result = q.fetch();	
		Assert.assertNotNull(result);
		Assert.assertTrue(result.size() > 0);
	}

	@Test
	@Transactional
	public void testBulkUpdate() {
		Query<Contact> q = all(Contact.class).filter("self.title.code = ?1", "mr");
		for (Contact c : q.fetch()) {
			Assert.assertNull(c.getLang());
		}
		// managed instances are not affected with mass update
		// so clear the session to avoid unexpected results
		getEntityManager().clear();
		q.update("self.lang", "EN");
		for (Contact c : q.fetch()) {
			Assert.assertEquals("EN", c.getLang());
		}
	}

	@Test
	public void testJDBC() {

		jdbcTask(new JDBCTask() {

			@Override
			public void execute(Connection connection) throws SQLException {
				Statement stm = connection.createStatement();
				ResultSet rs = stm.executeQuery("SELECT * FROM contact_title");
				ResultSetMetaData meta = rs.getMetaData();
				while (rs.next()) {
					Map<String, Object> item = Maps.newHashMap();
					for (int i = 0; i < meta.getColumnCount(); i++) {
						item.put(meta.getColumnName(i + 1), rs.getObject(i + 1));
					}
					Assert.assertFalse(item.isEmpty());
				}
			}
		});
	}
}
