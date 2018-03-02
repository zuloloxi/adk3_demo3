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
package com.axelor.data;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.data.csv.CSVImporter;
import com.axelor.db.Model;
import com.axelor.test.GuiceModules;
import com.axelor.test.GuiceRunner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Injector;

@RunWith(GuiceRunner.class)
@GuiceModules(DataModule.class)
public class CSVImportTest {

	protected final transient Logger log = LoggerFactory.getLogger(CSVImportTest.class);

	@Inject
	Injector injector;

	@Test
	public void test() throws ClassNotFoundException {
		final List<Model> records = Lists.newArrayList();
		CSVImporter importer = new CSVImporter(injector, "data/csv-multi-config.xml");

		Map<String, Object> context = Maps.newHashMap();

		context.put("CUSTOMER_PHONE", "+3326253225");

		importer.setContext(context);

		importer.addListener(new Listener() {
			@Override
			public void imported(Model bean) {
				log.info("Bean saved : {}(id={})",
						bean.getClass().getSimpleName(),
						bean.getId());
				records.add(bean);
			}

			@Override
			public void imported(Integer total, Integer success) {
				log.info("Record (total): " + total);
				log.info("Record (success): " + success);
			}

			@Override
			public void handle(Model bean, Exception e) {

			}
		});

		importer.runTask(new ImportTask(){

			@Override
			public void configure() throws IOException {
				input("[sale.order]", new File("data/csv-multi/so1.csv"));
				input("[sale.order]", new File("data/csv-multi/so2.csv"));
			}

			@Override
			public boolean handle(ImportException exception) {
				log.error("Import error : " + exception);
				return false;
			}

			@Override
			public boolean handle(IOException e) {
				log.error("IOException error : " + e);
				return true;
			}

		});


	}

}
