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
package org.unitime.timetable.server.exams;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.unitime.timetable.form.ExamInfoForm;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExamAssignmentDialogInterface.ExamAssignmentDialogRequest;
import org.unitime.timetable.gwt.shared.ExamAssignmentDialogInterface.ExamAssignmentDialogResponse;
import org.unitime.timetable.gwt.shared.ExamAssignmentDialogInterface.ExamChange;
import org.unitime.timetable.gwt.shared.ExamAssignmentDialogInterface.ExamChangeLine;
import org.unitime.timetable.gwt.shared.ExamAssignmentDialogInterface.ExamConflict;
import org.unitime.timetable.gwt.shared.ExamAssignmentDialogInterface.ExamPeriodOption;
import org.unitime.timetable.gwt.shared.ExamAssignmentDialogInterface.ExamPlacement;
import org.unitime.timetable.gwt.shared.ExamAssignmentDialogInterface.ExamRoomOption;
import org.unitime.timetable.gwt.shared.ExamAssignmentDialogInterface.Operation;
import org.unitime.timetable.model.AssignmentChange;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.PreferenceLevel;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.dao.AssignmentChangeDAO;
import org.unitime.timetable.model.dao.ExamDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.server.approval.AssignmentChangeService;
import org.unitime.timetable.solver.exam.ExamSolverProxy;
import org.unitime.timetable.solver.exam.ui.ExamAssignment;
import org.unitime.timetable.solver.exam.ui.ExamAssignmentInfo;
import org.unitime.timetable.solver.exam.ui.ExamAssignmentInfo.BackToBackConflict;
import org.unitime.timetable.solver.exam.ui.ExamAssignmentInfo.DirectConflict;
import org.unitime.timetable.solver.exam.ui.ExamAssignmentInfo.DistributionConflict;
import org.unitime.timetable.solver.exam.ui.ExamAssignmentInfo.MoreThanTwoADayConflict;
import org.unitime.timetable.solver.exam.ui.ExamInfo;
import org.unitime.timetable.solver.exam.ui.ExamInfoModel;
import org.unitime.timetable.solver.exam.ui.ExamProposedChange;
import org.unitime.timetable.solver.exam.ui.ExamRoomInfo;
import org.unitime.timetable.solver.exam.ui.ExamSuggestionsInfo;
import org.unitime.timetable.solver.service.ExaminationSolverService;
import org.unitime.timetable.util.RoomAvailability;

/**
 * Backend of the Angular examination assignment dialog (ExamAssignmentDialogRequest).
 *
 * It drives the same ExamInfoModel as the legacy examInfo.action page and returns structured
 * data instead of HTML: available periods with the conflicts each would create, available rooms,
 * the pending change with every affected (moved or bumped) exam, and suggestions.
 *
 * Like the legacy page, it works in two modes:
 *   - when an examination solver of the exam's type is loaded, changes go to the in-memory solution;
 *   - otherwise changes are written to the database (Exam.assign / Exam.unassign), which also
 *     records ChangeLog entries.
 *
 * The pending change is kept in the HTTP session between calls, as the legacy page does.
 * Every operation checks Right.ExaminationAssignment on the exam, as examInfo.action does.
 */
@GwtRpcImplements(ExamAssignmentDialogRequest.class)
public class ExamAssignmentDialogBackend implements GwtRpcImplementation<ExamAssignmentDialogRequest, ExamAssignmentDialogResponse> {
	private static Logger sLog = LogManager.getLogger(ExamAssignmentDialogBackend.class);

	private static final String ATTR_MODEL = "ExamAssignmentDialog.model";
	private static final String ATTR_FORM = "ExamAssignmentDialog.form";
	private static final String ATTR_SUGGESTIONS = "ExamAssignmentDialog.suggestions";

	@Autowired ExaminationSolverService examinationSolverService;
	@Autowired AssignmentChangeService assignmentChangeService;

