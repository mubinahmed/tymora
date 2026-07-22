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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.unitime.timetable.gwt.command.client.GwtRpcResponseList;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExamCbsInterface.ExamCbsRequest;
import org.unitime.timetable.gwt.shared.SuggestionsInterface.CBSNode;
import org.unitime.timetable.model.PreferenceLevel;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.solver.exam.ExamSolverProxy;
import org.unitime.timetable.solver.exam.ui.ExamConflictStatisticsInfo;
import org.unitime.timetable.solver.exam.ui.ExamConflictStatisticsInfo.CBSAssignment;
import org.unitime.timetable.solver.exam.ui.ExamConflictStatisticsInfo.CBSConstraint;
import org.unitime.timetable.solver.exam.ui.ExamConflictStatisticsInfo.CBSValue;
import org.unitime.timetable.solver.exam.ui.ExamConflictStatisticsInfo.CBSVariable;
import org.unitime.timetable.solver.service.SolverService;

/**
 * READ-ONLY command bean backing the Angular Examination Conflict-Based
 * Statistics screen (migrates the legacy ecbs.action / ExamCbsAction, which
 * calls {@code getExaminationSolverService().getSolver().getCbsInfo()}).
 *
 * Reads the in-memory examination solver and converts its
 * {@link ExamConflictStatisticsInfo} tree (exam -> period -> constraint ->
 * assignment) into the shared {@link CBSNode} tree already used by the course
 * CBS screen. Two orientations are supported: variable-based (default in the
 * legacy page) and constraint-based (transposed), both filtered per-level by
 * the requested limit exactly like {@code ExamConflictStatisticsInfo.printHtml}.
 *
 * When no examination solver is loaded (or it produced no conflicts) an empty
 * list is returned (never throws) so the UI renders a "solver not loaded"
 * banner, mirroring the course CBS / solver-log screens.
 *
 * Permission-gated by {@link Right#ExaminationConflictStatistics} (Session
 * qualified). Additive: introduces no changes to existing behavior.
 *
 * Deferrals (noted, not implemented): the legacy page's click-through dialogs
 * (open the exam-assign dialog, jump to the examination timetable grid for a
 * room/instructor constraint) are not wired here -- the migrated screen is a
 * read-only tree. No {@code selection}/{@code link} is set on the nodes.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ExamCbsRequest.class)
public class ExamCbsBackend implements GwtRpcImplementation<ExamCbsRequest, GwtRpcResponseList<CBSNode>> {

	@Autowired
	SolverService<ExamSolverProxy> examinationSolverService;

	@Override
	public GwtRpcResponseList<CBSNode> execute(ExamCbsRequest request, SessionContext context) {
		Long sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		context.checkPermission(sessionId, "Session", Right.ExaminationConflictStatistics);

		// Remember the user's view preferences (mirror ExamCbsAction "Change").
		context.getUser().setProperty("Ecbs.type", request.isVariableOriented() ? "0" : "1");
		context.getUser().setProperty("Ecbs.limit", String.valueOf(request.getLimit()));

		GwtRpcResponseList<CBSNode> response = new GwtRpcResponseList<CBSNode>();

		ExamSolverProxy solver = examinationSolverService.getSolver();
		if (solver == null) return response; // no exam solver loaded -> empty -> UI banner

		ExamConflictStatisticsInfo cbs = solver.getCbsInfo();
		if (cbs == null || cbs.getCBS() == null || cbs.getCBS().isEmpty()) return response;

		double limit = request.getLimit() / 100.0;
		@SuppressWarnings("unchecked")
		Collection<CBSVariable> variables = cbs.getCBS();

		if (request.isVariableOriented())
			buildVariableBased(response, variables);
		else
			buildConstraintBased(response, variables);

		// Filter each sibling level by the requested limit (as printHtml does).
		filterTree(response, limit);
		return response;
	}

	// --- variable-based: exam -> period -> constraint -> assignment ----------

	private void buildVariableBased(List<CBSNode> response, Collection<CBSVariable> variables) {
		for (CBSVariable var : variables) {
			CBSNode varNode = variableNode(var);
			response.add(varNode);
			@SuppressWarnings("unchecked")
			Collection<CBSValue> values = var.values();
			for (CBSValue val : values) {
				CBSNode valNode = valueNode(val);
				varNode.addNode(valNode);
				@SuppressWarnings("unchecked")
				Collection<CBSConstraint> constraints = val.constraints();
				for (CBSConstraint con : constraints) {
					CBSNode conNode = constraintNode(con);
					valNode.addNode(conNode);
					@SuppressWarnings("unchecked")
					Collection<CBSAssignment> assignments = con.assignments();
					for (CBSAssignment ass : assignments)
						conNode.addNode(assignmentNode(ass));
				}
			}
		}
	}

	// --- constraint-based: constraint -> exam -> period -> assignment --------
	// Transposes the variable tree, aggregating counts bottom-up (matching the
	// incCounter propagation in ExamConflictStatisticsInfo.printHtml).

	private void buildConstraintBased(List<CBSNode> response, Collection<CBSVariable> variables) {
		// key = type + "." + id  ->  constraint node (+ its per-exam child map)
		Map<String, CBSNode> conNodes = new LinkedHashMap<String, CBSNode>();
		Map<String, Map<Long, CBSNode>> conVarNodes = new LinkedHashMap<String, Map<Long, CBSNode>>();

		for (CBSVariable var : variables) {
			@SuppressWarnings("unchecked")
			Collection<CBSValue> values = var.values();
			for (CBSValue val : values) {
				@SuppressWarnings("unchecked")
				Collection<CBSConstraint> constraints = val.constraints();
				for (CBSConstraint con : constraints) {
					String key = con.getType() + "." + con.getId();
					CBSNode conNode = conNodes.get(key);
					Map<Long, CBSNode> varNodes = conVarNodes.get(key);
					if (conNode == null) {
						conNode = constraintNode(con);
						conNode.setCount(0);
						conNodes.put(key, conNode);
						response.add(conNode);
						varNodes = new LinkedHashMap<Long, CBSNode>();
						conVarNodes.put(key, varNodes);
					}
					CBSNode varNode = varNodes.get(var.getId());
					if (varNode == null) {
						varNode = variableNode(var);
						varNode.setCount(0);
						varNodes.put(var.getId(), varNode);
						conNode.addNode(varNode);
					}
					CBSNode valNode = valueNode(val);
					valNode.setCount(0);
					varNode.addNode(valNode);
					@SuppressWarnings("unchecked")
					Collection<CBSAssignment> assignments = con.assignments();
					for (CBSAssignment ass : assignments) {
						CBSNode assNode = assignmentNode(ass);
						valNode.addNode(assNode);
						int c = ass.getCounter();
						valNode.setCount(valNode.getCount() + c);
						varNode.setCount(varNode.getCount() + c);
						conNode.setCount(conNode.getCount() + c);
					}
				}
			}
		}
	}

	// --- node builders -------------------------------------------------------

	private CBSNode variableNode(CBSVariable var) {
		CBSNode node = new CBSNode();
		node.setCount(var.getCounter());
		node.setName(var.getName());
		node.setPref(var.getPref());
		node.setClassId(var.getId());
		String color = PreferenceLevel.prolog2color(var.getPref());
		node.setHTML("<font color='" + color + "'>" + esc(var.getName()) + "</font>");
		return node;
	}

	private CBSNode valueNode(CBSValue val) {
		CBSNode node = new CBSNode();
		node.setCount(val.getCounter());
		StringBuilder name = new StringBuilder();
		StringBuilder html = new StringBuilder();
		name.append(val.getPeriodName());
		html.append("<font color='").append(PreferenceLevel.int2color(val.getPeriodPref())).append("'>")
			.append(esc(val.getPeriodName())).append("</font> ");
		appendRooms(name, html, val.getRoomNames(), val.getRoomPrefs());
		node.setName(name.toString());
		node.setHTML(html.toString());
		return node;
	}

	private CBSNode constraintNode(CBSConstraint con) {
		CBSNode node = new CBSNode();
		node.setCount(con.getCounter());
		node.setPref(con.getPref());
		String label;
		switch (con.getType()) {
			case ExamConflictStatisticsInfo.sConstraintTypeGroup:
				label = "Distribution " + con.getName();
				break;
			case ExamConflictStatisticsInfo.sConstraintTypeInstructor:
				label = "Instructor " + con.getName();
				break;
			case ExamConflictStatisticsInfo.sConstraintTypeRoom:
				label = "Room " + con.getName();
				break;
			case ExamConflictStatisticsInfo.sConstraintTypeStudent:
				label = "Student " + con.getName();
				break;
			default:
				label = (con.getName() == null ? "Unknown" : con.getName());
		}
		node.setName(label);
		node.setHTML("<font color='" + PreferenceLevel.prolog2color(con.getPref()) + "'>" + esc(label) + "</font>");
		return node;
	}

	private CBSNode assignmentNode(CBSAssignment ass) {
		CBSNode node = new CBSNode();
		node.setCount(ass.getCounter());
		StringBuilder name = new StringBuilder();
		StringBuilder html = new StringBuilder();
		name.append(ass.getName()).append(" ← ").append(ass.getPeriodName());
		html.append("<font color='").append(PreferenceLevel.prolog2color(ass.getPref())).append("'>")
			.append(esc(ass.getName())).append("</font> &larr; ")
			.append("<font color='").append(PreferenceLevel.int2color(ass.getPeriodPref())).append("'>")
			.append(esc(ass.getPeriodName())).append("</font> ");
		appendRooms(name, html, ass.getRoomNames(), ass.getRoomPrefs());
		node.setName(name.toString());
		node.setHTML(html.toString());
		return node;
	}

	@SuppressWarnings("rawtypes")
	private void appendRooms(StringBuilder name, StringBuilder html, List roomNames, List roomPrefs) {
		if (roomNames == null) return;
		for (int i = 0; i < roomNames.size(); i++) {
			String rn = String.valueOf(roomNames.get(i));
			int rp = PreferenceLevel.sIntLevelRequired;
			if (roomPrefs != null && i < roomPrefs.size() && roomPrefs.get(i) instanceof Integer)
				rp = ((Integer) roomPrefs.get(i)).intValue();
			name.append(i > 0 ? ", " : "").append(rn);
			html.append(i > 0 ? ", " : "").append("<font color='").append(PreferenceLevel.int2color(rp)).append("'>")
				.append(esc(rn)).append("</font>");
		}
	}

	// --- per-level filtering (mirrors ExamConflictStatisticsInfo.filter) -----

	private void filterTree(List<CBSNode> nodes, double limit) {
		if (nodes == null || nodes.isEmpty()) return;
		List<CBSNode> kept = filter(nodes, limit);
		nodes.clear();
		nodes.addAll(kept);
		for (CBSNode n : nodes)
			if (n.hasNodes()) filterTree(n.getNodes(), limit);
	}

	private List<CBSNode> filter(List<CBSNode> nodes, double limit) {
		List<CBSNode> sorted = new ArrayList<CBSNode>(nodes);
		sorted.sort(new Comparator<CBSNode>() {
			@Override
			public int compare(CBSNode a, CBSNode b) {
				return Integer.compare(b.getCount(), a.getCount());
			}
		});
		int total = 0;
		for (CBSNode n : sorted) total += n.getCount();
		int totalLimit = (int) Math.ceil(limit * total);
		int current = 0;
		List<CBSNode> ret = new ArrayList<CBSNode>();
		for (CBSNode n : sorted) {
			ret.add(n);
			current += n.getCount();
			if (current >= totalLimit) break;
		}
		return ret;
	}

	private static String esc(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
