/*
 * Angular migration - Wave 4: command backend for the offering search.
 *
 * Reuses the existing model-level access (SubjectArea.getUserSubjectAreas and
 * SubjectArea.getCourseOfferings) rather than reimplementing any query, so the
 * business logic and permissions match the legacy Struts search.
 */
package org.unitime.timetable.rest.offerings;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.SubjectArea;
import org.unitime.timetable.model.dao.SubjectAreaDAO;
import org.unitime.timetable.rest.offerings.OfferingSearchInterface.OfferingRow;
import org.unitime.timetable.rest.offerings.OfferingSearchInterface.SearchOfferingsRequest;
import org.unitime.timetable.rest.offerings.OfferingSearchInterface.SearchOfferingsResponse;
import org.unitime.timetable.rest.offerings.OfferingSearchInterface.SubjectAreaItem;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

@GwtRpcImplements(SearchOfferingsRequest.class)
public class SearchOfferingsBackend implements GwtRpcImplementation<SearchOfferingsRequest, SearchOfferingsResponse> {

	@Override
	public SearchOfferingsResponse execute(SearchOfferingsRequest request, SessionContext context) {
		context.checkPermission(Right.InstructionalOfferings);
		SearchOfferingsResponse response = new SearchOfferingsResponse();
		response.setCanAdd(context.hasPermission(Right.AddCourseOffering));

		// Subject areas the current user may see (same source as the legacy screen).
		java.util.Set<Long> allowed = new java.util.HashSet<Long>();
		for (SubjectArea sa : SubjectArea.getUserSubjectAreas(context.getUser())) {
			response.addSubjectArea(new SubjectAreaItem(sa.getUniqueId(), sa.getSubjectAreaAbbreviation(), sa.getTitle()));
			allowed.add(sa.getUniqueId());
		}

		if (request.getSubjectAreaId() == null) return response; // just the picker

		SubjectArea sa = SubjectAreaDAO.getInstance().get(request.getSubjectAreaId());
		if (sa == null) throw new GwtRpcException("Subject area not found.");
		// The search is limited to the subject areas of the picker, as on the legacy screen. (This used to call
		// checkPermission(sa.getSession(), "Session", Right.InstructionalOfferings), which bound to the
		// (Serializable targetId, String targetType, Right) overload: the Session entity was taken as an id of type
		// "Session" for a Department-level right, so every search failed with HTTP 500.)
		if (!allowed.contains(sa.getUniqueId()) || !sa.getSession().getUniqueId().equals(context.getUser().getCurrentAcademicSessionId()))
			throw new GwtRpcException("You are not allowed to see the courses of " + sa.getSubjectAreaAbbreviation() + ".");

		String q = request.getCourseNumber() == null ? null : request.getCourseNumber().trim().toLowerCase();
		for (CourseOffering co : sa.getCourseOfferings()) {
			if (!Boolean.TRUE.equals(co.isIsControl())) continue; // controlling course = the offering
			if (q != null && !q.isEmpty()) {
				String nbr = co.getCourseNbr() == null ? "" : co.getCourseNbr().toLowerCase();
				if (!nbr.startsWith(q)) continue;
			}
			boolean offered = co.getInstructionalOffering() == null || !Boolean.TRUE.equals(co.getInstructionalOffering().isNotOffered());
			response.addOffering(new OfferingRow(co.getUniqueId(), co.getCourseName(), co.getTitle(), offered, co.getEnrollment()));
		}
		return response;
	}
}