	@Override
	public ExamAssignmentDialogResponse execute(ExamAssignmentDialogRequest request, SessionContext context) {
		Operation op = (request.getOperation() == null ? Operation.LOAD : request.getOperation());

		ExamInfoModel model = (ExamInfoModel)context.getAttribute(ATTR_MODEL);
		ExamInfoForm form = (ExamInfoForm)context.getAttribute(ATTR_FORM);
		if (model == null || form == null || op == Operation.LOAD) {
			model = new ExamInfoModel();
			form = new ExamInfoForm();
			form.reset();
			model.clear(context.getUser());
			context.setAttribute(ATTR_MODEL, model);
			context.setAttribute(ATTR_FORM, form);
			context.setAttribute(ATTR_SUGGESTIONS, Boolean.FALSE);
		}

		ExamAssignmentDialogResponse response = new ExamAssignmentDialogResponse();
		synchronized (model) {
			model.setForm(form);
			form.setModel(model);
			ExamSolverProxy solver = null;
			try {
				solver = examinationSolverService.getSolver();
			} catch (Exception e) {
				sLog.warn("Unable to obtain the examination solver: " + e.getMessage(), e);
			}
			model.setSolver(solver);
			boolean showSuggestions = Boolean.TRUE.equals(context.getAttribute(ATTR_SUGGESTIONS));

			try {
				if (op == Operation.LOAD || op == Operation.SELECT_EXAM) {
					if (request.getExamId() == null) throw new GwtRpcException("No examination given.");
					Exam exam = ExamDAO.getInstance().get(request.getExamId());
					if (exam == null) throw new GwtRpcException("Examination " + request.getExamId() + " does not exist.");
					context.checkPermission(exam, Right.ExaminationAssignment);
					if (op == Operation.LOAD) activateRoomAvailability(context, exam);
					model.setExam(exam);
					form.setSessionId(context.getUser().getCurrentAcademicSessionId());
					form.setExamTypeId(exam.getExamType().getUniqueId());
					showSuggestions = false;
				} else {
					if (model.getExam() == null)
						throw new GwtRpcException("The examination assignment dialog has expired. Please open it again.");
					context.checkPermission(model.getExam().getExam(), Right.ExaminationAssignment);
					applyOptions(request, form, model);
				}

				switch (op) {
				case SELECT_PERIOD:
					if (request.getPeriodId() == null) throw new GwtRpcException("No period given.");
					model.setPeriod(request.getPeriodId());
					showSuggestions = false;
					break;
				case SELECT_ROOMS:
					model.setRooms(roomsParameter(request.getRoomIds()));
					showSuggestions = false;
					break;
				case REMOVE:
					if (request.getExamId() != null) model.delete(request.getExamId());
					showSuggestions = false;
					break;
				case SUGGESTIONS:
					if (!model.getCanComputeSuggestions())
						throw new GwtRpcException("Suggestions need a loaded examination solver of the " + model.getExam().getExamTypeLabel() + " type.");
					model.refreshSuggestions();
					showSuggestions = true;
					break;
				case SELECT_SUGGESTION:
					if (request.getSuggestionIndex() == null) throw new GwtRpcException("No suggestion given.");
					model.setSuggestion(request.getSuggestionIndex());
					model.refreshRooms();
					break;
				case ASSIGN:
					if (!model.getCanAssign()) throw new GwtRpcException("The selected change is not complete (pick a period and enough rooms first).");
					String reason = AssignmentChangeService.checkReason(request.getReason());
					AssignmentChange.Mode mode = (model.getCanComputeSuggestions() ? AssignmentChange.Mode.SOLVER : AssignmentChange.Mode.DATABASE);
					ExamProposedChange pendingChange = model.getChange();
					String summary = summarize(toChange(pendingChange, null));
					Exam changed = ExamDAO.getInstance().get(model.getExam().getExamId());
					if (assignmentChangeService.needsApproval(context, AssignmentChange.Type.EXAM, mode)) {
						// stored as a pending request; nothing is changed until an approver approves it
						AssignmentChange request2 = assignmentChangeService.submitPendingExamChange(context, changed, mode, pendingChange, reason, summary);
						response.setSubmitted(true);
						response.setChangeRequestId(request2.getUniqueId());
						model.clear(context.getUser());
						model.setExam(changed);
						showSuggestions = false;
						break;
					}
					String message = model.assign();
					if (message == null || message.trim().isEmpty()) {
						assignmentChangeService.recordAppliedExamChange(context, changed, mode, pendingChange, reason, summary, null);
						response.setAssigned(true);
						// ExamInfo caches the Exam entity of an earlier request: reload and refresh it so the
						// dialog shows the committed state, not the stale pre-assignment copy.
						Long examId = model.getExam().getExamId();
						org.hibernate.Session hibSession = ExamDAO.getInstance().getSession();
						Exam exam = ExamDAO.getInstance().get(examId, hibSession);
						hibSession.refresh(exam);
						model.clear(context.getUser());
						model.setExam(exam);
					} else {
						response.setMessage(message);
					}
					showSuggestions = false;
					break;
				case CLEAR:
					Exam exam = model.getExam().getExam();
					model.clear(context.getUser());
					model.setExam(exam);
					showSuggestions = false;
					break;
				default:
					break;
				}
			} catch (GwtRpcException e) {
				throw e;
			} catch (Exception e) {
				sLog.error("Examination assignment dialog failed: " + e.getMessage(), e);
				throw new GwtRpcException(e.getMessage(), e);
			}
			context.setAttribute(ATTR_SUGGESTIONS, showSuggestions);

			fillResponse(response, model, form, showSuggestions);
			fillApproval(response, model, context);
		}
		return response;
	}

