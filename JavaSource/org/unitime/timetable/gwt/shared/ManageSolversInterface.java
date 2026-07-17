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
 * Read-only listing behind the legacy "Manage Solvers" page (manageSolvers.action).
 * Enumerates the active solver instances (course / examination / student sectioning /
 * instructor scheduling) currently loaded on the in-memory solver server(s), plus the
 * available solver servers themselves. Purely a report - no create/edit/delete
 * operations are exposed (all mutating operations of the legacy page - Select, Unload,
 * Shutdown, Reset, Reconnect, Enable/Disable - are intentionally omitted).
 *
 * @author Angular migration
 */
public class ManageSolversInterface implements IsSerializable {

	public static class ManageSolversRequest implements GwtRpcRequest<ManageSolversResponse> {
		public ManageSolversRequest() {}

		@Override
		public String toString() { return "ManageSolvers[]"; }
	}

	/**
	 * Mutating command behind the "Manage Solvers" page (legacy manageSolvers.action ops).
	 * Carries the operation name plus the identity of the target solver instance / server.
	 * Reuses {@link ManageSolversResponse} so the client gets a freshly rebuilt list back.
	 */
	public static class ManageSolversOpRequest implements GwtRpcRequest<ManageSolversResponse> {
		private String iOperation;
		private String iHost;
		private String iOwner;
		private String iType;
		private Long iOnlineId;

		public ManageSolversOpRequest() {}

		public String getOperation() { return iOperation; }
		public void setOperation(String operation) { iOperation = operation; }

		public String getHost() { return iHost; }
		public void setHost(String host) { iHost = host; }

		public String getOwner() { return iOwner; }
		public void setOwner(String owner) { iOwner = owner; }

		public String getType() { return iType; }
		public void setType(String type) { iType = type; }

		public Long getOnlineId() { return iOnlineId; }
		public void setOnlineId(Long onlineId) { iOnlineId = onlineId; }

		@Override
		public String toString() { return "ManageSolversOp[" + iOperation + "]"; }
	}

	public static class ManageSolversResponse implements GwtRpcResponse {
		private List<SolverInstanceInterface> iSolvers = new ArrayList<SolverInstanceInterface>();
		private List<SolverServerInterface> iServers = new ArrayList<SolverServerInterface>();
		private String iNavigate;

		public ManageSolversResponse() {}

		public List<SolverInstanceInterface> getSolvers() { return iSolvers; }
		public void addSolver(SolverInstanceInterface solver) { iSolvers.add(solver); }

		public List<SolverServerInterface> getServers() { return iServers; }
		public void addServer(SolverServerInterface server) { iServers.add(server); }

		/** Optional client-side navigation target (e.g. "solver?type=course"); set only by the Select op. */
		public String getNavigate() { return iNavigate; }
		public void setNavigate(String navigate) { iNavigate = navigate; }
	}

	public static class SolverInstanceInterface implements IsSerializable {
		private String iType;
		private String iOwner;
		private String iOwnerId;
		private Long iOnlineId;
		private String iSession;
		private String iHost;
		private String iConfiguration;
		private String iStatus;
		private String iCreated;
		private String iLastUsed;
		private int iProgress;
		private int iProgressMax;
		private boolean iWorking;
		private boolean iPassivated;

		public SolverInstanceInterface() {}

		public String getType() { return iType; }
		public void setType(String type) { iType = type; }

		public String getOwner() { return iOwner; }
		public void setOwner(String owner) { iOwner = owner; }

		/** OwnerPuid used by the Select/Unload operations (not the friendly owner label). */
		public String getOwnerId() { return iOwnerId; }
		public void setOwnerId(String ownerId) { iOwnerId = ownerId; }

		/** Session id of an online student-sectioning instance; null for regular solvers. */
		public Long getOnlineId() { return iOnlineId; }
		public void setOnlineId(Long onlineId) { iOnlineId = onlineId; }

		public String getSession() { return iSession; }
		public void setSession(String session) { iSession = session; }

		public String getHost() { return iHost; }
		public void setHost(String host) { iHost = host; }

		public String getConfiguration() { return iConfiguration; }
		public void setConfiguration(String configuration) { iConfiguration = configuration; }

		public String getStatus() { return iStatus; }
		public void setStatus(String status) { iStatus = status; }

		public String getCreated() { return iCreated; }
		public void setCreated(String created) { iCreated = created; }

		public String getLastUsed() { return iLastUsed; }
		public void setLastUsed(String lastUsed) { iLastUsed = lastUsed; }

		public int getProgress() { return iProgress; }
		public void setProgress(int progress) { iProgress = progress; }

		public int getProgressMax() { return iProgressMax; }
		public void setProgressMax(int progressMax) { iProgressMax = progressMax; }

		public boolean isWorking() { return iWorking; }
		public void setWorking(boolean working) { iWorking = working; }

		public boolean isPassivated() { return iPassivated; }
		public void setPassivated(boolean passivated) { iPassivated = passivated; }
	}

	public static class SolverServerInterface implements IsSerializable {
		private String iHost;
		private String iVersion;
		private String iStarted;
		private long iAvailableMemory;
		private int iCores;
		private long iUsage;
		private int iActiveInstances;
		private int iWorkingInstances;
		private int iPassivatedInstances;
		private boolean iActive;
		private boolean iLocal;

		public SolverServerInterface() {}

		public String getHost() { return iHost; }
		public void setHost(String host) { iHost = host; }

		public String getVersion() { return iVersion; }
		public void setVersion(String version) { iVersion = version; }

		public String getStarted() { return iStarted; }
		public void setStarted(String started) { iStarted = started; }

		public long getAvailableMemory() { return iAvailableMemory; }
		public void setAvailableMemory(long availableMemory) { iAvailableMemory = availableMemory; }

		public int getCores() { return iCores; }
		public void setCores(int cores) { iCores = cores; }

		public long getUsage() { return iUsage; }
		public void setUsage(long usage) { iUsage = usage; }

		public int getActiveInstances() { return iActiveInstances; }
		public void setActiveInstances(int activeInstances) { iActiveInstances = activeInstances; }

		public int getWorkingInstances() { return iWorkingInstances; }
		public void setWorkingInstances(int workingInstances) { iWorkingInstances = workingInstances; }

		public int getPassivatedInstances() { return iPassivatedInstances; }
		public void setPassivatedInstances(int passivatedInstances) { iPassivatedInstances = passivatedInstances; }

		public boolean isActive() { return iActive; }
		public void setActive(boolean active) { iActive = active; }

		public boolean isLocal() { return iLocal; }
		public void setLocal(boolean local) { iLocal = local; }
	}
}
