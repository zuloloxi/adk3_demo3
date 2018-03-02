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
package com.axelor.web.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import com.axelor.app.AppSettings;
import com.axelor.common.FileUtils;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.db.mapper.Mapper;
import com.axelor.meta.ActionHandler;
import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaAttachment;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.schema.actions.Action;
import com.axelor.meta.service.MetaService;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Request;
import com.axelor.rpc.Response;
import com.google.common.base.CaseFormat;
import com.google.common.base.Charsets;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.inject.servlet.RequestScoped;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataParam;

@RequestScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/rest/{model}")
public class RestService extends ResourceService {

	@Inject
	private MetaService service;
	
	@Inject
	private ActionHandler handler;

	@GET
	public Response find(
			@QueryParam("limit")
			@DefaultValue("40") int limit,
			@QueryParam("offset")
			@DefaultValue("0") int offset,
			@QueryParam("q") String query) {

		Request request = new Request();
		request.setOffset(offset);
		request.setLimit(limit);
		return getResource().search(request);
	}

	@POST
	@Path("search")
	@SuppressWarnings("all")
	public Response find(Request request) {
		
		request.setModel(getModel());

		final Map<String, Object> data = request.getData();
		if (data == null || !data.containsKey("_domainAction")) {
			return getResource().search(request);
		}

		// domain actions are used by portlets to evaluate view context
		// in the context of the current form

		final String action = (String) data.get("_domainAction");
		final Action act = MetaStore.getAction(action);
		final ActionRequest actRequest = new ActionRequest();
		
		actRequest.setModel(getModel());
		actRequest.setAction(action);
		actRequest.setData(data);

		Object res = act.evaluate(handler.forRequest(actRequest));

		if (res instanceof ActionResponse) {
			res = ((ActionResponse) res).getItem(0);
			if (res instanceof Map && ((Map) res).containsKey("view")) {
				res = ((Map) res).get("view");
			}
		}

		if (res instanceof Map && ((Map) res).containsKey("context")) {
			Map old = (Map) data.get("_domainContext");
			Map ctx = (Map) ((Map) res).get("context");
			if (ctx != null) {
				if (old == null) {
					((Map) data).put("_domainContext", ctx);
				} else {
					old.putAll(ctx);
				}
			}
		}

		return getResource().search(request);
	}

	@POST
	public Response save(Request request) {
		request.setModel(getModel());
		return getResource().save(request);
	}

	@PUT
	public Response create(Request request) {
		request.setModel(getModel());
		return getResource().save(request);
	}

	@GET
	@Path("{id}")
	public Response read(
			@PathParam("id") long id) {
		return getResource().read(id);
	}

	@POST
	@Path("{id}/fetch")
	public Response fetch(
			@PathParam("id") long id, Request request) {

		Response response = getResource().fetch(id, request);
		long attachments = Query.of(MetaAttachment.class)
				.filter("self.objectId = ?1 AND self.objectName = ?2", id, getModel())
				.cacheable().count();

		if(response.getItem(0) != null) {
			@SuppressWarnings("all")
			Map<String, Object> item = (Map) response.getItem(0);
			item.put("$attachments", attachments);
		}


		return response;
	}

	@POST
	@Path("{id}")
	public Response update(@PathParam("id") long id, Request request) {
		request.setModel(getModel());
		return getResource().save(request);
	}

	@POST
	@Path("updateMass")
	public Response updateMass(Request request) {
		request.setModel(getModel());
		return getResource().updateMass(request);
	}

	@DELETE
	@Path("{id}")
	public Response delete(@PathParam("id") long id, @QueryParam("version") int version) {
		Request request = new Request();
		request.setModel(getModel());
		request.setData(ImmutableMap.of("id", (Object) id, "version", version));
		return getResource().remove(id, request);
	}

	@POST
	@Path("{id}/remove")
	public Response remove(@PathParam("id") long id, Request request) {
		request.setModel(getModel());
		return getResource().remove(id, request);
	}

	@POST
	@Path("removeAll")
	public Response remove(Request request) {
		request.setModel(getModel());
		return getResource().remove(request);
	}

	@GET
	@Path("{id}/copy")
	public Response copy(@PathParam("id") long id) {
		return getResource().copy(id);
	}

	@GET
	@Path("{id}/details")
	public Response details(@PathParam("id") long id, @QueryParam("name") String name) {
		Request request = new Request();
		Map<String, Object> data = new HashMap<String, Object>();

		data.put("id", id);
		request.setModel(getModel());
		request.setData(data);
		request.setFields(Lists.newArrayList(name));

		return getResource().getRecordName(request);
	}
	
	private static final String DEFAULT_UPLOAD_PATH = "{java.io.tmpdir}/axelor/attachments";
	private static String uploadPath = AppSettings.get().getPath("file.upload.dir", DEFAULT_UPLOAD_PATH);

	private void uploadSave(InputStream in, OutputStream out) throws IOException {
		int read = 0;
		byte[] bytes = new byte[1024];
		while ((read = in.read(bytes)) != -1) {
			out.write(bytes, 0, read);
		}
		out.close();
	}