	/** Room and suggestion options sent by the dialog (only the ones present are applied). */
	protected void applyOptions(ExamAssignmentDialogRequest request, ExamInfoForm form, ExamInfoModel model) {
		boolean roomsChanged = false;
		if (request.getMinRoomSize() != null || request.getMaxRoomSize() != null || request.getRoomFilter() != null || request.getAllowRoomConflict() != null) {
			form.setMinRoomSize(request.getMinRoomSize() == null || request.getMinRoomSize() < 0 ? null : String.valueOf(request.getMinRoomSize()));
			form.setMaxRoomSize(request.getMaxRoomSize() == null || request.getMaxRoomSize() < 0 ? null : String.valueOf(request.getMaxRoomSize()));
			form.setRoomFilter(request.getRoomFilter() == null || request.getRoomFilter().trim().isEmpty() ? null : request.getRoomFilter().trim());
			if (request.getAllowRoomConflict() != null) form.setAllowRoomConflict(request.getAllowRoomConflict());
			roomsChanged = true;
		}
		if (request.getRoomOrder() != null) form.setRoomOrder(request.getRoomOrder());
		if (request.getDepth() != null && request.getDepth() > 0) form.setDepth(Math.min(request.getDepth(), 10));
		if (request.getTimeout() != null && request.getTimeout() > 0) form.setTimeout(Math.min(request.getTimeout(), 60000l));
		if (request.getFilter() != null) form.setFilter(request.getFilter().trim().isEmpty() ? null : request.getFilter().trim());
		if (roomsChanged) model.refreshRooms();
	}

	/** ExamInfoModel.setRooms expects ":roomId@capacity:roomId@capacity"; only the ids are read. */
	protected static String roomsParameter(List<Long> roomIds) {
		StringBuffer ret = new StringBuffer();
		if (roomIds != null)
			for (Long id: roomIds)
				if (id != null) ret.append(":").append(id).append("@0");
		return ret.toString();
	}

	/** Same as examInfo.action: make event room availability visible to the exam room checks. */
	protected void activateRoomAvailability(SessionContext context, Exam exam) {
		try {
			if (RoomAvailability.getInstance() == null) return;
			Session session = SessionDAO.getInstance().get(context.getUser().getCurrentAcademicSessionId());
			Date[] bounds = ExamPeriod.getBounds(session, exam.getExamType().getUniqueId());
			if (bounds == null) return;
			RoomAvailability.getInstance().activate(session.getUniqueId(), bounds[0], bounds[1], exam.getExamType().getReference(), false);
		} catch (Exception e) {
			sLog.warn("Unable to activate room availability: " + e.getMessage(), e);
		}
	}

