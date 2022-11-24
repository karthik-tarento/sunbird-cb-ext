package org.sunbird.passbook.competency.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CompetencyInfo {
	private String competencyId;
	private Map<String, String> additionalParam;
	private List<Map<String, Object>> acquiredDetails = new ArrayList<Map<String, Object>>();

	public CompetencyInfo(String competencyId) {
		this.competencyId = competencyId;
	}

	public String getCompetencyId() {
		return competencyId;
	}

	public void setCompetencyId(String competencyId) {
		this.competencyId = competencyId;
	}

	public Map<String, String> getAdditionalParam() {
		return additionalParam;
	}

	public void setAdditionalParam(Map<String, String> additionalParam) {
		this.additionalParam = additionalParam;
	}

	public List<Map<String, Object>> getAcquiredDetails() {
		return acquiredDetails;
	}

	public void setAcquiredDetails(List<Map<String, Object>> acquiredDetails) {
		this.acquiredDetails = acquiredDetails;
	}
}
