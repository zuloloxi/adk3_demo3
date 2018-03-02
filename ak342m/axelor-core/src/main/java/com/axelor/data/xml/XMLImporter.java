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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.FlushModeType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.axelor.data.ImportException;
import com.axelor.data.ImportTask;
import com.axelor.data.Importer;
import com.axelor.data.Listener;
import com.axelor.data.adapter.DataAdapter;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.inject.Injector;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;
import com.thoughtworks.xstream.mapper.MapperWrapper;

/**
 * XML data importer.
 * <br>
 * <br>
 * This class also provides {@link #runTask(ImportTask)} method to import data programatically.
 * <br>
 * <br>
 * For example:
 * <pre> 
 * XMLImporter importer = new XMLImporter(injector, &quot;path/to/xml-config.xml&quot;);
 * 
 * importer.runTask(new ImportTask(){
 * 	
 * 	public void configure() throws IOException {
 * 		input(&quot;contacts.xml&quot;, new File(&quot;data/xml/contacts.xml&quot;));
 * 		input(&quot;contacts.xml&quot;, new File(&quot;data/xml/contacts2.xml&quot;));
 * 	}
 * 
 * 	public boolean handle(ImportException e) {
 * 		System.err.println("Import error: " + e);
 * 		return true;
 * 	}
 * }
 * </pre>
 * 
 */
public class XMLImporter implements Importer {

	private Logger log = LoggerFactory.getLogger(getClass());
	
	private Injector injector;
	
	private File dataDir;
	
	private XMLConfig config;
	
	private Map<String, Object> context;

	private List<Listener> listeners = Lists.newArrayList();

	private boolean canClear = true;

	@Inject
	public XMLImporter(Injector injector,
			@Named("axelor.data.config") String configFile,
			@Named("axelor.data.dir") String dataDir) {

		Preconditions.checkNotNull(injector);
		Preconditions.checkNotNull(configFile);

		File _file = new File(configFile);

		Preconditions.checkArgument(_file.isFile(), "No such file: " + configFile);
		
		if (dataDir != null) {
			File _data = new File(dataDir);
			Preconditions.checkArgument(_data.isDirectory());
			this.dataDir = _data;
		}

		this.injector = injector;
		this.config = XMLConfig.parse(_file);
	}

	public XMLImporter(Injector injector, String configFile) {
		this(injector, configFile, null);
	}
	
	public void setContext(Map<String, Object> context) {
		this.context = context;
	}

	private List<File> getFiles(String... names) {
		List<File> all = Lists.newArrayList();
		for (String name : names)
			all.add(dataDir != null ? new File(dataDir, name) : new File(name));
		return all;
	}
	
	public void addListener(Listener listener) {
		this.listeners.add(listener);
	}

	public void clearListener() {
		this.listeners.clear();
	}

	public void setCanClear(boolean canClear) {
		this.canClear = canClear;
	}
	
	private int getBatchSize() {
		try {
			Object val = JPA.em().getEntityManagerFactory().getProperties().get("hibernate.jdbc.batch_size");
			return Integer.parseInt(val.toString());
		} catch (Exception e) {
		}
		return DEFAULT_BATCH_SIZE;
	}

	@Override
	public void run(Map<String, String[]> mappings) {
		
		if (mappings == null) {
			mappings = new HashMap<String, String[]>();
		}
		
		for (XMLInput input : config.getInputs()) {
			
			String fileName = input.getFileName();
			
			Pattern pattern = Pattern.compile("\\[([\\w.]+)\\]");
			Matcher matcher = pattern.matcher(fileName);
			
			List<File> files = matcher.matches() ?
					this.getFiles(mappings.get(matcher.group(1))) :
					this.getFiles(fileName);
			
			for(File file : files) {
				try {
					this.process(input, file);
				} catch (Exception e) {
					log.error("Error while importing {}.", file, e);
				}
			}
		}
	}
	
	public void runTask(ImportTask task) {
		try {
			if (task.readers.isEmpty()) {
				task.configure();
			}
			for (XMLInput input : config.getInputs()) {
				for(Reader reader : task.readers.get(input.getFileName())) {
					try {
						process(input, reader);
					} catch(ImportException e) {
						if (!task.handle(e)) {
							break;
						}
					}
				}
			}
		} catch(IOException e) {
			throw new IllegalArgumentException(e);
		} finally {
			task.readers.clear();
		}
	}