	protected void fillResponse(ExamAssignmentDialogResponse response, ExamInfoModel model, ExamInfoForm form, boolean showSuggestions) {
		ExamInfo exam = model.getExam();
		response.setExamId(exam.getExamId());
		response.setExamName(exam.getExamName());
		try { response.setExamType(exam.getExamTypeLabel()); } catch (Exception e) {}
		response.setNrStudents(exam.getNrStudents());
		response.setMaxRooms(exam.getMaxRooms());
		response.setLength(exam.getLength());
		try { response.setSeatingType(exam.getSeatingTypeLabel()); } catch (Exception e) {}
		response.setSolverMode(model.getCanComputeSuggestions());
		response.setCanComputeSuggestions(model.getCanComputeSuggestions());
		response.setCanAssign(model.getCanAssign());
		response.setAssignConfirm(model.getAssignConfirm());

		ExamAssignmentInfo old = model.getExamOldAssignment();
		if (old != null && old.getPeriodId() != null)
			response.setCurrent(toPlacement(old));

		ExamAssignmentInfo selected = null;
		try {
			selected = model.getSelectedAssignment();
		} catch (Exception e) {
			sLog.warn("Unable to compute the selected assignment: " + e.getMessage(), e);
		}
		if (selected != null) response.setSelected(toPlacement(selected));

		// Periods: the one in the pending change (or the current one) is marked selected.
		ExamProposedChange change = model.getChange();
		ExamAssignment pending = (change == null ? null : change.getCurrent(exam));
		Long selectedPeriodId = (pending != null ? pending.getPeriodId() : model.isExamAssigned() ? model.getExamAssignment().getPeriodId() : null);
		Collection<ExamAssignmentInfo> periods = model.getPeriods();
		if (periods != null) {
			for (ExamAssignmentInfo p: periods) {
				ExamPeriodOption o = new ExamPeriodOption();
				o.setPeriodId(p.getPeriodId());
				o.setName(p.getPeriodName());
				o.setPref(p.getPeriodPref());
				o.setInitial(old != null && old.getPeriodId() != null && old.getPeriodId().equals(p.getPeriodId()));
				o.setSelected(selectedPeriodId != null && selectedPeriodId.equals(p.getPeriodId()));
				o.setCounts(p.getNrDirectConflicts(), p.getNrMoreThanTwoConflicts(), p.getNrBackToBackConflicts(), p.getNrDistanceBackToBackConflicts(),
						p.getNrInstructorDirectConflicts(), p.getNrInstructorMoreThanTwoConflicts(), p.getNrInstructorBackToBackConflicts());
				if (p.getDistributionConflicts() != null)
					for (DistributionConflict dc: p.getDistributionConflicts())
						o.getDistributionConflicts().add(dc.getType() + " (" + PreferenceLevel.prolog2string(dc.getPreference()) + ")");
				response.getPeriods().add(o);
			}
		}

		// Rooms available in the selected period.
		List<ExamRoomInfo> rooms = model.getRooms();
		if (rooms != null) {
			final ExamInfo fExam = exam;
			final String order = form.getRoomOrder();
			List<ExamRoomInfo> sorted = new ArrayList<ExamRoomInfo>(rooms);
			Collections.sort(sorted, new Comparator<ExamRoomInfo>() {
				@Override
				public int compare(ExamRoomInfo r1, ExamRoomInfo r2) {
					int cmp = 0;
					if (ExamInfoForm.OrderBy.NameDesc.name().equals(order)) cmp = -r1.getName().compareTo(r2.getName());
					else if (ExamInfoForm.OrderBy.SizeAsc.name().equals(order)) cmp = Double.compare(r1.getCapacity(fExam), r2.getCapacity(fExam));
					else if (ExamInfoForm.OrderBy.SizeDesc.name().equals(order)) cmp = -Double.compare(r1.getCapacity(fExam), r2.getCapacity(fExam));
					else cmp = r1.getName().compareTo(r2.getName());
					if (cmp != 0) return cmp;
					cmp = r1.getName().compareTo(r2.getName());
					return (cmp != 0 ? cmp : r1.getLocationId().compareTo(r2.getLocationId()));
				}
			});
			Collection<ExamRoomInfo> assigned = (pending != null ? pending.getRooms() : model.isExamAssigned() ? model.getExamAssignment().getRooms() : null);
			Collection<ExamRoomInfo> original = (old != null ? old.getRooms() : null);
			List<ExamRoomOption> options = new ArrayList<ExamRoomOption>();
			for (ExamRoomInfo room: sorted) {
				ExamRoomOption o = new ExamRoomOption();
				o.setRoomId(room.getLocationId());
				o.setName(room.getName());
				o.setCapacity(room.getCapacity(exam));
				o.setPref(PreferenceLevel.int2prolog(room.getPreference()));
				o.setHard(room.isHard());
				o.setSelected(assigned != null && assigned.contains(room));
				o.setInitial(original != null && original.contains(room));
				options.add(o);
			}
			response.setRooms(options);
		}

		if (change != null) response.setChange(toChange(change, exam.getExamId()));

		if (showSuggestions && model.getCanComputeSuggestions()) {
			ExamSuggestionsInfo suggestions = model.getSuggestions();
			if (suggestions != null) {
				List<ExamChange> list = new ArrayList<ExamChange>();
				if (suggestions.getSuggestions() != null)
					for (ExamProposedChange s: suggestions.getSuggestions())
						list.add(toChange(s, null));
				response.setSuggestions(list);
				response.setSuggestionsMessage(suggestions.getMessage());
				response.setSuggestionsTimeout(suggestions.getTimeoutReached());
			}
		}

		try { response.setMinRoomSize(form.getMinRoomSize() == null || form.getMinRoomSize().isEmpty() ? null : Integer.valueOf(form.getMinRoomSize().trim())); } catch (NumberFormatException e) {}
		try { response.setMaxRoomSize(form.getMaxRoomSize() == null || form.getMaxRoomSize().isEmpty() ? null : Integer.valueOf(form.getMaxRoomSize().trim())); } catch (NumberFormatException e) {}
		response.setRoomFilter(form.getRoomFilter());
		response.setRoomOrder(form.getRoomOrder());
		response.setAllowRoomConflict(form.getAllowRoomConflict());
		response.setDepth(form.getDepth());
		response.setTimeout(form.getTimeout());
	}

