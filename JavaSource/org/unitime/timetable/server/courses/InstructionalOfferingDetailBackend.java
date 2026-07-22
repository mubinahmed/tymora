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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.InstructionalOfferingDetailInterface.ClassInfo;
import org.unitime.timetable.gwt.shared.InstructionalOfferingDetailInterface.ConfigInfo;
import org.unitime.timetable.gwt.shared.InstructionalOfferingDetailInterface.CoordinatorInfo;
import org.unitime.timetable.gwt.shared.InstructionalOfferingDetailInterface.CrossListInfo;
import org.unitime.timetable.gwt.shared.InstructionalOfferingDetailInterface.InstructionalOfferingDetailRequest;
import org.unitime.timetable.gwt.shared.InstructionalOfferingDetailInterface.InstructionalOfferingDetailResponse;
import org.unitime.timetable.gwt.shared.InstructionalOfferingDetailInterface.SubpartInfo;
import org.unitime.timetable.model.Assignment;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.ClassInstructor;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.OfferingCoordinator;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.comparators.ClassComparator;
import org.unitime.timetable.model.comparators.CourseOfferingComparator;
import org.unitime.timetable.model.comparators.InstrOfferingConfigComparator;
import org.unitime.timetable.model.comparators.OfferingCoordinatorComparator;
import org.unitime.timetable.model.comparators.SchedulingSubpartComparator;
import org.unitime.timetable.model.dao.CourseOfferingDAO;
import org.unitime.timetable.model.dao.InstructionalOfferingDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read-only backend for the migrated Instructional Offering Detail page (legacy
 * instructionalOfferingDetail.action). See
 * {@link org.unitime.timetable.gwt.shared.InstructionalOfferingDetailInterface}
 * for the request/response protocol.
 *
 * Mirrors the read path of {@code InstructionalOfferingDetailAction.doLoad()}:
 * gated by {@link Right#InstructionalOfferingDetail}, it projects the offering
 * summary, offering coordinators, cross-listed courses and the
 * configuration &rarr; subpart &rarr; class tree. Assigned time/room come from
 * each class' committed assignment only (this page does not attach to a live
 * solver), matching the conservative read-only intent of {@code ClassesSearchBackend}.
 *
 * @author Angular migration
 */
@GwtRpcImplements(InstructionalOfferingDetailRequest.class)
public class InstructionalOfferingDetailBackend implements GwtRpcImplementation<InstructionalOfferingDetailRequest, InstructionalOfferingDetailResponse> {