	@POST
	@Path("upload")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response upload(
			@FormDataParam("request") FormDataBodyPart requestText,
			@FormDataParam("field") String field,
			@FormDataParam("file") InputStream fileStream,
			@FormDataParam("file") FormDataContentDisposition fileDetails) throws IOException {

		requestText.setMediaType(MediaType.APPLICATION_JSON_TYPE);


		boolean isAttachment = MetaFile.class.getName().equals(getModel());

		Request request = requestText.getEntityAs(Request.class);
		request.setModel(getModel());

		Map<String, Object> data = request.getData();

		if (!isAttachment) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			uploadSave(fileStream, out);
			data.put(field, out.toByteArray());
		}

		String filePath = fileDetails.getFileName();
		if (isAttachment) {
			int maxCounter = 1000;
			long counter = 0;
			while (FileUtils.getFile(uploadPath, filePath).exists()) {
				if (counter++ > maxCounter) {
					counter = System.currentTimeMillis();
				}
				filePath = fileDetails.getFileName();
				filePath = Files.getNameWithoutExtension(filePath) + " (" + counter + ")." + Files.getFileExtension(filePath);
				if (counter > maxCounter) {
					break;
				}
			}
			data.put("filePath", filePath);
		}

		Response response = getResource().save(request);
		if (isAttachment && response.getStatus() == Response.STATUS_SUCCESS) {
			File file = FileUtils.getFile(uploadPath, filePath);
			Files.createParentDirs(file);
			FileOutputStream out = new FileOutputStream(file);
			uploadSave(fileStream, out);
		}

		return response;
	}

	@GET
	@Path("{id}/{field}/download")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@SuppressWarnings("all")
	public javax.ws.rs.core.Response download(
			@PathParam("id") Long id,
			@PathParam("field") String field) throws IOException {

		boolean isAttachment = MetaFile.class.getName().equals(getModel());

		Class klass = getResource().getModel();
		Mapper mapper = Mapper.of(klass);
		Model bean = JPA.find(klass, id);

		if (isAttachment) {
			final String fileName = (String) mapper.get(bean, "fileName");
			final String filePath = (String) mapper.get(bean, "filePath");
			final File inputFile = FileUtils.getFile(uploadPath, filePath);
			if (!inputFile.exists()) {
				return javax.ws.rs.core.Response.status(Status.NOT_FOUND).build();
			}
			return javax.ws.rs.core.Response.ok(new StreamingOutput() {

				@Override
				public void write(OutputStream output) throws IOException, WebApplicationException {
					uploadSave(new FileInputStream(inputFile), output);
				}
			})
			.header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
			.build();
		}

		String fileName = getModel() + "_" + field;
		Object data = mapper.get(bean, field);

		fileName = fileName.replaceAll("\\s", "") + "_" + id;
		fileName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, fileName);

		if (data == null) {
			return javax.ws.rs.core.Response.noContent().build();
		}
		return javax.ws.rs.core.Response
				.ok(data)
				.header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
				.build();
	}

	@POST
	@Path("{id}/attachment")
	public Response attachment(@PathParam("id") long id, Request request){
		return service.getAttachment(id, getModel(), request);
	}

	@POST
	@Path("removeAttachment")
	public Response removeAttachment(Request request) {
		request.setModel(getModel());
		return service.removeAttachment(request, uploadPath);
	}

	@POST
	@Path("{id}/addAttachment")
	public Response addAttachment(@PathParam("id") long id, Request request) {
		request.setModel(getModel());
		return service.addAttachment(id, request);
	}

	@POST
	@Path("verify")
	public Response verify(Request request) {
		request.setModel(getModel());
		return getResource().verify(request);
	}

	@GET
	@Path("perms")
	public Response perms(@QueryParam("action") String action, @QueryParam("id") Long id) {
		if (action != null && id != null) {
			return getResource().perms(id, action);
		}
		if (id != null) {
			return getResource().perms(id);
		}
		return getResource().perms();
	}

	private static final Cache<String, Request> EXPORT_REQUESTS = CacheBuilder
			.newBuilder()
			.expireAfterAccess(1, TimeUnit.MINUTES)
			.build();

	private static Charset csvCharset = Charsets.ISO_8859_1;
	static {
		try {
			csvCharset = Charset.forName(
					AppSettings.get().get("data.export.encoding"));
		} catch (Exception e) {
		}
	}

	@GET
	@Path("export/{name}")
	@Produces("text/csv")
	public StreamingOutput export(@PathParam("name") final String name) {

		final Request request = EXPORT_REQUESTS.getIfPresent(name);
		if (request == null) {
			throw new IllegalArgumentException(name);
		}

		return new StreamingOutput() {

			@Override
			public void write(OutputStream output) throws IOException, WebApplicationException {
				Writer writer = new OutputStreamWriter(output, csvCharset);
				try {
					getResource().export(request, writer);
				} finally {
					writer.close();
					EXPORT_REQUESTS.invalidate(name);
				}
			}
		};
	}

	@POST
	@Path("export")
	public Response export(Request request) {

		Response response = new Response();
		Map<String, Object> data = Maps.newHashMap();
		String fileName = Math.abs(new SecureRandom().nextLong()) + ".csv";

		EXPORT_REQUESTS.put(fileName, request);

		request.setModel(getModel());
		response.setData(data);

		data.put("fileName", fileName);

		return response;
	}

}