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
import org.unitime.commons.hibernate.util.HibernateUtil;
import org.unitime.timetable.action.ManageSolversAction;
import org.unitime.timetable.defaults.SessionAttribute;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.command.server.GwtRpcLogging;
import org.unitime.timetable.gwt.command.server.GwtRpcLogging.Level;
import org.unitime.timetable.gwt.shared.ManageSolversInterface.ManageSolversOpRequest;
import org.unitime.timetable.gwt.shared.ManageSolversInterface.ManageSolversResponse;
import org.unitime.timetable.gwt.shared.ManageSolversInterface.SolverInstanceInterface;
import org.unitime.timetable.gwt.shared.ManageSolversInterface.SolverServerInterface;
import org.unitime.timetable.model.SolverParameterGroup.SolverType;
import org.unitime.timetable.onlinesectioning.OnlineSectioningServer;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.solver.CommonSolverInterface;
import org.unitime.timetable.solver.SolverProxy;
import org.unitime.timetable.solver.exam.ExamSolverProxy;
import org.unitime.timetable.solver.instructor.InstructorSchedulingProxy;
import org.unitime.timetable.solver.jgroups.SolverContainer;
import org.unitime.timetable.solver.jgroups.SolverServer;
import org.unitime.timetable.solver.service.SolverServerService;
import org.unitime.timetable.solver.service.SolverService;
import org.unitime.timetable.solver.studentsct.StudentSolverProxy;
import org.unitime.timetable.util.Formats;

