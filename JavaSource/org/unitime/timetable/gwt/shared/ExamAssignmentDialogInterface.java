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
 * Typed contract for the Angular examination assignment dialog. It replaces the HTML-producing
 * legacy examInfo.action page and wraps the same ExamInfoModel.
 *
 * The dialog is stateful, like the legacy page. The proposed change being built (selected period,
 * rooms and the resulting bumped exams) lives in the user's HTTP session between calls, so each
 * operation only sends what changed.
 */
public class ExamAssignmentDialogInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		/** open the dialog for examId (resets any pending change) */
		LOAD,
		/** re-read the current state (e.g. after changing the room filter) */
		REFRESH,
		/** pick a period for the current exam; periodId = -1 means "unassign" */
		SELECT_PERIOD,
		/** pick the room(s) for the current exam in its selected period */
		SELECT_ROOMS,
		/** switch the dialog to another exam touched by the pending change */
		SELECT_EXAM,
		/** drop an exam from the pending change */
		REMOVE,
		/** compute suggestions (solver mode only) */
		SUGGESTIONS,
		/** adopt suggestion number suggestionIndex as the pending change */
		SELECT_SUGGESTION,
		/** commit the pending change (to the solver or to the database) */
		ASSIGN,
		/** discard the pending change */
		CLEAR,
	}

	public static class ExamAssignmentDialogRequest implements GwtRpcRequest<ExamAssignmentDialogResponse> {
		private Operation iOperation = Operation.LOAD;
		private Long iExamId;
		private Long iPeriodId;
		private List<Long> iRoomIds;
		private Integer iSuggestionIndex;
		private Integer iMinRoomSize, iMaxRoomSize;
		private String iRoomFilter;
		private Boolean iAllowRoomConflict;
		private String iRoomOrder;
		private Integer iDepth;
		private Long iTimeout;
		private String iFilter;
		/** ASSIGN: the reason for the change (required) */
		private String iReason;

		public ExamAssignmentDialogRequest() {}

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }
		public Long getExamId() { return iExamId; }
		public void setExamId(Long examId) { iExamId = examId; }
		public Long getPeriodId() { return iPeriodId; }
		public void setPeriodId(Long periodId) { iPeriodId = periodId; }
		public List<Long> getRoomIds() { return iRoomIds; }
		public void setRoomIds(List<Long> roomIds) { iRoomIds = roomIds; }
		public Integer getSuggestionIndex() { return iSuggestionIndex; }
		public void setSuggestionIndex(Integer suggestionIndex) { iSuggestionIndex = suggestionIndex; }
		public Integer getMinRoomSize() { return iMinRoomSize; }
		public void setMinRoomSize(Integer minRoomSize) { iMinRoomSize = minRoomSize; }
		public Integer getMaxRoomSize() { return iMaxRoomSize; }
		public void setMaxRoomSize(Integer maxRoomSize) { iMaxRoomSize = maxRoomSize; }
		public String getRoomFilter() { return iRoomFilter; }
		public void setRoomFilter(String roomFilter) { iRoomFilter = roomFilter; }
		public Boolean getAllowRoomConflict() { return iAllowRoomConflict; }
		public void setAllowRoomConflict(Boolean allowRoomConflict) { iAllowRoomConflict = allowRoomConflict; }
		public String getRoomOrder() { return iRoomOrder; }
		public void setRoomOrder(String roomOrder) { iRoomOrder = roomOrder; }
		public Integer getDepth() { return iDepth; }
		public void setDepth(Integer depth) { iDepth = depth; }
		public Long getTimeout() { return iTimeout; }
		public void setTimeout(Long timeout) { iTimeout = timeout; }
		public String getFilter() { return iFilter; }
		public void setFilter(String filter) { iFilter = filter; }
		public String getReason() { return iReason; }
		public void setReason(String reason) { iReason = reason; }
	}

	/** One student or instructor conflict of a placement, with the reason in words. */
	public static class ExamConflict implements IsSerializable {
		/** Direct, MoreThanTwoADay, BackToBack, DistanceBackToBack, Distribution */
		private String iType;
		/** true for an instructor conflict, false for a student conflict */
		private boolean iInstructor;
		private int iCount;
		/** the other examination(s), class or event involved */
		private String iOther;
		/** the other side's period / time */
		private String iOtherTime;
		/** for distribution conflicts: preference (R, P, S, D, ...) */
		private String iPreference;

		public ExamConflict() {}
		public ExamConflict(String type, boolean instructor, int count, String other, String otherTime) {
			iType = type; iInstructor = instructor; iCount = count; iOther = other; iOtherTime = otherTime;
		}
		public String getType() { return iType; }
		public boolean isInstructor() { return iInstructor; }
		public int getCount() { return iCount; }
		public String getOther() { return iOther; }
		public String getOtherTime() { return iOtherTime; }
		public String getPreference() { return iPreference; }
		public void setPreference(String preference) { iPreference = preference; }
	}

	/** A placement (period + rooms) of the dialog's exam with its conflict counts and details. */
	public static class ExamPlacement implements IsSerializable {
		private Long iPeriodId;
		private String iPeriod;
		private String iPeriodPref;
		private List<Long> iRoomIds = new ArrayList<Long>();
		private String iRooms;
		private int iRoomCapacity;
		private boolean iValid = true;
		private int iDirect, iMoreThanTwoADay, iBackToBack, iDistanceBackToBack;
		private int iInstructorDirect, iInstructorMoreThanTwoADay, iInstructorBackToBack;
		private int iDistribution;
		private List<ExamConflict> iConflicts = new ArrayList<ExamConflict>();

		public ExamPlacement() {}
		public Long getPeriodId() { return iPeriodId; }
		public void setPeriodId(Long periodId) { iPeriodId = periodId; }
		public String getPeriod() { return iPeriod; }
		public void setPeriod(String period) { iPeriod = period; }
		public String getPeriodPref() { return iPeriodPref; }
		public void setPeriodPref(String periodPref) { iPeriodPref = periodPref; }
		public List<Long> getRoomIds() { return iRoomIds; }
		public String getRooms() { return iRooms; }
		public void setRooms(String rooms) { iRooms = rooms; }
		public int getRoomCapacity() { return iRoomCapacity; }
		public void setRoomCapacity(int roomCapacity) { iRoomCapacity = roomCapacity; }
		public boolean isValid() { return iValid; }
		public void setValid(boolean valid) { iValid = valid; }
		public void setCounts(int direct, int m2d, int btb, int dbtb, int iDirectI, int m2dI, int btbI, int distribution) {
			iDirect = direct; iMoreThanTwoADay = m2d; iBackToBack = btb; iDistanceBackToBack = dbtb;
			iInstructorDirect = iDirectI; iInstructorMoreThanTwoADay = m2dI; iInstructorBackToBack = btbI;
			iDistribution = distribution;
		}
		public int getDirect() { return iDirect; }
		public int getMoreThanTwoADay() { return iMoreThanTwoADay; }
		public int getBackToBack() { return iBackToBack; }
		public int getDistanceBackToBack() { return iDistanceBackToBack; }
		public int getInstructorDirect() { return iInstructorDirect; }
		public int getInstructorMoreThanTwoADay() { return iInstructorMoreThanTwoADay; }
		public int getInstructorBackToBack() { return iInstructorBackToBack; }
		public int getDistribution() { return iDistribution; }
		public List<ExamConflict> getConflicts() { return iConflicts; }
	}

	/** One available period of the exam, with the conflicts it would create. */
	public static class ExamPeriodOption implements IsSerializable {
		private Long iPeriodId;
		private String iName;
		private String iPref;
		private boolean iInitial, iSelected;
		private int iDirect, iMoreThanTwoADay, iBackToBack, iDistanceBackToBack;
		private int iInstructorDirect, iInstructorMoreThanTwoADay, iInstructorBackToBack;
		private List<String> iDistributionConflicts = new ArrayList<String>();

		public ExamPeriodOption() {}
		public Long getPeriodId() { return iPeriodId; }
		public void setPeriodId(Long periodId) { iPeriodId = periodId; }
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
		public String getPref() { return iPref; }
		public void setPref(String pref) { iPref = pref; }
		public boolean isInitial() { return iInitial; }
		public void setInitial(boolean initial) { iInitial = initial; }
		public boolean isSelected() { return iSelected; }
		public void setSelected(boolean selected) { iSelected = selected; }
		public void setCounts(int direct, int m2d, int btb, int dbtb, int iDirectI, int m2dI, int btbI) {
			iDirect = direct; iMoreThanTwoADay = m2d; iBackToBack = btb; iDistanceBackToBack = dbtb;
			iInstructorDirect = iDirectI; iInstructorMoreThanTwoADay = m2dI; iInstructorBackToBack = btbI;
		}
		public int getDirect() { return iDirect; }
		public int getMoreThanTwoADay() { return iMoreThanTwoADay; }
		public int getBackToBack() { return iBackToBack; }
		public int getDistanceBackToBack() { return iDistanceBackToBack; }
		public int getInstructorDirect() { return iInstructorDirect; }
		public int getInstructorMoreThanTwoADay() { return iInstructorMoreThanTwoADay; }
		public int getInstructorBackToBack() { return iInstructorBackToBack; }
		public List<String> getDistributionConflicts() { return iDistributionConflicts; }
	}

	/** One room available in the selected period. */
	public static class ExamRoomOption implements IsSerializable {
		private Long iRoomId;
		private String iName;
		private int iCapacity;
		private String iPref;
		/** false when the room ignores room checks (may be shared) */
		private boolean iHard = true;
		private boolean iSelected, iInitial;

		public ExamRoomOption() {}
		public Long getRoomId() { return iRoomId; }
		public void setRoomId(Long roomId) { iRoomId = roomId; }
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
		public int getCapacity() { return iCapacity; }
		public void setCapacity(int capacity) { iCapacity = capacity; }
		public String getPref() { return iPref; }
		public void setPref(String pref) { iPref = pref; }
		public boolean isHard() { return iHard; }
		public void setHard(boolean hard) { iHard = hard; }
		public boolean isSelected() { return iSelected; }
		public void setSelected(boolean selected) { iSelected = selected; }
		public boolean isInitial() { return iInitial; }
		public void setInitial(boolean initial) { iInitial = initial; }
	}

	/** One exam affected by a proposed change: moved, newly assigned or bumped (unassigned). */
	public static class ExamChangeLine implements IsSerializable {
		private Long iExamId;
		private String iExam;
		/** true when the exam would lose its assignment (bumped by the change) */
		private boolean iUnassigned;
		private boolean iSelected;
		/** true when the exam needs rooms but none are selected yet */
		private boolean iRoomsMissing;
		private String iFromPeriod, iToPeriod, iFromRooms, iToRooms;
		private int iDirect, iDirectBefore, iMoreThanTwoADay, iMoreThanTwoADayBefore, iBackToBack, iBackToBackBefore;

		public ExamChangeLine() {}
		public Long getExamId() { return iExamId; }
		public void setExamId(Long examId) { iExamId = examId; }
		public String getExam() { return iExam; }
		public void setExam(String exam) { iExam = exam; }
		public boolean isUnassigned() { return iUnassigned; }
		public void setUnassigned(boolean unassigned) { iUnassigned = unassigned; }
		public boolean isSelected() { return iSelected; }
		public void setSelected(boolean selected) { iSelected = selected; }
		public boolean isRoomsMissing() { return iRoomsMissing; }
		public void setRoomsMissing(boolean roomsMissing) { iRoomsMissing = roomsMissing; }
		public String getFromPeriod() { return iFromPeriod; }
		public void setFromPeriod(String fromPeriod) { iFromPeriod = fromPeriod; }
		public String getToPeriod() { return iToPeriod; }
		public void setToPeriod(String toPeriod) { iToPeriod = toPeriod; }
		public String getFromRooms() { return iFromRooms; }
		public void setFromRooms(String fromRooms) { iFromRooms = fromRooms; }
		public String getToRooms() { return iToRooms; }
		public void setToRooms(String toRooms) { iToRooms = toRooms; }
		public void setCounts(int direct, int directBefore, int m2d, int m2dBefore, int btb, int btbBefore) {
			iDirect = direct; iDirectBefore = directBefore; iMoreThanTwoADay = m2d; iMoreThanTwoADayBefore = m2dBefore;
			iBackToBack = btb; iBackToBackBefore = btbBefore;
		}
		public int getDirect() { return iDirect; }
		public int getDirectBefore() { return iDirectBefore; }
		public int getMoreThanTwoADay() { return iMoreThanTwoADay; }
		public int getMoreThanTwoADayBefore() { return iMoreThanTwoADayBefore; }
		public int getBackToBack() { return iBackToBack; }
		public int getBackToBackBefore() { return iBackToBackBefore; }
	}

	/** A proposed change (the pending one, or one suggestion). */
	public static class ExamChange implements IsSerializable {
		private double iValue;
		private int iNrAssigned, iNrUnassigned;
		private List<ExamChangeLine> iLines = new ArrayList<ExamChangeLine>();

		public ExamChange() {}
		public double getValue() { return iValue; }
		public void setValue(double value) { iValue = value; }
		public int getNrAssigned() { return iNrAssigned; }
		public void setNrAssigned(int nrAssigned) { iNrAssigned = nrAssigned; }
		public int getNrUnassigned() { return iNrUnassigned; }
		public void setNrUnassigned(int nrUnassigned) { iNrUnassigned = nrUnassigned; }
		public List<ExamChangeLine> getLines() { return iLines; }
	}

	public static class ExamAssignmentDialogResponse implements GwtRpcResponse {
		private Long iExamId;
		private String iExamName;
		private String iExamType;
		private int iNrStudents, iMaxRooms, iLength;
		private String iSeatingType;
		/** true when a loaded examination solver holds this exam type (changes go to the solver) */
		private boolean iSolverMode;
		private boolean iCanComputeSuggestions;
		private boolean iCanAssign;
		private String iAssignConfirm;
		/** set after ASSIGN: true when the change was committed */
		private boolean iAssigned;
		/** warnings / error text from the last operation */
		private String iMessage;
		private ExamPlacement iCurrent;
		private ExamPlacement iSelected;
		private List<ExamPeriodOption> iPeriods = new ArrayList<ExamPeriodOption>();
		private List<ExamRoomOption> iRooms;
		private ExamChange iChange;
		private List<ExamChange> iSuggestions;
		private String iSuggestionsMessage;
		private boolean iSuggestionsTimeout;
		private Integer iMinRoomSize, iMaxRoomSize;
		private String iRoomFilter, iRoomOrder;
		private boolean iAllowRoomConflict;
		private int iDepth;
		private long iTimeout;
		/** whether a change by this user (in the current mode) needs approval */
		private boolean iNeedsApproval;
		/** set after ASSIGN when the change was stored as a pending request instead of applied */
		private boolean iSubmitted;
		private Long iChangeRequestId;
		/** pending change requests for this examination */
		private List<AssignmentChangeInterface.AssignmentChangeInfo> iPendingRequests;

		public ExamAssignmentDialogResponse() {}
		public Long getExamId() { return iExamId; }
		public void setExamId(Long examId) { iExamId = examId; }
		public String getExamName() { return iExamName; }
		public void setExamName(String examName) { iExamName = examName; }
		public String getExamType() { return iExamType; }
		public void setExamType(String examType) { iExamType = examType; }
		public int getNrStudents() { return iNrStudents; }
		public void setNrStudents(int nrStudents) { iNrStudents = nrStudents; }
		public int getMaxRooms() { return iMaxRooms; }
		public void setMaxRooms(int maxRooms) { iMaxRooms = maxRooms; }
		public int getLength() { return iLength; }
		public void setLength(int length) { iLength = length; }
		public String getSeatingType() { return iSeatingType; }
		public void setSeatingType(String seatingType) { iSeatingType = seatingType; }
		public boolean isSolverMode() { return iSolverMode; }
		public void setSolverMode(boolean solverMode) { iSolverMode = solverMode; }
		public boolean isCanComputeSuggestions() { return iCanComputeSuggestions; }
		public void setCanComputeSuggestions(boolean canComputeSuggestions) { iCanComputeSuggestions = canComputeSuggestions; }
		public boolean isCanAssign() { return iCanAssign; }
		public void setCanAssign(boolean canAssign) { iCanAssign = canAssign; }
		public String getAssignConfirm() { return iAssignConfirm; }
		public void setAssignConfirm(String assignConfirm) { iAssignConfirm = assignConfirm; }
		public boolean isAssigned() { return iAssigned; }
		public void setAssigned(boolean assigned) { iAssigned = assigned; }
		public String getMessage() { return iMessage; }
		public void setMessage(String message) { iMessage = message; }
		public ExamPlacement getCurrent() { return iCurrent; }
		public void setCurrent(ExamPlacement current) { iCurrent = current; }
		public ExamPlacement getSelected() { return iSelected; }
		public void setSelected(ExamPlacement selected) { iSelected = selected; }
		public List<ExamPeriodOption> getPeriods() { return iPeriods; }
		public List<ExamRoomOption> getRooms() { return iRooms; }
		public void setRooms(List<ExamRoomOption> rooms) { iRooms = rooms; }
		public ExamChange getChange() { return iChange; }
		public void setChange(ExamChange change) { iChange = change; }
		public List<ExamChange> getSuggestions() { return iSuggestions; }
		public void setSuggestions(List<ExamChange> suggestions) { iSuggestions = suggestions; }
		public String getSuggestionsMessage() { return iSuggestionsMessage; }
		public void setSuggestionsMessage(String suggestionsMessage) { iSuggestionsMessage = suggestionsMessage; }
		public boolean isSuggestionsTimeout() { return iSuggestionsTimeout; }
		public void setSuggestionsTimeout(boolean suggestionsTimeout) { iSuggestionsTimeout = suggestionsTimeout; }
		public Integer getMinRoomSize() { return iMinRoomSize; }
		public void setMinRoomSize(Integer minRoomSize) { iMinRoomSize = minRoomSize; }
		public Integer getMaxRoomSize() { return iMaxRoomSize; }
		public void setMaxRoomSize(Integer maxRoomSize) { iMaxRoomSize = maxRoomSize; }
		public String getRoomFilter() { return iRoomFilter; }
		public void setRoomFilter(String roomFilter) { iRoomFilter = roomFilter; }
		public String getRoomOrder() { return iRoomOrder; }
		public void setRoomOrder(String roomOrder) { iRoomOrder = roomOrder; }
		public boolean isAllowRoomConflict() { return iAllowRoomConflict; }
		public void setAllowRoomConflict(boolean allowRoomConflict) { iAllowRoomConflict = allowRoomConflict; }
		public int getDepth() { return iDepth; }
		public void setDepth(int depth) { iDepth = depth; }
		public long getTimeout() { return iTimeout; }
		public void setTimeout(long timeout) { iTimeout = timeout; }
		public boolean isNeedsApproval() { return iNeedsApproval; }
		public void setNeedsApproval(boolean needsApproval) { iNeedsApproval = needsApproval; }
		public boolean isSubmitted() { return iSubmitted; }
		public void setSubmitted(boolean submitted) { iSubmitted = submitted; }
		public Long getChangeRequestId() { return iChangeRequestId; }
		public void setChangeRequestId(Long changeRequestId) { iChangeRequestId = changeRequestId; }
		public List<AssignmentChangeInterface.AssignmentChangeInfo> getPendingRequests() { return iPendingRequests; }
		public void setPendingRequests(List<AssignmentChangeInterface.AssignmentChangeInfo> pendingRequests) { iPendingRequests = pendingRequests; }
	}
}
