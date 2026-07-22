/*
 * Angular migration - Wave 4: additive command DTOs for an offering search.
 *
 * The instructional-offering search is a legacy Struts screen with no command
 * backend. These new request/response POJOs (plus SearchOfferingsBackend) wrap
 * the existing model-level search so Angular can list offerings and link to the
 * command-pattern editor. No existing file is changed; nothing here is used by
 * the GWT client, so it is a plain server-side + JSON contract.
 */
package org.unitime.timetable.rest.offerings;

import java.util.ArrayList;
import java.util.List;

import org.unitime.timetable.gwt.command.client.GwtRpcRequest;
import org.unitime.timetable.gwt.command.client.GwtRpcResponse;

public class OfferingSearchInterface {

	public static class SearchOfferingsRequest implements GwtRpcRequest<SearchOfferingsResponse> {
		private Long subjectAreaId;
		private String courseNumber;

		public SearchOfferingsRequest() {}

		public Long getSubjectAreaId() { return subjectAreaId; }
		public void setSubjectAreaId(Long subjectAreaId) { this.subjectAreaId = subjectAreaId; }
		public String getCourseNumber() { return courseNumber; }
		public void setCourseNumber(String courseNumber) { this.courseNumber = courseNumber; }

		@Override
		public String toString() { return "SearchOfferings(" + subjectAreaId + "," + courseNumber + ")"; }
	}

	public static class SubjectAreaItem {
		private Long id;
		private String abbreviation;
		private String title;

		public SubjectAreaItem() {}
		public SubjectAreaItem(Long id, String abbreviation, String title) {
			this.id = id; this.abbreviation = abbreviation; this.title = title;
		}
		public Long getId() { return id; }
		public String getAbbreviation() { return abbreviation; }
		public String getTitle() { return title; }
	}

	public static class OfferingRow {
		private Long id;
		private String courseName;
		private String title;
		private boolean offered;
		private Integer enrollment;

		public OfferingRow() {}
		public OfferingRow(Long id, String courseName, String title, boolean offered, Integer enrollment) {
			this.id = id; this.courseName = courseName; this.title = title;
			this.offered = offered; this.enrollment = enrollment;
		}
		public Long getId() { return id; }
		public String getCourseName() { return courseName; }
		public String getTitle() { return title; }
		public boolean isOffered() { return offered; }
		public Integer getEnrollment() { return enrollment; }
	}

	public static class SearchOfferingsResponse implements GwtRpcResponse {
		private List<SubjectAreaItem> subjectAreas = new ArrayList<SubjectAreaItem>();
		private List<OfferingRow> offerings = new ArrayList<OfferingRow>();
		private boolean canAdd;

		public List<SubjectAreaItem> getSubjectAreas() { return subjectAreas; }
		public void addSubjectArea(SubjectAreaItem item) { subjectAreas.add(item); }
		public List<OfferingRow> getOfferings() { return offerings; }
		public void addOffering(OfferingRow row) { offerings.add(row); }
		public boolean isCanAdd() { return canAdd; }
		public void setCanAdd(boolean canAdd) { this.canAdd = canAdd; }
	}
}