/**
 * Mutating backend for the Angular "Manage Solvers" screen. Replicates the operation
 * branches of the legacy {@link ManageSolversAction} (Select / Unload / Deselect /
 * Reload / Shutdown / Reset / Reconnect / Hibernate / Enable / Disable) and returns a
 * freshly rebuilt {@link ManageSolversResponse} so the client table refreshes. The
 * read-only listing remains served by {@link ManageSolversBackend}.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ManageSolversOpRequest.class)
@GwtRpcLogging(Level.ON_EXCEPTION)
public class ManageSolversOpBackend implements GwtRpcImplementation<ManageSolversOpRequest, ManageSolversResponse> {
	private static Logger sLog = LogManager.getLogger(ManageSolversOpBackend.class);
	private static Formats.Format<Date> sDF = Formats.getDateFormat(Formats.Pattern.DATE_TIME_STAMP);

	@Autowired SolverServerService solverServerService;
	@Autowired SolverService<SolverProxy> courseTimetablingSolverService;
	@Autowired SolverService<ExamSolverProxy> examinationSolverService;
	@Autowired SolverService<StudentSolverProxy> studentSectioningSolverService;
	@Autowired SolverService<InstructorSchedulingProxy> instructorSchedulingSolverService;

	@Override
	public ManageSolversResponse execute(ManageSolversOpRequest request, SessionContext context) {
		context.checkPermission(Right.ManageSolvers);

		String op = request.getOperation();
		String owner = request.getOwner();
		String type = request.getType();
		String host = request.getHost();
		Long onlineId = request.getOnlineId();

		ManageSolversResponse response = new ManageSolversResponse();

		if ("Select".equals(op) && owner != null && type != null) {
			switch (SolverType.valueOf(type)) {
			case COURSE:
				context.setAttribute(SessionAttribute.CourseTimetablingUser, owner);
				context.removeAttribute(SessionAttribute.CourseTimetablingSolver);
				response.setNavigate("solver?type=course");
				return response;
			case EXAM:
				context.setAttribute(SessionAttribute.ExaminationUser, owner);
				context.removeAttribute(SessionAttribute.ExaminationSolver);
				response.setNavigate("solver?type=exam");
				return response;
			case STUDENT:
				context.setAttribute(SessionAttribute.StudentSectioningUser, owner);
				context.removeAttribute(SessionAttribute.StudentSectioningSolver);
				response.setNavigate("solver?type=student");
				return response;
			case INSTRUCTOR:
				context.setAttribute(SessionAttribute.InstructorSchedulingUser, owner);
				context.removeAttribute(SessionAttribute.InstructorSchedulingSolver);
				response.setNavigate("solver?type=instructor");
				return response;
			}
		}

		if ("Unload".equals(op) && owner != null && type != null) {
			switch (SolverType.valueOf(type)) {
			case COURSE:
				context.setAttribute(SessionAttribute.CourseTimetablingUser, owner);
				context.removeAttribute(SessionAttribute.CourseTimetablingSolver);
				courseTimetablingSolverService.removeSolver();
				break;
			case EXAM:
				context.setAttribute(SessionAttribute.ExaminationUser, owner);
				context.removeAttribute(SessionAttribute.ExaminationSolver);
				examinationSolverService.removeSolver();
				break;
			case STUDENT:
				context.setAttribute(SessionAttribute.StudentSectioningUser, owner);
				context.removeAttribute(SessionAttribute.StudentSectioningSolver);
				studentSectioningSolverService.removeSolver();
				break;
			case INSTRUCTOR:
				context.setAttribute(SessionAttribute.InstructorSchedulingUser, owner);
				context.removeAttribute(SessionAttribute.InstructorSchedulingSolver);
				instructorSchedulingSolverService.removeSolver();
				break;
			}
		}

		if ("Unload".equals(op) && onlineId != null) {
			String sessionId = String.valueOf(onlineId);
			if (host != null) {
				SolverServer server = solverServerService.getServer(host);
				if (server != null) {
					server.getOnlineStudentSchedulingContainer().unloadSolver(sessionId);
				} else {
					solverServerService.getOnlineStudentSchedulingContainer().unloadSolver(sessionId);
				}
			} else {
				solverServerService.getOnlineStudentSchedulingContainer().unloadSolver(sessionId);
			}
		}

		if ("Reload".equals(op) && onlineId != null) {
			OnlineSectioningServer solver = solverServerService.getOnlineStudentSchedulingContainer().getSolver(String.valueOf(onlineId));
			if (solver != null) {
				solver.reload();
			}
		}

		if ("Deselect".equals(op)) {
			context.removeAttribute(SessionAttribute.CourseTimetablingUser);
			context.removeAttribute(SessionAttribute.CourseTimetablingSolver);
			context.removeAttribute(SessionAttribute.ExaminationUser);
			context.removeAttribute(SessionAttribute.ExaminationSolver);
			context.removeAttribute(SessionAttribute.StudentSectioningUser);
			context.removeAttribute(SessionAttribute.StudentSectioningSolver);
			context.removeAttribute(SessionAttribute.InstructorSchedulingUser);
			context.removeAttribute(SessionAttribute.InstructorSchedulingSolver);
		}

		if ("Shutdown".equals(op)) {
			SolverServer server = solverServerService.getServer(host);
			if (server != null)
				server.shutdown();
		}

		if ("Reset".equals(op)) {
			SolverServer server = solverServerService.getServer(host);
			if (server != null)
				server.reset(false);
		}

		if ("Reconnect".equals(op)) {
			SolverServer server = solverServerService.getServer(host);
			if (server != null)
				server.reconnect();
		}

		if ("Hibernate".equals(op)) {
			SolverServer server = solverServerService.getServer(host);
			if (server != null)
				server.reconnectHibernate();
			else
				try {
					HibernateUtil.reconnect(null);
				} catch (ClassNotFoundException e) {
					throw new GwtRpcException("Failed to reconnect Hibernate: " + e.getMessage());
				}
		}

		if ("Enable".equals(op)) {
			SolverServer server = solverServerService.getServer(host);
			if (server != null)
				server.setUsageBase(0);
		}

		if ("Disable".equals(op)) {
			SolverServer server = solverServerService.getServer(host);
			if (server != null)
				server.setUsageBase(1000);
		}

		// Rebuild the same listing the read-only backend produces so the table refreshes.
		buildListing(response);
		return response;
	}

	// ------------------------------------------------------------------
	// Listing rebuild (mirrors ManageSolversBackend; wrapped in try/catch).
	// ------------------------------------------------------------------

	private void buildListing(ManageSolversResponse response) {
		for (SolverServer server: solverServerService.getServers(false)) {
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
			int[] counts = new int[3];
			addInstances(response, server, SolverType.COURSE, counts);
			addInstances(response, server, SolverType.EXAM, counts);
			addInstances(response, server, SolverType.STUDENT, counts);
			addInstances(response, server, SolverType.INSTRUCTOR, counts);
			s.setActiveInstances(counts[0]);
			s.setWorkingInstances(counts[1]);
			s.setPassivatedInstances(counts[2]);
			response.addServer(s);
		}
		addOnlineInstances(response);
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
			try { row.setOwnerId(properties.getProperty("General.OwnerPuid")); } catch (Exception e) {}
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

	private void addOnlineInstances(ManageSolversResponse response) {
		try {
			for (SolverServer server: solverServerService.getServers(true)) {
				SolverContainer<OnlineSectioningServer> container = null;
				try { container = server.getOnlineStudentSchedulingContainer(); } catch (Exception e) {}
				if (container == null) continue;
				try {
					for (String sessionId: container.getSolvers()) {
						OnlineSectioningServer solver = null;
						try { solver = container.getSolver(sessionId); } catch (Exception e) {}
						if (solver == null) continue;
						try {
							SolverInstanceInterface row = new SolverInstanceInterface();
							try { row.setOnlineId(Long.valueOf(sessionId)); } catch (Exception e) {}
							try { row.setHost(solver.getHost()); } catch (Exception e) {}
							try { row.setSession(solver.getAcademicSession().toString()); } catch (Exception e) {}
							try { row.setConfiguration(solver.getAcademicSession().isSectioningEnabled() ? "Online" : "Assistant"); } catch (Exception e) {}
							try { row.setStatus(solver.isReady() ? "Ready" : "Loading"); } catch (Exception e) {}
							try {
								Date d = new Date(solver.getConfig().getPropertyLong("General.StartUpDate", 0));
								if (d.getTime() > 0) row.setCreated(sDF.format(d));
							} catch (Exception e) {}
							response.addSolver(row);
						} catch (Exception e) {
							sLog.debug("Failed to project online solver " + sessionId + ": " + e.getMessage());
						}
					}
				} catch (Exception e) {
					sLog.debug("Failed to list online solvers on " + safeHost(server) + ": " + e.getMessage());
				}
			}
		} catch (Exception e) {
			sLog.debug("Failed to list online solver servers: " + e.getMessage());
		}
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
