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
package org.unitime.timetable.model;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import org.unitime.timetable.model.base.BaseReliefConfiguration;
import org.unitime.timetable.model.dao.SessionDAO;

/**
 * Per-session policy used by the relief-generation engine: a weekly relief cap,
 * whether to prefer subject/academic continuity, whether to exclude non-teaching
 * staff, and an explicit set of exempt staff (comma-separated external ids).
 * Exactly one row per academic session; {@link #getOrDefault} supplies sensible
 * defaults when a session has none yet.
 */
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Table(name = "relief_configuration")
public class ReliefConfiguration extends BaseReliefConfiguration {
	private static final long serialVersionUID = 1L;

	/** Default weekly relief-period cap per teacher when none is configured. */
	public static final int DEFAULT_WEEKLY_CAP = 10;

	public ReliefConfiguration() {
		super();
	}

	public ReliefConfiguration(Long uniqueId) {
		super(uniqueId);
	}

	public boolean preferContinuity() { return getPreferContinuity() == null || getPreferContinuity(); }
	public boolean excludeNonTeaching() { return getExcludeNonTeaching() != null && getExcludeNonTeaching(); }
	public boolean sameDeptFirst() { return getSameDeptFirst() == null || getSameDeptFirst(); }
	public int weeklyCap() { return getWeeklyCap() == null || getWeeklyCap() <= 0 ? DEFAULT_WEEKLY_CAP : getWeeklyCap(); }

	/** Parsed set of exempt external ids (never null). */
	public Set<String> exemptUidSet() {
		Set<String> ids = new HashSet<String>();
		if (getExemptUids() != null)
			for (String s: getExemptUids().split(","))
				if (!s.trim().isEmpty()) ids.add(s.trim());
		return ids;
	}

	public boolean isExempt(String externalUid) {
		return externalUid != null && exemptUidSet().contains(externalUid);
	}

	/** The session's configuration, or a transient default instance if none is stored. */
	public static ReliefConfiguration getOrDefault(org.hibernate.Session hibSession, Long sessionId) {
		ReliefConfiguration cfg = hibSession.createQuery(
				"from ReliefConfiguration c where c.session.uniqueId = :sessionId", ReliefConfiguration.class)
				.setParameter("sessionId", sessionId).setMaxResults(1).uniqueResult();
		if (cfg != null) return cfg;
		cfg = new ReliefConfiguration();
		cfg.setWeeklyCap(DEFAULT_WEEKLY_CAP);
		cfg.setPreferContinuity(true);
		cfg.setExcludeNonTeaching(true);
		cfg.setSameDeptFirst(true);
		cfg.setSession(SessionDAO.getInstance().get(sessionId, hibSession));
		return cfg;
	}
}