	@Override
	public InstructionalOfferingDetailResponse execute(InstructionalOfferingDetailRequest request, SessionContext context) {
		Long offeringId = request.getOfferingId();
		if (offeringId == null && request.getCourseOfferingId() != null) {
			CourseOffering co = CourseOfferingDAO.getInstance().get(request.getCourseOfferingId());
			if (co != null && co.getInstructionalOffering() != null)
				offeringId = co.getInstructionalOffering().getUniqueId();
		}
		if (offeringId == null)
			throw new GwtRpcException("No instructional offering was specified.");

		context.checkPermission(offeringId, "InstructionalOffering", Right.InstructionalOfferingDetail);

		InstructionalOffering io = InstructionalOfferingDAO.getInstance().get(offeringId);
		if (io == null)
			throw new GwtRpcException("Instructional offering " + offeringId + " was not found.");

		Long subjectAreaId = io.getControllingCourseOffering().getSubjectArea().getUniqueId();

		InstructionalOfferingDetailResponse response = new InstructionalOfferingDetailResponse();
		response.setOfferingId(io.getUniqueId());
		response.setControllingCourseId(io.getControllingCourseOffering().getUniqueId());
		response.setCourseName(io.getCourseName());
		response.setTitle(str(io.getControllingCourseOffering().getTitle()));
		response.setOffered(!io.isNotOffered());
		response.setByReservationOnly(io.isByReservationOnly());
		response.setEnrollment(io.getEnrollment());
		response.setLimit(io.getLimit());
		response.setSnapshotLimit(io.getSnapshotLimit());
		response.setDemand(io.getDemand());
		response.setProjectedDemand(io.getProjectedDemand());
		response.setNotes(io.getNotes());
		response.setConsent(consent(io));
		response.setCredit(credit(io));
		response.setWaitList(waitList(io));

		boolean unlimited = false;
		for (Iterator<?> i = io.getInstrOfferingConfigs().iterator(); i.hasNext(); ) {
			InstrOfferingConfig cfg = (InstrOfferingConfig) i.next();
			if (Boolean.TRUE.equals(cfg.isUnlimitedEnrollment())) { unlimited = true; break; }
		}
		response.setUnlimited(unlimited);

		// Offering coordinators.
		List<OfferingCoordinator> coordinators = new ArrayList<OfferingCoordinator>(io.getOfferingCoordinators());
		Collections.sort(coordinators, new OfferingCoordinatorComparator(context));
		for (OfferingCoordinator oc : coordinators) {
			if (oc.getInstructor() == null) continue;
			CoordinatorInfo ci = new CoordinatorInfo();
			ci.setInstructorId(oc.getInstructor().getUniqueId());
			ci.setName(oc.getInstructor().getNameLastFirst());
			ci.setShare(share(oc));
			response.addCoordinator(ci);
		}

		// Cross-listed courses.
		List<CourseOffering> offerings = new ArrayList<CourseOffering>(io.getCourseOfferings());
		Collections.sort(offerings, new CourseOfferingComparator(CourseOfferingComparator.COMPARE_BY_CTRL_CRS));
		for (CourseOffering co : offerings) {
			CrossListInfo xl = new CrossListInfo();
			xl.setCourseId(co.getUniqueId());
			xl.setCourse(co.getCourseName());
			xl.setTitle(str(co.getTitle()));
			xl.setControlling(Boolean.TRUE.equals(co.getIsControl()));
			xl.setReservation(co.getReservation() == null ? "" : co.getReservation().toString());
			response.addCrossListing(xl);
		}

		// Configuration -> subpart -> class tree.
		List<InstrOfferingConfig> configs = new ArrayList<InstrOfferingConfig>(io.getInstrOfferingConfigs());
		Collections.sort(configs, new InstrOfferingConfigComparator(subjectAreaId));
		for (InstrOfferingConfig config : configs) {
			ConfigInfo cfg = new ConfigInfo();
			cfg.setId(config.getUniqueId());
			cfg.setName(config.getName());
			cfg.setUnlimited(Boolean.TRUE.equals(config.isUnlimitedEnrollment()));
			cfg.setLimit(Boolean.TRUE.equals(config.isUnlimitedEnrollment()) || config.getLimit() == null ? "" : config.getLimit().toString());

			List<SchedulingSubpart> subparts = new ArrayList<SchedulingSubpart>(config.getSchedulingSubparts());
			Collections.sort(subparts, new SchedulingSubpartComparator());
			for (SchedulingSubpart subpart : subparts) {
				SubpartInfo sp = new SubpartInfo();
				sp.setId(subpart.getUniqueId());
				sp.setType(str(subpart.getItypeDesc()));
				sp.setIndent(indent(subpart));

				List<Class_> classes = new ArrayList<Class_>(subpart.getClasses());
				Collections.sort(classes, new ClassComparator(ClassComparator.COMPARE_BY_ITYPE));
				CourseOffering controlling = io.getControllingCourseOffering();
				for (Class_ clazz : classes) {
					ClassInfo ck = new ClassInfo();
					ck.setId(clazz.getUniqueId());
					ck.setSection(section(clazz, controlling));
					ck.setLimit(limit(clazz));
					ck.setEnrollment(clazz.getEnrollment() == null ? "" : clazz.getEnrollment().toString());
					Assignment a = committedAssignment(clazz);
					ck.setTime(assignedTime(a));
					ck.setRoom(assignedRoom(a));
					ck.setInstructors(instructors(clazz));
					sp.addClass(ck);
				}
				cfg.addSubpart(sp);
			}
			response.addConfig(cfg);
		}

		return response;
	}