	/** Approval state and the pending requests of this exam (shown as a banner in the dialog). */
	protected void fillApproval(ExamAssignmentDialogResponse response, ExamInfoModel model, SessionContext context) {
		AssignmentChange.Mode mode = (model.getCanComputeSuggestions() ? AssignmentChange.Mode.SOLVER : AssignmentChange.Mode.DATABASE);
		response.setNeedsApproval(assignmentChangeService.needsApproval(context, AssignmentChange.Type.EXAM, mode));
		try {
			boolean canApprove = assignmentChangeService.canApprove(context);
			List<org.unitime.timetable.gwt.shared.AssignmentChangeInterface.AssignmentChangeInfo> pending = new ArrayList<org.unitime.timetable.gwt.shared.AssignmentChangeInterface.AssignmentChangeInfo>();
			for (AssignmentChange c: AssignmentChangeDAO.getInstance().find(AssignmentChangeDAO.getInstance().getSession(),
					context.getUser().getCurrentAcademicSessionId(), AssignmentChange.Status.PENDING.name(), null, model.getExam().getExamId(), 20))
				pending.add(assignmentChangeService.toInfo(context, c, canApprove));
			if (!pending.isEmpty()) response.setPendingRequests(pending);
		} catch (Exception e) {
			sLog.warn("Unable to read pending change requests: " + e.getMessage(), e);
		}
	}

