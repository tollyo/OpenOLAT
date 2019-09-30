/**
* OLAT - Online Learning and Training<br>
* http://www.olat.org
* <p>
* Licensed under the Apache License, Version 2.0 (the "License"); <br>
* you may not use this file except in compliance with the License.<br>
* You may obtain a copy of the License at
* <p>
* http://www.apache.org/licenses/LICENSE-2.0
* <p>
* Unless required by applicable law or agreed to in writing,<br>
* software distributed under the License is distributed on an "AS IS" BASIS, <br>
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. <br>
* See the License for the specific language governing permissions and <br>
* limitations under the License.
* <p>
* Copyright (c) since 2004 at Multimedia- & E-Learning Services (MELS),<br>
* University of Zurich, Switzerland.
* <hr>
* <a href="http://www.openolat.org">
* OpenOLAT - Online Learning and Training</a><br>
* This file has been modified by the OpenOLAT community. Changes are licensed
* under the Apache 2.0 license as the original file.
*/

package org.olat.course.run.scoring;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.LazyInitializationException;
import org.olat.core.CoreSpringFactory;
import org.olat.core.id.Identity;
import org.olat.core.util.nodes.INode;
import org.olat.core.util.tree.TreeVisitor;
import org.olat.core.util.tree.Visitor;
import org.olat.course.assessment.CourseAssessmentService;
import org.olat.course.assessment.handler.AssessmentConfig;
import org.olat.course.condition.interpreter.ConditionInterpreter;
import org.olat.course.groupsandrights.CourseGroupManager;
import org.olat.course.nodes.CourseNode;
import org.olat.course.run.userview.UserCourseEnvironment;
import org.olat.modules.assessment.AssessmentEntry;
import org.olat.modules.assessment.model.AssessmentEntryStatus;
import org.olat.modules.assessment.model.AssessmentObligation;
import org.olat.modules.assessment.model.AssessmentRunStatus;
import org.olat.repository.RepositoryEntry;
import org.olat.repository.RepositoryService;
import org.olat.repository.model.RepositoryEntryLifecycle;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Description:<BR/>
 * The score accounting contains all score evaluations for a user
 * <P/>
 * Initial Date:  Oct 12, 2004
 *
 * @author Felix Jost
 */
public class ScoreAccountingImpl implements ScoreAccounting {
	
	private final UserCourseEnvironment userCourseEnvironment;
	private final Map<CourseNode, AssessmentEvaluation> cachedScoreEvals = new HashMap<>();
	
	@Autowired
	private CourseAssessmentService courseAssessmentService;

	/**
	 * Constructor of the user score accounting object
	 * @param userCourseEnvironment
	 */
	public ScoreAccountingImpl(UserCourseEnvironment userCourseEnvironment) {
		this.userCourseEnvironment = userCourseEnvironment;
		CoreSpringFactory.autowireObject(this);
	}

	/**
	 * Retrieve all the score evaluations for all course nodes
	 */
	@Override
	public void evaluateAll() {
		evaluateAll(false);
	}
	
	@Override
	public boolean evaluateAll(boolean update) {
		Identity identity = userCourseEnvironment.getIdentityEnvironment().getIdentity();
		List<AssessmentEntry> entries = userCourseEnvironment.getCourseEnvironment()
				.getAssessmentManager().getAssessmentEntries(identity);

		AssessableTreeVisitor visitor = new AssessableTreeVisitor(entries, update);
		// collect all assessable nodes and eval 'em
		CourseNode root = userCourseEnvironment.getCourseEnvironment().getRunStructure().getRootNode();
		// breadth first traversal gives an easier order of evaluation for debugging
		// however, for live it is absolutely mandatory to use depth first since using breadth first
		// the score accoutings local cache hash map will never be used. this can slow down things like 
		// crazy (course with 10 tests, 300 users and some crazy score and passed calculations will have
		// 10 time performance differences) 

		cachedScoreEvals.clear();
		for(AssessmentEntry entry:entries) {
			String nodeIdent = entry.getSubIdent();
			CourseNode courseNode = userCourseEnvironment.getCourseEnvironment().getRunStructure().getNode(nodeIdent);
			AssessmentEvaluation se = courseAssessmentService.toAssessmentEvaluation(entry, courseNode);
			cachedScoreEvals.put(courseNode, se);
		}
		
		TreeVisitor tv = new TreeVisitor(visitor, root, true); // true=depth first
		tv.visitAll();
		return visitor.hasChanges();
	}
	
