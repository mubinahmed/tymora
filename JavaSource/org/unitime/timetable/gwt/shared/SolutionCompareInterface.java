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
import java.util.Date;
import java.util.List;

import org.unitime.timetable.gwt.command.client.GwtRpcRequest;
import org.unitime.timetable.gwt.command.client.GwtRpcResponse;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Side-by-side comparison of two saved course timetables (SolutionCompareRequest): the solution
 * statistics of each (global info) next to each other, and class by class where the two place a class
 * differently (time, room, or assigned in only one), with the student conflicts of each placement.
 */
public class SolutionCompareInterface implements IsSerializable {

	public static enum Change implements IsSerializable { SAME, TIME, ROOM, TIME_AND_ROOM, ONLY_A, ONLY_B }

	public static class SolutionCompareRequest implements GwtRpcRequest<SolutionCompareResponse> {
		private Long iSolutionA, iSolutionB;

		public SolutionCompareRequest() {}
		public Long getSolutionA() { return iSolutionA; }
		public void setSolutionA(Long solutionA) { iSolutionA = solutionA; }
		public Long getSolutionB() { return iSolutionB; }
		public void setSolutionB(Long solutionB) { iSolutionB = solutionB; }
	}

	/** A saved solution the user may compare. */
	public static class SolutionOption implements IsSerializable {
		private Long iId;
		private String iOwner, iOwnerName, iNote, iCreator;
		private Date iCreated, iCommitDate;
		private boolean iCommitted;
		private int iNrAssignments;

		public SolutionOption() {}
		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }
		public String getOwner() { return iOwner; }
		public void setOwner(String owner) { iOwner = owner; }
		public String getOwnerName() { return iOwnerName; }
		public void setOwnerName(String ownerName) { iOwnerName = ownerName; }
		public String getNote() { return iNote; }
		public void setNote(String note) { iNote = note; }
		public String getCreator() { return iCreator; }
		public void setCreator(String creator) { iCreator = creator; }
		public Date getCreated() { return iCreated; }
		public void setCreated(Date created) { iCreated = created; }
		public Date getCommitDate() { return iCommitDate; }
		public void setCommitDate(Date commitDate) { iCommitDate = commitDate; }
		public boolean isCommitted() { return iCommitted; }
		public void setCommitted(boolean committed) { iCommitted = committed; }
		public int getNrAssignments() { return iNrAssignments; }
		public void setNrAssignments(int nrAssignments) { iNrAssignments = nrAssignments; }
	}

	/** One solution statistic of the two timetables. */
	public static class Metric implements IsSerializable {
		private String iName, iValueA, iValueB;
		/** numeric values when both parse (for the difference), else null */
		private Double iNumberA, iNumberB;

		public Metric() {}
		public Metric(String name, String a, String b) { iName = name; iValueA = a; iValueB = b; }
		public String getName() { return iName; }
		public String getValueA() { return iValueA; }
		public String getValueB() { return iValueB; }
		public Double getNumberA() { return iNumberA; }
		public void setNumberA(Double numberA) { iNumberA = numberA; }
		public Double getNumberB() { return iNumberB; }
		public void setNumberB(Double numberB) { iNumberB = numberB; }
	}

	/** One class and its placement in each timetable. */
	public static class ClassRow implements IsSerializable {
		private Long iClassId;
		private String iClassName, iDepartment;
		private String iTimeA, iRoomA, iDateA, iInstructorA;
		private String iTimeB, iRoomB, iDateB, iInstructorB;
		private Integer iStudentConflictsA, iStudentConflictsB;
		private Change iChange;

		public ClassRow() {}
		public Long getClassId() { return iClassId; }
		public void setClassId(Long classId) { iClassId = classId; }
		public String getClassName() { return iClassName; }
		public void setClassName(String className) { iClassName = className; }
		public String getDepartment() { return iDepartment; }
		public void setDepartment(String department) { iDepartment = department; }
		public String getTimeA() { return iTimeA; }
		public void setTimeA(String timeA) { iTimeA = timeA; }
		public String getRoomA() { return iRoomA; }
		public void setRoomA(String roomA) { iRoomA = roomA; }
		public String getDateA() { return iDateA; }
		public void setDateA(String dateA) { iDateA = dateA; }
		public String getInstructorA() { return iInstructorA; }
		public void setInstructorA(String instructorA) { iInstructorA = instructorA; }
		public String getTimeB() { return iTimeB; }
		public void setTimeB(String timeB) { iTimeB = timeB; }
		public String getRoomB() { return iRoomB; }
		public void setRoomB(String roomB) { iRoomB = roomB; }
		public String getDateB() { return iDateB; }
		public void setDateB(String dateB) { iDateB = dateB; }
		public String getInstructorB() { return iInstructorB; }
		public void setInstructorB(String instructorB) { iInstructorB = instructorB; }
		public Integer getStudentConflictsA() { return iStudentConflictsA; }
		public void setStudentConflictsA(Integer studentConflictsA) { iStudentConflictsA = studentConflictsA; }
		public Integer getStudentConflictsB() { return iStudentConflictsB; }
		public void setStudentConflictsB(Integer studentConflictsB) { iStudentConflictsB = studentConflictsB; }
		public Change getChange() { return iChange; }
		public void setChange(Change change) { iChange = change; }
	}

	public static class SolutionCompareResponse implements GwtRpcResponse {
		private List<SolutionOption> iSolutions = new ArrayList<SolutionOption>();
		private SolutionOption iA, iB;
		private List<Metric> iMetrics = new ArrayList<Metric>();
		private List<ClassRow> iRows = new ArrayList<ClassRow>();
		private int iSame, iTimeChanged, iRoomChanged, iTimeAndRoomChanged, iOnlyA, iOnlyB;
		private String iMessage;

		public SolutionCompareResponse() {}
		public List<SolutionOption> getSolutions() { return iSolutions; }
		public SolutionOption getA() { return iA; }
		public void setA(SolutionOption a) { iA = a; }
		public SolutionOption getB() { return iB; }
		public void setB(SolutionOption b) { iB = b; }
		public List<Metric> getMetrics() { return iMetrics; }
		public List<ClassRow> getRows() { return iRows; }
		public void count(Change change) {
			switch (change) {
			case SAME: iSame++; break;
			case TIME: iTimeChanged++; break;
			case ROOM: iRoomChanged++; break;
			case TIME_AND_ROOM: iTimeAndRoomChanged++; break;
			case ONLY_A: iOnlyA++; break;
			case ONLY_B: iOnlyB++; break;
			}
		}
		public int getSame() { return iSame; }
		public int getTimeChanged() { return iTimeChanged; }
		public int getRoomChanged() { return iRoomChanged; }
		public int getTimeAndRoomChanged() { return iTimeAndRoomChanged; }
		public int getOnlyA() { return iOnlyA; }
		public int getOnlyB() { return iOnlyB; }
		public String getMessage() { return iMessage; }
		public void setMessage(String message) { iMessage = message; }
	}
}