	/** One line per affected exam, e.g. "ALG 101: Tue 12/14 1:00p EDUC 102 -> Wed 12/15 1:00p EDUC 102". */
	protected static String summarize(ExamChange change) {
		StringBuffer ret = new StringBuffer();
		for (ExamChangeLine l: change.getLines()) {
			if (ret.length() > 0) ret.append("\n");
			String from = (l.getFromPeriod() == null ? "not assigned" : l.getFromPeriod() + (l.getFromRooms() == null || l.getFromRooms().isEmpty() ? "" : " " + l.getFromRooms()));
			String to = (l.isUnassigned() || l.getToPeriod() == null ? "not assigned" : l.getToPeriod() + (l.getToRooms() == null || l.getToRooms().isEmpty() ? "" : " " + l.getToRooms()));
			ret.append(l.getExam()).append(": ").append(from).append(" -> ").append(to);
			if (l.getDirect() != l.getDirectBefore() || l.getBackToBack() != l.getBackToBackBefore() || l.getMoreThanTwoADay() != l.getMoreThanTwoADayBefore())
				ret.append(" (direct ").append(l.getDirectBefore()).append("->").append(l.getDirect())
					.append(", >2/day ").append(l.getMoreThanTwoADayBefore()).append("->").append(l.getMoreThanTwoADay())
					.append(", back-to-back ").append(l.getBackToBackBefore()).append("->").append(l.getBackToBack()).append(")");
		}
		return ret.toString();
	}

	protected static ExamPlacement toPlacement(ExamAssignmentInfo a) {
		ExamPlacement p = new ExamPlacement();
		p.setPeriodId(a.getPeriodId());
		p.setPeriod(a.getPeriodId() == null ? null : a.getPeriodName());
		p.setPeriodPref(a.getPeriodPref());
		if (a.getRooms() != null)
			for (ExamRoomInfo r: a.getRooms()) p.getRoomIds().add(r.getLocationId());
		p.setRooms(a.getRoomsName(", "));
		p.setRoomCapacity(a.getRoomsCapacity());
		p.setValid(a.isValid());
		p.setCounts(a.getNrDirectConflicts(), a.getNrMoreThanTwoConflicts(), a.getNrBackToBackConflicts(), a.getNrDistanceBackToBackConflicts(),
				a.getNrInstructorDirectConflicts(), a.getNrInstructorMoreThanTwoConflicts(), a.getNrInstructorBackToBackConflicts(),
				a.getNrDistributionConflicts());
		addDirect(p, a.getDirectConflicts(), false);
		addMoreThanTwo(p, a.getMoreThanTwoADaysConflicts(), false);
		addBackToBack(p, a.getBackToBackConflicts(), false);
		addDirect(p, a.getInstructorDirectConflicts(), true);
		addMoreThanTwo(p, a.getInstructorMoreThanTwoADaysConflicts(), true);
		addBackToBack(p, a.getInstructorBackToBackConflicts(), true);
		if (a.getDistributionConflicts() != null)
			for (DistributionConflict dc: a.getDistributionConflicts()) {
				StringBuffer other = new StringBuffer();
				if (dc.getOtherExams() != null)
					for (ExamInfo x: dc.getOtherExams()) other.append(other.length() == 0 ? "" : ", ").append(x.getExamName());
				ExamConflict c = new ExamConflict("Distribution", false, 1, dc.getType() + (other.length() == 0 ? "" : ": " + other), null);
				c.setPreference(dc.getPreference());
				p.getConflicts().add(c);
			}
		return p;
	}

	private static void addDirect(ExamPlacement p, Collection<DirectConflict> conflicts, boolean instructor) {
		if (conflicts == null) return;
		for (DirectConflict dc: conflicts) {
			String other, time;
			if (dc.getOtherExam() != null) {
				other = dc.getOtherExam().getExamName();
				time = dc.getOtherExam().getPeriodAbbreviation();
			} else if (dc.getOtherEventId() != null) {
				other = dc.getOtherEventName();
				time = dc.getOtherEventDate() + " " + dc.getOtherEventTime();
			} else {
				other = "not available"; time = null;
			}
			p.getConflicts().add(new ExamConflict("Direct", instructor, dc.getNrStudents(), other, time));
		}
	}