	private class AssessableTreeVisitor implements Visitor {
		
		private final boolean update;
		private boolean changes = false;
		private int recursionLevel = 0;
		private final Map<String,AssessmentEntry> identToEntries = new HashMap<>();
		
		public AssessableTreeVisitor(List<AssessmentEntry> entries, boolean update) {
			this.update = update;
			for(AssessmentEntry entry:entries) {
				String ident = entry.getSubIdent();
				if(identToEntries.containsKey(ident)) {
					AssessmentEntry currentEntry = identToEntries.get(ident);
					if(entry.getLastModified().after(currentEntry.getLastModified())) {
						identToEntries.put(ident, entry);
					}
				} else {
					identToEntries.put(ident, entry);
				}
			}
		}
		
		public boolean hasChanges() {
			return changes;
		}

		@Override
		public void visit(INode node) {
			evalCourseNode((CourseNode)node);
		}
		
		public AssessmentEvaluation evalCourseNode(CourseNode cn) {
			// make sure we have no circular calculations
			recursionLevel++;
			AssessmentEvaluation se = null;
			if (recursionLevel <= 15) {
				AssessmentConfig assessmentConfig = courseAssessmentService.getAssessmentConfig(cn);
				if (update && assessmentConfig.isEvaluationCalculated()) {
					AssessmentEntry entry = identToEntries.get(cn.getIdent());
					se = calculateScoreEvaluation(entry, cn, assessmentConfig);
					cachedScoreEvals.put(cn, se);
				} else {
					se = cachedScoreEvals.get(cn);
					if (se == null) { // result of this node has not been cached yet, do it
						if(assessmentConfig.isEvaluationPersisted()) {
							AssessmentEntry entry = identToEntries.get(cn.getIdent());
							se = courseAssessmentService.toAssessmentEvaluation(entry, assessmentConfig);
						} else {
							se = courseAssessmentService.getAssessmentEvaluation(cn, userCourseEnvironment);
						}
						cachedScoreEvals.put(cn, se);
					}
				}
			}
			recursionLevel--;
			return se;
		}
		
		private void updateLastModified(CourseNode cNode, LastModifications lastModifications) {
			AssessmentEvaluation eval = cachedScoreEvals.get(cNode);
			if(eval != null) {
				lastModifications.addLastUserModified(eval.getLastUserModified());
				lastModifications.addLastCoachModified(eval.getLastCoachModified());
			}
			
			for(int i=cNode.getChildCount(); i-->0; ) {
				updateLastModified((CourseNode)cNode.getChildAt(i), lastModifications);
			}
		}
		
