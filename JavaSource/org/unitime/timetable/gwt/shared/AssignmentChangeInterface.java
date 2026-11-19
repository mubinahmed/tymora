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
import org.unitime.timetable.gwt.shared.SuggestionsInterface.SelectedAssignment;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Reason and approval step for class and examination assignment changes (AssignmentChangeRequest).
 *
 * Every change made from the Angular assignment dialogs carries a reason. Depending on the
 * tmtbl.assignment.approval policy and on whether the user holds Right.AssignmentChangeApprove,
 * a change is either applied at once (recorded as self-approved) or stored as a pending request
 * that an approver approves (then it is applied) or rejects. Examination changes are submitted
 * through ExamAssignmentDialogRequest ASSIGN, which uses the same store; this request covers class
 * changes, the request list, and the decisions.
 */
public class AssignmentChangeInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		/** what the current user may do and whether their changes need approval */
		INFO,
		/** submit a class change: assignments (+ reason, summary); applied now or stored as pending */
		SUBMIT_CLASS,
		/** list change requests (filter: PENDING, MINE, ALL; optional subjectId) */
		LIST,
		/** approve a pending request (id, note): it is applied */
		APPROVE,
		/** reject a pending request (id, note) */
		REJECT,
		/** the requester withdraws their own pending request (id) */
		CANCEL,
		/** the requester has seen the decisions on their requests */
		MARK_SEEN,
	}

	public static class AssignmentChangeRequest implements GwtRpcRequest<AssignmentChangeResponse> {
		private Operation iOperation = Operation.INFO;
		private Long iId;
		private Long iClassId;
		private List<SelectedAssignment> iAssignments;
		private String iReason;
		private String iSummary;
		private String iNote;
		/** LIST: PENDING (awaiting a decision), MINE (requested by me), ALL */
		private String iFilter;
		private Long iSubjectId;

		public AssignmentChangeRequest() {}
		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }
		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }
		public Long getClassId() { return iClassId; }
		public void setClassId(Long classId) { iClassId = classId; }
		public List<SelectedAssignment> getAssignments() { return iAssignments; }
		public void setAssignments(List<SelectedAssignment> assignments) { iAssignments = assignments; }
		public String getReason() { return iReason; }
		public void setReason(String reason) { iReason = reason; }
		public String getSummary() { return iSummary; }
		public void setSummary(String summary) { iSummary = summary; }
		public String getNote() { return iNote; }
		public void setNote(String note) { iNote = note; }
		public String getFilter() { return iFilter; }
		public void setFilter(String filter) { iFilter = filter; }
		public Long getSubjectId() { return iSubjectId; }
		public void setSubjectId(Long subjectId) { iSubjectId = subjectId; }
	}

	public static class AssignmentChangeInfo implements IsSerializable {
		private Long iId;
		private String iType, iMode, iStatus;
		private Long iSubjectId;
		private String iSubjectName;
		private String iSummary;
		private String iReason;
		private String iRequestedBy, iRequestedName;
		private Date iRequestedTs;
		private String iDecidedName;
		private Date iDecidedTs;
		private String iDecisionNote;
		private String iResultMessage;
		private boolean iMine, iCanDecide, iCanCancel, iSelfApproved;

		public AssignmentChangeInfo() {}
		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }
		public String getType() { return iType; }
		public void setType(String type) { iType = type; }
		public String getMode() { return iMode; }
		public void setMode(String mode) { iMode = mode; }
		public String getStatus() { return iStatus; }
		public void setStatus(String status) { iStatus = status; }
		public Long getSubjectId() { return iSubjectId; }
		public void setSubjectId(Long subjectId) { iSubjectId = subjectId; }
		public String getSubjectName() { return iSubjectName; }
		public void setSubjectName(String subjectName) { iSubjectName = subjectName; }
		public String getSummary() { return iSummary; }
		public void setSummary(String summary) { iSummary = summary; }
		public String getReason() { return iReason; }
		public void setReason(String reason) { iReason = reason; }
		public String getRequestedBy() { return iRequestedBy; }
		public void setRequestedBy(String requestedBy) { iRequestedBy = requestedBy; }
		public String getRequestedName() { return iRequestedName; }
		public void setRequestedName(String requestedName) { iRequestedName = requestedName; }
		public Date getRequestedTs() { return iRequestedTs; }
		public void setRequestedTs(Date requestedTs) { iRequestedTs = requestedTs; }
		public String getDecidedName() { return iDecidedName; }
		public void setDecidedName(String decidedName) { iDecidedName = decidedName; }
		public Date getDecidedTs() { return iDecidedTs; }
		public void setDecidedTs(Date decidedTs) { iDecidedTs = decidedTs; }
		public String getDecisionNote() { return iDecisionNote; }
		public void setDecisionNote(String decisionNote) { iDecisionNote = decisionNote; }
		public String getResultMessage() { return iResultMessage; }
		public void setResultMessage(String resultMessage) { iResultMessage = resultMessage; }
		public boolean isMine() { return iMine; }
		public void setMine(boolean mine) { iMine = mine; }
		public boolean isCanDecide() { return iCanDecide; }
		public void setCanDecide(boolean canDecide) { iCanDecide = canDecide; }
		public boolean isCanCancel() { return iCanCancel; }
		public void setCanCancel(boolean canCancel) { iCanCancel = canCancel; }
		public boolean isSelfApproved() { return iSelfApproved; }
		public void setSelfApproved(boolean selfApproved) { iSelfApproved = selfApproved; }
	}

	public static class AssignmentChangeResponse implements GwtRpcResponse {
		/** tmtbl.assignment.approval: all, database or none */
		private String iPolicy;
		private boolean iCanApprove;
		private boolean iCanRequest;
		/** whether this user's changes need approval, per kind of change */
		private boolean iClassNeedsApproval, iExamSolverNeedsApproval, iExamDatabaseNeedsApproval;
		/** pending requests awaiting a decision (approvers) */
		private int iPendingCount;
		/** my requests decided since I last looked */
		private int iUnseenDecisions;
		/** my requests still pending */
		private int iMyPendingCount;
		private List<AssignmentChangeInfo> iChanges;
		/** the request created / decided by this call */
		private AssignmentChangeInfo iChange;
		private String iMessage;

		public AssignmentChangeResponse() {}
		public String getPolicy() { return iPolicy; }
		public void setPolicy(String policy) { iPolicy = policy; }
		public boolean isCanApprove() { return iCanApprove; }
		public void setCanApprove(boolean canApprove) { iCanApprove = canApprove; }
		public boolean isCanRequest() { return iCanRequest; }
		public void setCanRequest(boolean canRequest) { iCanRequest = canRequest; }
		public boolean isClassNeedsApproval() { return iClassNeedsApproval; }
		public void setClassNeedsApproval(boolean classNeedsApproval) { iClassNeedsApproval = classNeedsApproval; }
		public boolean isExamSolverNeedsApproval() { return iExamSolverNeedsApproval; }
		public void setExamSolverNeedsApproval(boolean examSolverNeedsApproval) { iExamSolverNeedsApproval = examSolverNeedsApproval; }
		public boolean isExamDatabaseNeedsApproval() { return iExamDatabaseNeedsApproval; }
		public void setExamDatabaseNeedsApproval(boolean examDatabaseNeedsApproval) { iExamDatabaseNeedsApproval = examDatabaseNeedsApproval; }
		public int getPendingCount() { return iPendingCount; }
		public void setPendingCount(int pendingCount) { iPendingCount = pendingCount; }
		public int getUnseenDecisions() { return iUnseenDecisions; }
		public void setUnseenDecisions(int unseenDecisions) { iUnseenDecisions = unseenDecisions; }
		public int getMyPendingCount() { return iMyPendingCount; }
		public void setMyPendingCount(int myPendingCount) { iMyPendingCount = myPendingCount; }
		public List<AssignmentChangeInfo> getChanges() { return iChanges; }
		public void addChange(AssignmentChangeInfo change) {
			if (iChanges == null) iChanges = new ArrayList<AssignmentChangeInfo>();
			iChanges.add(change);
		}
		public AssignmentChangeInfo getChange() { return iChange; }
		public void setChange(AssignmentChangeInfo change) { iChange = change; }
		public String getMessage() { return iMessage; }
		public void setMessage(String message) { iMessage = message; }
	}
}
