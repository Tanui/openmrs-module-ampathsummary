/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.ampathsummary.reporting.data.evaluator;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Cohort;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.annotation.Handler;
import org.openmrs.api.context.Context;
import org.openmrs.module.ampathsummary.reporting.data.definition.MultiConceptObsDataDefinition;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.common.ListMap;
import org.openmrs.module.reporting.common.TimeQualifier;
import org.openmrs.module.reporting.data.person.EvaluatedPersonData;
import org.openmrs.module.reporting.data.person.definition.PersonDataDefinition;
import org.openmrs.module.reporting.data.person.evaluator.PersonDataEvaluator;
import org.openmrs.module.reporting.dataset.query.service.DataSetQueryService;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.EvaluationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 */
@Handler(supports = MultiConceptObsDataDefinition.class, order = 50)
public class MultiConceptObsDataEvaluator implements PersonDataEvaluator {

    /**
     * @should return the obs that match the passed definition configuration
     * @see org.openmrs.module.reporting.data.person.evaluator.PersonDataEvaluator#evaluate(org.openmrs.module.reporting.data.person.definition.PersonDataDefinition, org.openmrs.module.reporting.evaluation.EvaluationContext)
     */
    public EvaluatedPersonData evaluate(final PersonDataDefinition definition, final EvaluationContext context) throws EvaluationException {

        MultiConceptObsDataDefinition dataDefinition = (MultiConceptObsDataDefinition) definition;
        EvaluatedPersonData data = new EvaluatedPersonData(dataDefinition, context);
        Cohort cohort = context.getBaseCohort();

        if (cohort != null && cohort.isEmpty())
            return data;

        DataSetQueryService qs = Context.getService(DataSetQueryService.class);

        StringBuilder hql = new StringBuilder();
        Map<String, Object> mappings = new HashMap<String, Object>();

        hql.append(" from Obs ");
        hql.append(" where voided = false ");

        if (cohort != null) {
            hql.append("   and personId in (:patientIds) ");
            mappings.put("patientIds", context.getBaseCohort());
        }

        List<Concept> questions = dataDefinition.getQuestions();
        if (!questions.isEmpty()) {

            int counter = 0;
            List<String> questionClauses = new ArrayList<String>();
            for (Concept question : questions) {
                String key = "question" + counter ++;
                questionClauses.add("concept.conceptId = :" + key);
                mappings.put(key, question.getConceptId());
            }

            String questionClause = "   and ( " + StringUtils.join(questionClauses, " or ") + " ) ";
            hql.append(questionClause);
        }

        if (dataDefinition.getOnOrAfter() != null) {
            hql.append("   and obsDatetime >= :onOrAfter ");
            mappings.put("onOrAfter", dataDefinition.getOnOrAfter());
        }

        if (dataDefinition.getOnOrBefore() != null) {
            hql.append("   and obsDatetime <= :onOrBefore ");
            mappings.put("onOrBefore", DateUtil.getEndOfDayIfTimeExcluded(dataDefinition.getOnOrBefore()));
        }

        String ordering = " asc ";
        if (dataDefinition.getWhich() == TimeQualifier.LAST)
            ordering = " desc ";

        hql.append(" order by obsDatetime ").append(ordering);

        System.out.println("HQL: " + hql.toString());
        List<Object> queryResult = qs.executeHqlQuery(hql.toString(), mappings);

        ListMap<Integer, Obs> obsForPatients = new ListMap<Integer, Obs>();
        for (Object object : queryResult) {
            Obs obs = (Obs) object;
            obsForPatients.putInList(obs.getPersonId(), obs);
        }

        for (Integer personId : obsForPatients.keySet()) {
            List<Obs> obsList = obsForPatients.get(personId);
            if (dataDefinition.getWhich() == TimeQualifier.LAST
                    || dataDefinition.getWhich() == TimeQualifier.FIRST) {
                data.addData(personId, obsList.get(0));
            } else {
                data.addData(personId, obsList);
            }
        }

        return data;
    }
}