		/**
		 * Recalculate the score of structure nodes.
		 * 
		 * @param entry
		 * @param cNode
		 * @param assessmentConfig 
		 * @return
		 */
		private AssessmentEvaluation calculateScoreEvaluation(AssessmentEntry entry, CourseNode cNode, AssessmentConfig assessmentConfig) {
			AssessmentEvaluation se;
			if(assessmentConfig.hasScore() || assessmentConfig.hasPassed()) {
				ScoreCalculator scoreCalculator = courseAssessmentService.getScoreCalculator(cNode);
				String scoreExpressionStr = scoreCalculator.getScoreExpression();
				String passedExpressionStr = scoreCalculator.getPassedExpression();

				Float score = null;
				Boolean passed = null;
				Boolean userVisibility = entry == null ? null : entry.getUserVisibility();
				Long assessmendId = entry == null ? null : entry.getAssessmentId();
				int numOfAssessmentDocs = entry == null ? -1 : entry.getNumberOfAssessmentDocuments();
				Date lastModified = entry == null ? null : entry.getLastModified();
				Double currentRunCompletion = entry == null ? null : entry.getCurrentRunCompletion();
				AssessmentRunStatus runStatus = entry == null ? null : entry.getCurrentRunStatus();
				AssessmentObligation obligation = entry == null ? null : entry.getObligation();
				Integer duration = entry == null ? null : entry.getDuration();
				
				AssessmentEntryStatus assessmentStatus = AssessmentEntryStatus.inProgress;
				ConditionInterpreter ci = userCourseEnvironment.getConditionInterpreter();
				if (assessmentConfig.hasScore() && scoreExpressionStr != null) {
					score = Float.valueOf(ci.evaluateCalculation(scoreExpressionStr));
				}
				if (assessmentConfig.hasPassed() && passedExpressionStr != null) {
					boolean hasPassed = ci.evaluateCondition(passedExpressionStr);
					if(hasPassed) {
						passed = Boolean.TRUE;
						assessmentStatus = AssessmentEntryStatus.done;
					} else {
						//some rules to set -> failed
						FailedEvaluationType failedType = scoreCalculator.getFailedType();
						if(failedType == null || failedType == FailedEvaluationType.failedAsNotPassed) {
							passed = Boolean.FALSE;
						} else if(failedType == FailedEvaluationType.failedAsNotPassedAfterEndDate) {
							RepositoryEntryLifecycle lifecycle = getRepositoryEntryLifecycle();
							if(lifecycle != null && lifecycle.getValidTo() != null && lifecycle.getValidTo().compareTo(new Date()) < 0) {
								passed = Boolean.FALSE;
							}
						} else if(failedType == FailedEvaluationType.manual) {
							passed = entry == null ? null : entry.getPassed();
						}
					}
				}
				
				LastModifications lastModifications = new LastModifications();
				updateLastModified(cNode, lastModifications);
				Date assessmentDone = aetAssessmentDone(entry, assessmentStatus);
				se = new AssessmentEvaluation(score, passed, null, null, assessmentStatus, userVisibility,
						null, currentRunCompletion, runStatus, assessmendId, null, null, numOfAssessmentDocs,
						lastModified, lastModifications.getLastUserModified(),
						lastModifications.getLastCoachModified(), assessmentDone, obligation, duration);
				
				if(entry == null) {
					Identity assessedIdentity = userCourseEnvironment.getIdentityEnvironment().getIdentity();
					userCourseEnvironment.getCourseEnvironment().getAssessmentManager()
						.createAssessmentEntry(cNode, assessedIdentity, se);
					changes = true;
				} else if(!same(se, entry)) {
					if(score != null) {
						entry.setScore(new BigDecimal(score));
					} else {
						entry.setScore(null);
					}
					entry.setPassed(passed);
					if(lastModifications.getLastCoachModified() != null
							&& (entry.getLastCoachModified() == null || (entry.getLastCoachModified() != null && entry.getLastCoachModified().before(lastModifications.getLastCoachModified())))) {
						entry.setLastCoachModified(lastModifications.getLastCoachModified());
					}
					if(lastModifications.getLastUserModified() != null
							&& (entry.getLastUserModified() == null || (entry.getLastUserModified() != null && entry.getLastUserModified().before(lastModifications.getLastUserModified())))) {
						entry.setLastUserModified(lastModifications.getLastUserModified());
					}	
					entry = userCourseEnvironment.getCourseEnvironment().getAssessmentManager().updateAssessmentEntry(entry);
					identToEntries.put(cNode.getIdent(), entry);
					changes = true;
				}
			} else {
				//only update the last modifications dates
				LastModifications lastModifications = new LastModifications();
				updateLastModified(cNode, lastModifications);
				if(entry == null) {
					if(lastModifications.getLastCoachModified() != null || lastModifications.getLastUserModified() != null) {
						se = new AssessmentEvaluation(new Date(), lastModifications.getLastUserModified(), lastModifications.getLastCoachModified());
						Identity assessedIdentity = userCourseEnvironment.getIdentityEnvironment().getIdentity();
						userCourseEnvironment.getCourseEnvironment().getAssessmentManager()
							.createAssessmentEntry(cNode, assessedIdentity, se);
						changes = true;
					}
				} else {
					boolean updated = false;
					if(lastModifications.getLastCoachModified() != null
							&& (entry.getLastCoachModified() == null || (entry.getLastCoachModified() != null && entry.getLastCoachModified().before(lastModifications.getLastCoachModified())))) {
						entry.setLastCoachModified(lastModifications.getLastCoachModified());
						updated = true;
					}
					if(lastModifications.getLastUserModified() != null
							&& (entry.getLastUserModified() == null || (entry.getLastUserModified() != null && entry.getLastUserModified().before(lastModifications.getLastUserModified())))) {
						entry.setLastUserModified(lastModifications.getLastUserModified());
						updated = true;
					}
					if(updated) {
						entry = userCourseEnvironment.getCourseEnvironment().getAssessmentManager().updateAssessmentEntry(entry);
						identToEntries.put(cNode.getIdent(), entry);
						changes = true;
					}
				}
				
				se = AssessmentEvaluation.EMPTY_EVAL;
			}
			return se;
		}
		