	private static String str(String s) { return s == null ? "" : s.trim(); }

	private static String consent(InstructionalOffering io) {
		try {
			if (io.getControllingCourseOffering().getConsentType() != null)
				return str(io.getControllingCourseOffering().getConsentType().getLabel());
		} catch (Exception e) {}
		return "";
	}

	private static String credit(InstructionalOffering io) {
		try {
			if (io.getControllingCourseOffering().getCredit() != null)
				return str(io.getControllingCourseOffering().getCredit().creditText());
		} catch (Exception e) {}
		return "";
	}

	private static String waitList(InstructionalOffering io) {
		try {
			if (io.getEffectiveWaitListMode() != null)
				return io.getEffectiveWaitListMode().name();
		} catch (Exception e) {}
		return "";
	}

	private static String share(OfferingCoordinator oc) {
		try {
			int pct = oc.getPercentShare() == null ? 0 : oc.getPercentShare();
			if (oc.getResponsibility() != null)
				return oc.getResponsibility().getLabel() + (pct > 0 ? " (" + pct + "%)" : "");
			return pct != 0 ? pct + "%" : "";
		} catch (Exception e) {
			return "";
		}
	}

	private static int indent(SchedulingSubpart subpart) {
		int indent = 0;
		try {
			SchedulingSubpart parent = subpart.getParentSubpart();
			while (parent != null) { indent++; parent = parent.getParentSubpart(); }
		} catch (Exception e) {}
		return indent;
	}

	private static String section(Class_ c, CourseOffering co) {
		try {
			String s = c.getClassSuffix(co);
			if (s != null && !s.isEmpty()) return s;
		} catch (Exception e) {}
		try {
			String s = c.getSectionNumberString();
			if (s != null && !s.isEmpty()) return s;
		} catch (Exception e) {}
		try {
			return str(c.getClassLabel(co));
		} catch (Exception e) {
			return "";
		}
	}

	private static String limit(Class_ c) {
		Integer exp = c.getExpectedCapacity();
		Integer max = c.getMaxExpectedCapacity();
		if (exp == null) return max == null ? "" : max.toString();
		if (max == null || max.equals(exp)) return exp.toString();
		return exp + "-" + max;
	}

	private static Assignment committedAssignment(Class_ c) {
		try {
			return c.getCommittedAssignment();
		} catch (Exception e) {
			return null;
		}
	}

	private static String assignedTime(Assignment a) {
		if (a == null) return "";
		try {
			return str(a.getTimeLocation().getDayHeader()) + " " + str(a.getTimeLocation().getStartTimeHeader(true));
		} catch (Exception e) {
			return "";
		}
	}

	private static String assignedRoom(Assignment a) {
		if (a == null) return "";
		try {
			StringBuilder sb = new StringBuilder();
			for (Location loc : a.getRooms()) {
				if (loc == null) continue;
				if (sb.length() > 0) sb.append(", ");
				sb.append(loc.getLabel());
			}
			return sb.toString();
		} catch (Exception e) {
			return "";
		}
	}

	private static String instructors(Class_ c) {
		try {
			if (c.getClassInstructors() == null) return "";
			StringBuilder sb = new StringBuilder();
			for (Iterator<ClassInstructor> i = c.getClassInstructors().iterator(); i.hasNext();) {
				ClassInstructor ci = i.next();
				if (ci == null || ci.getInstructor() == null) continue;
				if (sb.length() > 0) sb.append(", ");
				sb.append(ci.getInstructor().getNameLastFirst());
			}
			return sb.toString();
		} catch (Exception e) {
			return "";
		}
	}
}