	/**
	 * Process the given key -> reader multi-mapping to import data from some streams.
	 * 
	 * @param readers multi-value mapping of filename -> reader
	 * @throws ImportException
	 * 
	 * @see {@link #input(String, File)}
	 * @see {@link #input(String, InputStream)}
	 * @see {@link #input(String, Reader)}
	 * @see {@link #consume()}
	 */
	@Deprecated
	public void process(Multimap<String, Reader> readers) throws ImportException {
		
		Preconditions.checkNotNull(config);
		Preconditions.checkNotNull(readers);

		for (XMLInput input : config.getInputs()) {
			for(Reader reader : readers.get(input.getFileName()))
				this.process(input, reader);
		}
	}
	
	/**
	 * Process the data file with the given input binding.
	 * 
	 * @param input input binding configuration
	 * @param file data file
	 * @throws ImportException
	 */
	private void process(XMLInput input, File file) throws ImportException {
		try {
			log.info("Importing: {}", file.getName());
			this.process(input, new FileReader(file));
		} catch (IOException e) {
			throw new ImportException(e);
		}
	}
	
	private void process(XMLInput input, Reader reader) throws ImportException {

		final int batchSize = getBatchSize();

		final XStream stream = new XStream(new StaxDriver()) {

			private String root = null;

			@Override
			@SuppressWarnings("all")
			protected MapperWrapper wrapMapper(MapperWrapper next) {
				
				return new MapperWrapper(next) {
					
					@Override
					public Class realClass(String elementName) {
						if (root == null) {
							root = elementName;
							return Document.class;
						}
						return Element.class;
					}
				};
			}
		};
		
		final XMLBinder binder = new XMLBinder(input, context) {
			
			int count = 0;
			int total = 0;
			
			@Override
			protected void handle(Object bean, XMLBind binding, Map<String, Object> ctx) {
				if (bean == null) {
					return;
				}
				try {
					bean = binding.call(bean, ctx, injector);
					if (bean != null) {
						bean = JPA.manage((Model) bean);
						count++;
						for(Listener listener : listeners) {
							listener.imported((Model) bean);
						}
					}
				} catch (Exception e) {
					log.error("Unable to import object {}.", bean);
					log.error("With binding {}.", binding);
					log.error("With exception:", e);
					
					// Recover the transaction
					if (JPA.em().getTransaction().getRollbackOnly()) {
						JPA.em().getTransaction().rollback();
					}
					if (!JPA.em().getTransaction().isActive()) {
						JPA.em().getTransaction().begin();
					}
					
					for(Listener listener : listeners) {
						listener.handle((Model) bean, e);
					}
				}
				if (++total % batchSize == 0) {
					JPA.flush();
					JPA.clear();
				}
			}
			
			@Override
			protected void finish() {
				for(Listener listener : listeners) {
					listener.imported(total, count);
				}
			}
		};

		// register type adapters
		for(DataAdapter adapter : defaultAdapters) {
			binder.registerAdapter(adapter);
		}
		for(DataAdapter adapter : config.getAdapters()) {
			binder.registerAdapter(adapter);
		}
		for(DataAdapter adapter : input.getAdapters()) {
			binder.registerAdapter(adapter);
		}

		stream.setMode(XStream.NO_REFERENCES);
		stream.registerConverter(new ElementConverter(binder));
		
		final EntityManager em = JPA.em();
		final EntityTransaction txn = em.getTransaction();
		final boolean started = !txn.isActive();

		if (canClear) {
			em.setFlushMode(FlushModeType.COMMIT);
		}
		if (started) {
			txn.begin();
		}

		try {
			stream.fromXML(reader);
			binder.finish();
			if (txn.isActive() && started) {
				txn.commit();
				if (canClear) {
					em.clear();
				}
			}
		} catch (Exception e) {
			if (txn.isActive() && started) {
				txn.rollback();
			}
			throw new ImportException(e);
		}
	}
}