		private Date aetAssessmentDone(AssessmentEntry entry, AssessmentEntryStatus assessmentStatus) {
			Date assessmentDone = null;
			if (AssessmentEntryStatus.done.equals(assessmentStatus)) {
				if (entry != null && entry.getAssessmentDone() != null) {
					assessmentDone = entry.getAssessmentDone();
				} else {
					assessmentDone = new Date();
				}
			}
			return assessmentDone;
		}

		private RepositoryEntryLifecycle getRepositoryEntryLifecycle() {
			CourseGroupManager cgm = userCourseEnvironment.getCourseEnvironment().getCourseGroupManager();
			try {
				RepositoryEntryLifecycle lifecycle = cgm.getCourseEntry().getLifecycle();
				if(lifecycle != null) {
					lifecycle.getValidTo();//
				}
				return lifecycle;
			} catch (LazyInitializationException e) {
				//OO-2667: only seen in 1 instance but as it's a critical place, secure the system
				RepositoryEntry reloadedEntry = CoreSpringFactory.getImpl(RepositoryService.class)
						.loadByKey(cgm.getCourseEntry().getKey());
				userCourseEnvironment.getCourseEnvironment().updateCourseEntry(reloadedEntry);
				return reloadedEntry.getLifecycle();
			}
		}
		
		private boolean same(AssessmentEvaluation se, AssessmentEntry entry) {
			boolean same = true;
			
			if((se.getPassed() == null && entry.getPassed() != null)
					|| (se.getPassed() != null && entry.getPassed() == null)
					|| (se.getPassed() != null && !se.getPassed().equals(entry.getPassed()))) {
				same &= false;
			}
			
			if((se.getScore() == null && entry.getScore() != null)
					|| (se.getScore() != null && entry.getScore() == null)
					|| (se.getScore() != null && entry.getScore() != null
							&& Math.abs(se.getScore().floatValue() - entry.getScore().floatValue()) > 0.00001)) {
				same &= false;
			}
			
			if((entry.getLastUserModified() == null && se.getLastUserModified() != null)
					|| (se.getLastUserModified() != null && entry.getLastUserModified() != null
							&& se.getLastUserModified().after(entry.getLastUserModified()))) {
				same &= false;
			}
			
			if((entry.getLastCoachModified() == null && se.getLastCoachModified() != null)
					|| (se.getLastCoachModified() != null && entry.getLastCoachModified() != null
							&& se.getLastCoachModified().after(entry.getLastCoachModified()))) {
				same &= false;
			}
			
			return same;
		}
	}

	/**
	 * Get the score evaluation for a given course node without using the cache.
	 * @param courseNode
	 * @return The score evaluation
	 */
	@Override
	public AssessmentEvaluation getScoreEvaluation(CourseNode courseNode) {
		return courseAssessmentService.getAssessmentEvaluation(courseNode, userCourseEnvironment);
	}

	/**
	 * Evaluates the course node or simply returns the evaluation from the cache.
	 * @param cn
	 * @return ScoreEvaluation
	 */
	@Override
	public AssessmentEvaluation evalCourseNode(CourseNode cn) {
		AssessmentEvaluation se = cachedScoreEvals.get(cn);
		if (se == null) { // result of this node has not been calculated yet, do it
			se = getScoreEvaluation(cn);
			cachedScoreEvals.put(cn, se);
		}
		return se;
	}
	
	private static class LastModifications {
		
		private Date lastUserModified;
		private Date lastCoachModified;
		
		public Date getLastUserModified() {
			return lastUserModified;
		}
		
		public void addLastUserModified(Date date) {
			if(date != null && (lastUserModified == null || lastUserModified.before(date))) {
				lastUserModified = date;
			}
		}
		
		public Date getLastCoachModified() {
			return lastCoachModified;
		}
		
		public void addLastCoachModified(Date date) {
			if(date != null && (lastCoachModified == null || lastCoachModified.before(date))) {
				lastCoachModified = date;
			}
		}
	}
}
