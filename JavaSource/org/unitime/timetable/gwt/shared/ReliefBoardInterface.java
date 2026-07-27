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
 * Protocol for the Relief Planning oversight board. {@code LOAD} returns every
 * vacated lesson on a date together with its current relief teacher and the ranked
 * list of free candidates; {@code GENERATE} runs the allocation engine for the date;
 * {@code REASSIGN} sets a lesson's relief teacher manually; {@code CLEAR} unassigns
 * one. Dates are {@code yyyy-MM-dd}. Additive — introduces no changes to existing
 * behavior.
 *
 * @author Angular migration (Relief Planning)
 */
public class ReliefBoardInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD, GENERATE, REASSIGN, CLEAR
	}

	/** A teacher free to cover a given lesson. */
	public static class CandidateInfo implements IsSerializable {
		private String iUid;
		private String iName;
		private boolean iSameDept;
		private int iWeekLoad;

		public CandidateInfo() {}
		public CandidateInfo(String uid, String name, boolean sameDept, int weekLoad) {
			iUid = uid; iName = name; iSameDept = sameDept; iWeekLoad = weekLoad;
		}
		public String getUid() { return iUid; }
		public void setUid(String uid) { iUid = uid; }
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
		public boolean isSameDept() { return iSameDept; }
		public void setSameDept(boolean sameDept) { iSameDept = sameDept; }
		public int getWeekLoad() { return iWeekLoad; }
		public void setWeekLoad(int weekLoad) { iWeekLoad = weekLoad; }
	}

	/** One vacated lesson and its relief assignment. */
	public static class LessonInfo implements IsSerializable {
		private Long iId;
		private String iAbsentName;
		private String iReasonLabel;
		private String iClassName;
		private String iTimeText;
		private String iRoomName;
		private String iReliefUid;
		private String iReliefName;
		private int iStatus;
		private String iStatusLabel;
		private List<CandidateInfo> iCandidates = new ArrayList<CandidateInfo>();

		public LessonInfo() {}
		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }
		public String getAbsentName() { return iAbsentName; }
		public void setAbsentName(String absentName) { iAbsentName = absentName; }
		public String getReasonLabel() { return iReasonLabel; }
		public void setReasonLabel(String reasonLabel) { iReasonLabel = reasonLabel; }
		public String getClassName() { return iClassName; }
		public void setClassName(String className) { iClassName = className; }
		public String getTimeText() { return iTimeText; }
		public void setTimeText(String timeText) { iTimeText = timeText; }
		public String getRoomName() { return iRoomName; }
		public void setRoomName(String roomName) { iRoomName = roomName; }
		public String getReliefUid() { return iReliefUid; }
		public void setReliefUid(String reliefUid) { iReliefUid = reliefUid; }
		public String getReliefName() { return iReliefName; }
		public void setReliefName(String reliefName) { iReliefName = reliefName; }
		public int getStatus() { return iStatus; }
		public void setStatus(int status) { iStatus = status; }
		public String getStatusLabel() { return iStatusLabel; }
		public void setStatusLabel(String statusLabel) { iStatusLabel = statusLabel; }
		public List<CandidateInfo> getCandidates() { return iCandidates; }
		public void addCandidate(CandidateInfo c) { iCandidates.add(c); }
	}

	public static class ReliefBoardRequest implements GwtRpcRequest<ReliefBoardResponse> {
		private Operation iOperation = Operation.LOAD;
		private String iDate;
		private Long iLessonId;
		private String iReliefUid;

		public ReliefBoardRequest() {}
		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }
		public String getDate() { return iDate; }
		public void setDate(String date) { iDate = date; }
		public Long getLessonId() { return iLessonId; }
		public void setLessonId(Long lessonId) { iLessonId = lessonId; }
		public String getReliefUid() { return iReliefUid; }
		public void setReliefUid(String reliefUid) { iReliefUid = reliefUid; }

		@Override
		public String toString() { return "ReliefBoard[" + iOperation + "," + iDate + "]"; }
	}

	public static class ReliefBoardResponse implements GwtRpcResponse {
		private String iDate;
		private boolean iCanManage;
		private int iGeneratedCount;
		private List<LessonInfo> iLessons = new ArrayList<LessonInfo>();

		public ReliefBoardResponse() {}
		public String getDate() { return iDate; }
		public void setDate(String date) { iDate = date; }
		public boolean isCanManage() { return iCanManage; }
		public void setCanManage(boolean canManage) { iCanManage = canManage; }
		public int getGeneratedCount() { return iGeneratedCount; }
		public void setGeneratedCount(int generatedCount) { iGeneratedCount = generatedCount; }
		public List<LessonInfo> getLessons() { return iLessons; }
		public void addLesson(LessonInfo l) { iLessons.add(l); }
	}
}