	private static void addBackToBack(ExamPlacement p, Collection<BackToBackConflict> conflicts, boolean instructor) {
		if (conflicts == null) return;
		for (BackToBackConflict btb: conflicts) {
			ExamAssignment other = btb.getOtherExam();
			p.getConflicts().add(new ExamConflict(btb.isDistance() ? "DistanceBackToBack" : "BackToBack", instructor, btb.getNrStudents(),
					other == null ? null : other.getExamName(), other == null ? null : other.getPeriodAbbreviation()));
		}
	}

	private static void addMoreThanTwo(ExamPlacement p, Collection<MoreThanTwoADayConflict> conflicts, boolean instructor) {
		if (conflicts == null) return;
		for (MoreThanTwoADayConflict m2d: conflicts) {
			StringBuffer names = new StringBuffer(), times = new StringBuffer();
			if (m2d.getOtherExams() != null)
				for (ExamAssignment x: m2d.getOtherExams()) {
					names.append(names.length() == 0 ? "" : ", ").append(x.getExamName());
					times.append(times.length() == 0 ? "" : ", ").append(x.getPeriodAbbreviation());
				}
			p.getConflicts().add(new ExamConflict("MoreThanTwoADay", instructor, m2d.getNrStudents(), names.toString(), times.toString()));
		}
	}

	protected static ExamChange toChange(ExamProposedChange change, Long selectedExamId) {
		ExamChange ret = new ExamChange();
		ret.setValue(change.getValue());
		ret.setNrAssigned(change.getNrAssigned());
		ret.setNrUnassigned(change.getNrUnassigned());
		for (ExamAssignmentInfo current: change.getAssignments()) {
			ExamAssignment initial = change.getInitial(current);
			ExamChangeLine line = new ExamChangeLine();
			line.setExamId(current.getExamId());
			line.setExam(current.getExamName());
			line.setSelected(current.getExamId().equals(selectedExamId));
			line.setFromPeriod(initial == null || initial.getPeriodId() == null ? null : initial.getPeriodAbbreviation());
			line.setFromRooms(initial == null ? null : initial.getRoomsName(", "));
			line.setToPeriod(current.getPeriodId() == null ? null : current.getPeriodAbbreviation());
			line.setToRooms(current.getRoomsName(", "));
			line.setRoomsMissing(current.getPeriodId() != null && current.getNrRooms() == 0 && current.getMaxRooms() > 0);
			line.setUnassigned(current.getPeriodId() == null);
			line.setCounts(
					current.getPlacementNrDirectConflicts(), initial == null ? 0 : initial.getPlacementNrDirectConflicts(),
					current.getPlacementNrMoreThanTwoADayConflicts(), initial == null ? 0 : initial.getPlacementNrMoreThanTwoADayConflicts(),
					current.getPlacementNrBackToBackConflicts(), initial == null ? 0 : initial.getPlacementNrBackToBackConflicts());
			ret.getLines().add(line);
		}
		for (ExamAssignment conflict: change.getConflicts()) {
			if (conflict == null) continue;
			ExamChangeLine line = new ExamChangeLine();
			line.setExamId(conflict.getExamId());
			line.setExam(conflict.getExamName());
			line.setSelected(conflict.getExamId().equals(selectedExamId));
			line.setUnassigned(true);
			line.setFromPeriod(conflict.getPeriodId() == null ? null : conflict.getPeriodAbbreviation());
			line.setFromRooms(conflict.getRoomsName(", "));
			line.setCounts(0, conflict.getPlacementNrDirectConflicts(), 0, conflict.getPlacementNrMoreThanTwoADayConflicts(), 0, conflict.getPlacementNrBackToBackConflicts());
			ret.getLines().add(line);
		}
		return ret;
	}
}
