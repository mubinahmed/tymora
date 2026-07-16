/*
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * The Apereo Foundation licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
*/
package org.unitime.timetable.gwt.shared;

import java.util.ArrayList;
import java.util.List;

import org.unitime.timetable.gwt.command.client.GwtRpcRequest;
import org.unitime.timetable.gwt.command.client.GwtRpcResponse;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Generic read-only tabular listing for legacy Struts pages that have no other
 * GwtRpc command bean. One protocol (a {@code page} key) drives many entity
 * listings; the backend (SimpleListBackend) gates each by its permission and
 * projects the entities to string rows. The Angular /list/:page screen renders
 * columns + rows.
 *
 * @author Angular migration
 */
public class SimpleListInterface implements IsSerializable {

	public static class SimpleListRequest implements GwtRpcRequest<SimpleListResponse> {
		private String iPage;

		public SimpleListRequest() {}
		public SimpleListRequest(String page) { iPage = page; }

		public String getPage() { return iPage; }
		public void setPage(String page) { iPage = page; }

		@Override
		public String toString() { return "SimpleList[" + iPage + "]"; }
	}

	public static class SimpleListResponse implements GwtRpcResponse {
		private String iTitle;
		private List<String> iColumns = new ArrayList<String>();
		private List<Row> iRows = new ArrayList<Row>();

		public SimpleListResponse() {}

		public String getTitle() { return iTitle; }
		public void setTitle(String title) { iTitle = title; }

		public List<String> getColumns() { return iColumns; }
		public void addColumn(String column) { iColumns.add(column); }

		public List<Row> getRows() { return iRows; }
		public Row addRow(Long id) {
			Row row = new Row();
			row.setId(id);
			iRows.add(row);
			return row;
		}
	}

	public static class Row implements IsSerializable {
		private Long iId;
		private List<String> iCells = new ArrayList<String>();

		public Row() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public List<String> getCells() { return iCells; }
		public Row add(String cell) { iCells.add(cell == null ? "" : cell); return this; }
	}
}
