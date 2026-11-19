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
package org.unitime.timetable.server.approval;

import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.AssignmentChangeInterface.AssignmentChangeRequest;
import org.unitime.timetable.gwt.shared.AssignmentChangeInterface.AssignmentChangeResponse;
import org.unitime.timetable.gwt.shared.AssignmentChangeInterface.Operation;
import org.unitime.timetable.model.AssignmentChange;
import org.unitime.timetable.model.dao.AssignmentChangeDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Backend of AssignmentChangeRequest: what the user may do (INFO), class change submission,
 * the change request list, approve / reject, withdraw, and the "decisions seen" marker.
 * Examination changes are submitted through ExamAssignmentDialogRequest ASSIGN.
 *
 * Listing needs Right.AssignmentChangeRequests (requesters see their own requests) or
 * Right.AssignmentChangeApprove (approvers see all); deciding needs AssignmentChangeApprove.
 */
@GwtRpcImplements(AssignmentChangeRequest.class)
public class AssignmentChangeBackend implements GwtRpcImplementation<AssignmentChangeRequest, AssignmentChangeResponse> {

	@Autowired AssignmentChangeService assignmentChangeService;

	@Override
	public AssignmentChangeResponse execute(AssignmentChangeRequest request, SessionContext context) {
		Operation op = (request.getOperation() == null ? Operation.INFO : request.getOperation());
		boolean canApprove = assignmentChangeService.canApprove(context);
		boolean canRequest = context.hasPermission(Right.AssignmentChangeRequests);
		if (!canApprove && !canRequest && op != Operation.INFO && op != Operation.SUBMIT_CLASS)
			throw new GwtRpcException("You are not allowed to see assignment change requests.");
		Long sessionId = context.getUser().getCurrentAcademicSessionId();
		String me = context.getUser().getExternalUserId();
		AssignmentChangeResponse response = new AssignmentChangeResponse();

		switch (op) {
		case SUBMIT_CLASS:
			AssignmentChange c = assignmentChangeService.submitClassChange(context, request.getClassId(), request.getAssignments(), request.getReason(), request.getSummary());
			response.setChange(assignmentChangeService.toInfo(context, c, canApprove));
			response.setMessage(c.isPending()
					? "Sent for approval. The change is not applied until an approver approves it."
					: "Change applied to the in-memory solution and recorded with your reason.");
			break;
		case APPROVE:
		case REJECT:
			AssignmentChange d = assignmentChangeService.decide(context, request.getId(), op == Operation.APPROVE, request.getNote());
			response.setChange(assignmentChangeService.toInfo(context, d, canApprove));
			break;
		case CANCEL:
			response.setChange(assignmentChangeService.toInfo(context, assignmentChangeService.cancel(context, request.getId()), canApprove));
			break;
		case MARK_SEEN:
			org.hibernate.Session hibSession = AssignmentChangeDAO.getInstance().getSession();
			Transaction tx = hibSession.beginTransaction();
			try {
				hibSession.createMutationQuery("update AssignmentChange set requesterSeen = true where session.uniqueId = :sessionId and requestedBy = :me and requesterSeen = false")
					.setParameter("sessionId", sessionId).setParameter("me", me).executeUpdate();
				tx.commit();
			} catch (Exception e) {
				tx.rollback();
				throw new GwtRpcException(e.getMessage(), e);
			}
			break;
		case LIST:
			String filter = (request.getFilter() == null ? (canApprove ? "PENDING" : "MINE") : request.getFilter());
			String status = null, requestedBy = null;
			if ("PENDING".equals(filter)) {
				status = AssignmentChange.Status.PENDING.name();
				if (!canApprove) requestedBy = me;
			} else if ("MINE".equals(filter) || !canApprove) {
				requestedBy = me;
			}
			for (AssignmentChange x: AssignmentChangeDAO.getInstance().find(AssignmentChangeDAO.getInstance().getSession(), sessionId, status, requestedBy, request.getSubjectId(), 500))
				response.addChange(assignmentChangeService.toInfo(context, x, canApprove));
			break;
		default:
			break;
		}

		// INFO, and counts for every call (drive the header badge)
		response.setPolicy(assignmentChangeService.getPolicy());
		response.setCanApprove(canApprove);
		response.setCanRequest(canRequest);
		response.setClassNeedsApproval(assignmentChangeService.needsApproval(context, AssignmentChange.Type.CLASS, AssignmentChange.Mode.SOLVER));
		response.setExamSolverNeedsApproval(assignmentChangeService.needsApproval(context, AssignmentChange.Type.EXAM, AssignmentChange.Mode.SOLVER));
		response.setExamDatabaseNeedsApproval(assignmentChangeService.needsApproval(context, AssignmentChange.Type.EXAM, AssignmentChange.Mode.DATABASE));
		if (canApprove || canRequest) {
			AssignmentChangeDAO dao = AssignmentChangeDAO.getInstance();
			org.hibernate.Session hs = dao.getSession();
			if (canApprove) response.setPendingCount((int)dao.count(hs, sessionId, AssignmentChange.Status.PENDING.name(), null, null));
			response.setMyPendingCount((int)dao.count(hs, sessionId, AssignmentChange.Status.PENDING.name(), me, null));
			response.setUnseenDecisions((int)dao.count(hs, sessionId, null, me, Boolean.FALSE));
		}
		return response;
	}
}
