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
package org.unitime.timetable.server.courses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.FlushMode;
import org.hibernate.Transaction;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.CrossListsInterface.CourseReservation;
import org.unitime.timetable.gwt.shared.CrossListsInterface.CrossListsResponse;
import org.unitime.timetable.gwt.shared.CrossListsInterface.CrossListsUpdateRequest;
import org.unitime.timetable.model.AdvisorClassPref;
import org.unitime.timetable.model.AdvisorCourseRequest;
import org.unitime.timetable.model.AdvisorSectioningPref;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.ClassInstructor;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.CourseRequest;
import org.unitime.timetable.model.CurriculumCourse;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.Event;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.OfferingCoordinator;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.SubjectArea;
import org.unitime.timetable.model.dao.CourseOfferingDAO;
import org.unitime.timetable.model.dao.InstructionalOfferingDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Save backend for the migrated Cross Lists page — full add/remove reconciliation, a faithful
 * port of {@code CrossListsModifyAction.doUpdate()}. The request's {@code courses} is the DESIRED
 * final set of course offerings (with reservations); the backend compares it to the offering's
 * current set:
 * <ul>
 *   <li><b>removed</b> (current, not desired) &rarr; split into a new NOT-offered instructional
 *       offering (clone the course, move curriculum courses / course &amp; advisor requests);</li>
 *   <li><b>kept</b> &rarr; update controlling flag + reservation limit;</li>
 *   <li><b>added</b> (desired, not current) &rarr; merge the course's offering into this one and
 *       delete the emptied offering;</li>
 * </ul>
 * then propagate the controlling department to classes and fix class instructors / offering
 * coordinators. Gated by {@link Right#InstructionalOfferingCrossLists} (+ per removed course
 * {@link Right#CourseOfferingDeleteFromCrossList}), transactional, and change-logged.
 *
 * @author Angular migration
 */
@GwtRpcImplements(CrossListsUpdateRequest.class)
public class CrossListsUpdateBackend implements GwtRpcImplementation<CrossListsUpdateRequest, CrossListsResponse> {

	@Override
	public CrossListsResponse execute(CrossListsUpdateRequest request, SessionContext context) {
		Long offeringId = request.getOfferingId();
		if (offeringId == null)
			throw new GwtRpcException("No instructional offering was specified.");
		Long ctrlId = request.getControllingCourseId();
		if (ctrlId == null)
			throw new GwtRpcException("No controlling course was specified.");

		context.checkPermission(offeringId, "InstructionalOffering", Right.InstructionalOfferingCrossLists);

		CourseOfferingDAO cdao = CourseOfferingDAO.getInstance();
		InstructionalOfferingDAO idao = InstructionalOfferingDAO.getInstance();
		InstructionalOffering io = idao.get(offeringId);
		if (io == null)
			throw new GwtRpcException("Instructional offering " + offeringId + " was not found.");

		// Desired final set (ordered) + reservation lookup.
		List<Long> desiredIds = new ArrayList<Long>();
		Map<Long, Integer> reservation = new HashMap<Long, Integer>();
		for (CourseReservation cr : request.getCourses()) {
			if (cr.getCourseId() == null) continue;
			desiredIds.add(cr.getCourseId());
			reservation.put(cr.getCourseId(), cr.getReservation());
		}
		if (desiredIds.isEmpty())
			throw new GwtRpcException("A cross-listed offering must keep at least one course.");
		if (!desiredIds.contains(ctrlId))
			throw new GwtRpcException("The controlling course must be one of the cross-listed courses.");

		// Current set.
		List<Long> origIds = new ArrayList<Long>();
		for (CourseOffering co : io.getCourseOfferings()) origIds.add(co.getUniqueId());

		// Up-front permission for removed courses (clean 403, before opening a transaction).
		for (Long origCrs : origIds)
			if (!desiredIds.contains(origCrs))
				context.checkPermission(cdao.get(origCrs), Right.CourseOfferingDeleteFromCrossList);

		boolean singleCourseLimit = ApplicationProperty.ModifyCrossListSingleCourseLimit.isTrue();
		boolean keepRequests = ApplicationProperty.ModifyCrossListKeepCourseRequests.isTrue();

		org.hibernate.Session hibSession = idao.getSession();
		hibSession.setHibernateFlushMode(FlushMode.MANUAL);
		Transaction tx = hibSession.beginTransaction();
		List<CurriculumCourse> cc = new ArrayList<CurriculumCourse>();
		List<CourseRequest> courseRequests = new ArrayList<CourseRequest>();
		Map<String, List<AdvisorCourseRequest>> advCourseReqs = new HashMap<String, List<AdvisorCourseRequest>>();
		List<CourseOffering> deletedOfferings = new ArrayList<CourseOffering>();
		List<CourseOffering> addedOfferings = new ArrayList<CourseOffering>();
		try {
			for (Long origCrs : origIds) {
				if (!desiredIds.contains(origCrs)) {
					// 1. Removed course -> new not-offered offering with a clone of the course.
					InstructionalOffering io1 = new InstructionalOffering();
					CourseOffering co1 = cdao.get(origCrs);
					SubjectArea sa1 = co1.getSubjectArea();
					io1.setNotOffered(Boolean.TRUE);
					io1.setSession(io.getSession());
					io1.setByReservationOnly(io.getByReservationOnly());

					CourseOffering co2 = (CourseOffering) co1.clone();
					co2.setIsControl(Boolean.TRUE);

					for (CurriculumCourse x : hibSession.createQuery(
							"from CurriculumCourse where course.uniqueId = :courseId", CurriculumCourse.class)
							.setParameter("courseId", co1.getUniqueId()).list()) {
						cc.add(x.clone(co2));
						x.getClassification().getCourses().remove(x);
						hibSession.remove(x);
					}
					if (keepRequests)
						for (CourseRequest oldReq : hibSession.createQuery(
								"from CourseRequest where courseOffering.uniqueId = :courseId", CourseRequest.class)
								.setParameter("courseId", co1.getUniqueId()).list()) {
							CourseRequest newReq = new CourseRequest();
							newReq.setAllowOverlap(oldReq.getAllowOverlap());
							newReq.setOrder(oldReq.getOrder());
							newReq.setCredit(oldReq.getCredit());
							newReq.setCourseOffering(co2);
							newReq.setCourseDemand(oldReq.getCourseDemand());
							oldReq.getCourseDemand().getCourseRequests().remove(oldReq);
							courseRequests.add(newReq);
							hibSession.remove(oldReq);
						}
					List<AdvisorCourseRequest> acrs = hibSession.createQuery(
							"from AdvisorCourseRequest where courseOffering.uniqueId = :courseId", AdvisorCourseRequest.class)
							.setParameter("courseId", co1.getUniqueId()).list();
					advCourseReqs.put(co2.getCourseName(), acrs);
					for (AdvisorCourseRequest acr : acrs) acr.setCourseOffering(null);
					deletedOfferings.add(co2);

					for (Iterator<?> i = io.getCourseOfferings().iterator(); i.hasNext(); ) {
						CourseOffering co3 = (CourseOffering) i.next();
						if (co3.equals(co1)) {
							SubjectArea sa = co3.getSubjectArea();
							sa.getCourseOfferings().remove(co1);
							hibSession.merge(sa);
						}
					}
					sa1.getCourseOfferings().remove(co1);
					hibSession.merge(sa1);

					io.removeCourseOffering(co1);
					Event.deleteFromEvents(hibSession, co1);
					Exam.deleteFromExams(hibSession, co1);
					hibSession.remove(co1);
					hibSession.merge(io);
					hibSession.flush();

					co2.setInstructionalOffering(io1);
					io1.addToCourseOfferings(co2);
					if (io1.getInstrOfferingPermId() == null) io1.generateInstrOfferingPermId();
					hibSession.persist(io1);
					hibSession.flush();
				} else {
					// 2. Kept course -> controlling flag + reservation.
					CourseOffering co = cdao.get(origCrs);
					co.setIsControl(Boolean.valueOf(ctrlId.equals(co.getUniqueId())));
					Integer res = reservation.get(origCrs);
					if (singleCourseLimit)
						co.setReservation(res);
					else
						co.setReservation(desiredIds.size() > 1 ? res : null);
					hibSession.merge(co);
					hibSession.flush();
				}
			}

			// 3. Added courses -> merge their (single-course, not-offered) offering into this one.
			for (Long course : desiredIds) {
				if (origIds.contains(course)) continue;
				CourseOffering co1 = cdao.get(course);
				InstructionalOffering io1 = co1.getInstructionalOffering();
				SubjectArea sa = io1.getControllingCourseOffering().getSubjectArea();
				for (Iterator<?> i = io1.getCourseOfferings().iterator(); i.hasNext(); ) {
					CourseOffering co2 = (CourseOffering) i.next();
					SubjectArea sa2 = co2.getSubjectArea();
					CourseOffering co3 = (CourseOffering) co2.clone();
					co3.setIsControl(Boolean.valueOf(ctrlId.equals(co2.getUniqueId())));

					for (CurriculumCourse x : hibSession.createQuery(
							"from CurriculumCourse where course.uniqueId = :courseId", CurriculumCourse.class)
							.setParameter("courseId", co2.getUniqueId()).list()) {
						cc.add(x.clone(co3));
						x.getClassification().getCourses().remove(x);
						hibSession.remove(x);
					}
					if (keepRequests)
						for (CourseRequest oldReq : hibSession.createQuery(
								"from CourseRequest where courseOffering.uniqueId = :courseId", CourseRequest.class)
								.setParameter("courseId", co2.getUniqueId()).list()) {
							CourseRequest newReq = new CourseRequest();
							newReq.setAllowOverlap(oldReq.getAllowOverlap());
							newReq.setOrder(oldReq.getOrder());
							newReq.setCredit(oldReq.getCredit());
							newReq.setCourseOffering(co3);
							newReq.setCourseDemand(oldReq.getCourseDemand());
							oldReq.getCourseDemand().getCourseRequests().remove(oldReq);
							courseRequests.add(newReq);
							hibSession.remove(oldReq);
						}
					List<AdvisorCourseRequest> acrs = hibSession.createQuery(
							"from AdvisorCourseRequest where courseOffering.uniqueId = :courseId", AdvisorCourseRequest.class)
							.setParameter("courseId", co2.getUniqueId()).list();
					advCourseReqs.put(co3.getCourseName(), acrs);
					addedOfferings.add(co3);
					for (AdvisorCourseRequest acr : acrs) acr.setCourseOffering(null);

					co3.setReservation(reservation.get(course));

					sa2.getCourseOfferings().remove(co2);
					hibSession.merge(sa2);
					io1.removeCourseOffering(co2);
					Event.deleteFromEvents(hibSession, co2);
					Exam.deleteFromExams(hibSession, co2);
					hibSession.remove(co2);
					hibSession.flush();
				}
				Event.deleteFromEvents(hibSession, io1);
				Exam.deleteFromExams(hibSession, io1);
				hibSession.remove(io1);
				hibSession.flush();
				hibSession.merge(sa);
			}

			hibSession.flush();

			for (CourseOffering co3 : addedOfferings) {
				co3.setInstructionalOffering(io);
				io.addToCourseOfferings(co3);
				hibSession.persist(co3);
				hibSession.flush();
				hibSession.merge(io);
			}
			for (CurriculumCourse x : cc) hibSession.persist(x);
			for (CourseRequest x : courseRequests) {
				x.getCourseDemand().getCourseRequests().add(x);
				hibSession.persist(x);
			}
			for (CourseOffering co : deletedOfferings) {
				List<AdvisorCourseRequest> acrs = advCourseReqs.get(co.getCourseName());
				if (acrs != null)
					for (AdvisorCourseRequest req : acrs) {
						req.setCourseOffering(co);
						for (Iterator<AdvisorSectioningPref> ip = req.getPreferences().iterator(); ip.hasNext(); ) {
							AdvisorSectioningPref p = ip.next();
							if (p instanceof AdvisorClassPref) { hibSession.remove(p); ip.remove(); }
						}
						hibSession.merge(req);
					}
			}
			for (CourseOffering co : addedOfferings) {
				List<AdvisorCourseRequest> acrs = advCourseReqs.get(co.getCourseName());
				if (acrs != null)
					for (AdvisorCourseRequest req : acrs) { req.setCourseOffering(co); hibSession.merge(req); }
			}

			// Propagate the controlling department to all classes; fix instructors / coordinators.
			Department dept = io.getControllingCourseOffering().getDepartment();
			for (Iterator<?> iterCfg = io.getInstrOfferingConfigs().iterator(); iterCfg.hasNext(); ) {
				InstrOfferingConfig cfg = (InstrOfferingConfig) iterCfg.next();
				for (Iterator<?> iterSbp = cfg.getSchedulingSubparts().iterator(); iterSbp.hasNext(); ) {
					SchedulingSubpart subpart = (SchedulingSubpart) iterSbp.next();
					for (Iterator<?> iterCls = subpart.getClasses().iterator(); iterCls.hasNext(); ) {
						Class_ cls = (Class_) iterCls.next();
						if (!cls.getManagingDept().isExternalManager()) {
							cls.setManagingDept(dept, context.getUser(), hibSession);
							hibSession.merge(cls);
						}
						for (Iterator<ClassInstructor> i = cls.getClassInstructors().iterator(); i.hasNext(); ) {
							ClassInstructor ci = i.next();
							if (!ci.getInstructor().getDepartment().equals(dept)) {
								ci.getInstructor().getClasses().remove(ci);
								DepartmentalInstructor di = (ci.getInstructor().getExternalUniqueId() == null ? null :
										DepartmentalInstructor.findByPuidDepartmentId(ci.getInstructor().getExternalUniqueId(), dept.getUniqueId(), hibSession));
								if (di == null) { hibSession.remove(ci); i.remove(); }
								else { ci.setInstructor(di); di.getClasses().add(ci); hibSession.merge(ci); }
							}
						}
					}
				}
			}
			if (io.getOfferingCoordinators() != null)
				for (Iterator<OfferingCoordinator> i = io.getOfferingCoordinators().iterator(); i.hasNext(); ) {
					OfferingCoordinator oc = i.next();
					if (!oc.getInstructor().getDepartment().equals(dept)) {
						oc.getInstructor().getOfferingCoordinators().remove(oc);
						DepartmentalInstructor di = (oc.getInstructor().getExternalUniqueId() == null ? null :
								DepartmentalInstructor.findByPuidDepartmentId(oc.getInstructor().getExternalUniqueId(), dept.getUniqueId(), hibSession));
						if (di == null) { hibSession.remove(oc); i.remove(); }
						else { oc.setInstructor(di); di.getOfferingCoordinators().add(oc); hibSession.merge(oc); }
					}
				}

			ChangeLog.addChange(
					hibSession, context, io,
					ChangeLog.Source.CROSS_LIST, ChangeLog.Operation.UPDATE,
					io.getControllingCourseOffering().getSubjectArea(), null);

			tx.commit();
			hibSession.flush();
			hibSession.clear();
		} catch (GwtRpcException e) {
			try { if (tx != null && tx.isActive()) tx.rollback(); } catch (Exception x) {}
			throw e;
		} catch (Exception e) {
			try { if (tx != null && tx.isActive()) tx.rollback(); } catch (Exception x) {}
			throw new GwtRpcException("Failed to save cross lists: " + e.getMessage(), e);
		}

		InstructionalOffering reloaded = InstructionalOfferingDAO.getInstance().get(offeringId);
		CrossListsResponse response = new CrossListsResponse();
		CrossListsBackend.fill(response, reloaded, context);
		response.setSaved(true);
		return response;
	}
}
