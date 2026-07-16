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
package org.unitime.timetable.server.solver;

import java.util.Date;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cpsolver.ifs.util.DataProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.unitime.timetable.action.ManageSolversAction;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ManageSolversInterface.ManageSolversRequest;
import org.unitime.timetable.gwt.shared.ManageSolversInterface.ManageSolversResponse;
import org.unitime.timetable.gwt.shared.ManageSolversInterface.SolverInstanceInterface;
import org.unitime.timetable.gwt.shared.ManageSolversInterface.SolverServerInterface;
import org.unitime.timetable.model.SolverParameterGroup.SolverType;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.solver.CommonSolverInterface;
import org.unitime.timetable.solver.jgroups.SolverContainer;
import org.unitime.timetable.solver.jgroups.SolverServer;
import org.unitime.timetable.solver.service.SolverServerService;
import org.unitime.timetable.util.Formats;

/**
 * Read-only backend for the Angular "Manage Solvers" screen (legacy manageSolvers.action).
 * Enumerates the active solver instances currently loaded on the in-memory solver
 * server(s) plus the list of available servers. Data is projected from the
 * {@link SolverServerService} solver server registry; no mutating operation of the
 * legacy page is implemented here (all reads are wrapped in try/catch for lazy-init /
 * remote-proxy safety).
 *
 * @author Angular migration
 */
@GwtRpcImplements(ManageSolversRequest.class)
public class ManageSolversBackend implements GwtRpcImplementation<ManageSolversRequest, ManageSolversResponse> {
	private static Logger sLog = LogManager.getLogger(ManageSolversBackend.class);
	private static Formats.Format<Date> sDF = Formats.getDateFormat(Formats.Pattern.DATE_TIME_STAMP);

	@Autowired SolverServerService solverServerService;

	@Override
	public ManageSolversResponse execute(ManageSolversRequest request, SessionContext context) {
		context.checkPermission(Right.ManageSolvers);

		ManageSolversResponse response = new ManageSolversResponse();

		for (SolverServer server: solverServerService.getServers(false)) {
			// Available server row
			SolverServerInterface s = new SolverServerInterface();
			try { s.setHost(server.getHost()); } catch (Exception e) {}
			boolean active = false;
			try { active = server.isActive(); } catch (Exception e) {}
			s.setActive(active);
			if (active) {
				try { s.setVersion(server.getVersion()); } catch (Exception e) {}
				try { Date d = server.getStartTime(); if (d != null) s.setStarted(sDF.format(d)); } catch (Exception e) {}
				try { s.setAvailableMemory(server.getAvailableMemory()); } catch (Exception e) {}
				try { s.setCores(server.getAvailableProcessors()); } catch (Exception e) {}
				try { s.setUsage(server.getUsage()); } catch (Exception e) {}
				try { s.setLocal(server.isLocal()); } catch (Exception e) {}
			}
			int[] counts = new int[3]; // active, working, passivated
			addInstances(response, server, SolverType.COURSE, counts);
			addInstances(response, server, SolverType.EXAM, counts);
			addInstances(response, server, SolverType.STUDENT, counts);
			addInstances(response, server, SolverType.INSTRUCTOR, counts);
			s.setActiveInstances(counts[0]);
			s.setWorkingInstances(counts[1]);
			s.setPassivatedInstances(counts[2]);
			response.addServer(s);
		}

		return response;
	}

	private void addInstances(ManageSolversResponse response, SolverServer server, SolverType type, int[] counts) {
		SolverContainer<? extends CommonSolverInterface> container = getContainer(server, type);
		if (container == null) return;
		try {
			for (String user: container.getSolvers()) {
				CommonSolverInterface solver = container.getSolver(user);
				if (solver == null) continue;
				try {
					boolean passivated = false;
					try { passivated = solver.isPassivated(); } catch (Exception e) {}
					if (passivated) counts[2]++;
					else {
						counts[0]++;
						try { if (solver.isWorking()) counts[1]++; } catch (Exception e) {}
					}
					response.addSolver(toRow(solver, type));
				} catch (Exception e) {
					sLog.debug("Failed to project solver " + user + ": " + e.getMessage());
				}
			}
		} catch (Exception e) {
			sLog.debug("Failed to list " + type + " solvers on " + safeHost(server) + ": " + e.getMessage());
		}
	}

	private SolverInstanceInterface toRow(CommonSolverInterface solver, SolverType type) {
		SolverInstanceInterface row = new SolverInstanceInterface();
		row.setType(type.name());
		DataProperties properties = null;
		try { properties = solver.getProperties(); } catch (Exception e) {}
		if (properties != null) {
			try { row.setOwner(ManageSolversAction.getSolverOwner(properties)); } catch (Exception e) {}
			try { row.setSession(ManageSolversAction.getSolverSession(properties)); } catch (Exception e) {}
			try { row.setConfiguration(ManageSolversAction.getSolverConfiguration(properties)); } catch (Exception e) {}
		}
		try { row.setHost(solver.getHost()); } catch (Exception e) {}
		try { row.setStatus(ManageSolversAction.getSolverStatus(solver)); } catch (Exception e) {}
		try { Date d = solver.getLoadedDate(); if (d != null) row.setCreated(sDF.format(d)); } catch (Exception e) {}
		try { Date d = solver.getLastUsed(); if (d != null) row.setLastUsed(sDF.format(d)); } catch (Exception e) {}
		try { row.setWorking(solver.isWorking()); } catch (Exception e) {}
		try { row.setPassivated(solver.isPassivated()); } catch (Exception e) {}
		try {
			Map<?, ?> progress = solver.getProgress();
			if (progress != null) {
				Object p = progress.get("PROGRESS");
				Object m = progress.get("MAX_PROGRESS");
				if (p instanceof Number) row.setProgress(((Number) p).intValue());
				if (m instanceof Number) row.setProgressMax(((Number) m).intValue());
			}
		} catch (Exception e) {}
		return row;
	}

	private SolverContainer<? extends CommonSolverInterface> getContainer(SolverServer server, SolverType type) {
		try {
			switch (type) {
			case COURSE: return server.getCourseSolverContainer();
			case EXAM: return server.getExamSolverContainer();
			case STUDENT: return server.getStudentSolverContainer();
			case INSTRUCTOR: return server.getInstructorSchedulingContainer();
			default: return null;
			}
		} catch (Exception e) {
			return null;
		}
	}

	private String safeHost(SolverServer server) {
		try { return server.getHost(); } catch (Exception e) { return "?"; }
	}
}
