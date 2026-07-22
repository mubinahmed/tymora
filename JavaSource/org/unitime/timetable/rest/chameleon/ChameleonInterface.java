/*
 * Angular migration: additive command DTOs for the "chameleon" (masquerade)
 * screen. Wraps the existing ChameleonAction logic so Angular can list the
 * candidate users and switch, without touching the legacy screen.
 */
package org.unitime.timetable.rest.chameleon;

import java.util.ArrayList;
import java.util.List;

import org.unitime.timetable.gwt.command.client.GwtRpcRequest;
import org.unitime.timetable.gwt.command.client.GwtRpcResponse;
import org.unitime.timetable.gwt.command.client.GwtRpcResponseNull;

public class ChameleonInterface {

	public static class ChameleonUserItem {
		private String puid;
		private String name;
		public ChameleonUserItem() {}
		public ChameleonUserItem(String puid, String name) { this.puid = puid; this.name = name; }
		public String getPuid() { return puid; }
		public String getName() { return name; }
	}

	public static class GetChameleonUsersRequest implements GwtRpcRequest<GetChameleonUsersResponse> {}

	public static class GetChameleonUsersResponse implements GwtRpcResponse {
		private String currentName;
		private boolean masquerading;
		private List<ChameleonUserItem> users = new ArrayList<ChameleonUserItem>();
		public String getCurrentName() { return currentName; }
		public void setCurrentName(String currentName) { this.currentName = currentName; }
		public boolean isMasquerading() { return masquerading; }
		public void setMasquerading(boolean masquerading) { this.masquerading = masquerading; }
		public List<ChameleonUserItem> getUsers() { return users; }
		public void addUser(ChameleonUserItem u) { users.add(u); }
	}

	/** puid == null (or the original user's puid) stops masquerading. */
	public static class ChameleonSwitchRequest implements GwtRpcRequest<GwtRpcResponseNull> {
		private String puid;
		private String name;
		public ChameleonSwitchRequest() {}
		public String getPuid() { return puid; }
		public void setPuid(String puid) { this.puid = puid; }
		public String getName() { return name; }
		public void setName(String name) { this.name = name; }
	}
}
