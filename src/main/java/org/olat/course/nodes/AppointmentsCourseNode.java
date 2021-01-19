/**
 * <a href="http://www.openolat.org">
 * OpenOLAT - Online Learning and Training</a><br>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); <br>
 * you may not use this file except in compliance with the License.<br>
 * You may obtain a copy of the License at the
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache homepage</a>
 * <p>
 * Unless required by applicable law or agreed to in writing,<br>
 * software distributed under the License is distributed on an "AS IS" BASIS, <br>
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. <br>
 * See the License for the specific language governing permissions and <br>
 * limitations under the License.
 * <p>
 * Initial code contributed and copyrighted by<br>
 * frentix GmbH, http://www.frentix.com
 * <p>
 */
package org.olat.course.nodes;

import java.util.List;

import org.olat.core.CoreSpringFactory;
import org.olat.core.gui.UserRequest;
import org.olat.core.gui.components.stack.BreadcrumbPanel;
import org.olat.core.gui.control.Controller;
import org.olat.core.gui.control.WindowControl;
import org.olat.core.gui.control.generic.messages.MessageUIFactory;
import org.olat.core.gui.control.generic.tabbable.TabbableController;
import org.olat.core.gui.translator.Translator;
import org.olat.core.id.Roles;
import org.olat.core.util.Util;
import org.olat.core.util.nodes.INode;
import org.olat.course.ICourse;
import org.olat.course.editor.ConditionAccessEditConfig;
import org.olat.course.editor.CourseEditorEnv;
import org.olat.course.editor.NodeEditController;
import org.olat.course.editor.StatusDescription;
import org.olat.course.nodes.appointments.AppointmentsSecurityCallbackFactory;
import org.olat.course.nodes.appointments.ui.AppointmentsEditController;
import org.olat.course.nodes.appointments.ui.AppointmentsPeekViewController;
import org.olat.course.nodes.appointments.ui.AppointmentsRunController;
import org.olat.course.run.navigation.NodeRunConstructionResult;
import org.olat.course.run.userview.CourseNodeSecurityCallback;
import org.olat.course.run.userview.UserCourseEnvironment;
import org.olat.modules.ModuleConfiguration;
import org.olat.modules.appointments.AppointmentsSecurityCallback;
import org.olat.modules.appointments.AppointmentsService;
import org.olat.repository.RepositoryEntry;

/**
 * 
 * Initial date: 13 Apr 2020<br>
 * @author uhensler, urs.hensler@frentix.com, http://www.frentix.com
 *
 */
public class AppointmentsCourseNode extends AbstractAccessableCourseNode {
	
	private static final long serialVersionUID = 2684639881298198543L;
	
	@SuppressWarnings("deprecation")
	private static final String TRANSLATOR_PACKAGE = Util.getPackageName(AppointmentsEditController.class);

	public static final String TYPE = "appointments";
	public static final String ICON_CSS = "o_appointment_icon";
	
	// configuration
	private static final int CURRENT_VERSION = 1;
	public static final String CONFIG_COACH_EDIT_TOPIC = "coach.edit.topic";
	public static final String CONFIG_COACH_EDIT_APPOINTMENT = "coach.edit.appointment";

	
	public AppointmentsCourseNode() {
		this(null);
	}
	
	public AppointmentsCourseNode(CourseNode parent) {
		super(TYPE, parent);
	}

	@Override
	public TabbableController createEditController(UserRequest ureq, WindowControl wControl, BreadcrumbPanel stackPanel,
			ICourse course, UserCourseEnvironment userCourseEnv) {
		CourseNode chosenNode = course.getEditorTreeModel().getCourseNode(userCourseEnv.getCourseEditorEnv().getCurrentCourseNodeId());
		AppointmentsEditController childTabCtrl = new AppointmentsEditController(ureq, wControl, this);
		NodeEditController nodeEditCtr = new NodeEditController(ureq, wControl, course, chosenNode,
				userCourseEnv, childTabCtrl);
		nodeEditCtr.addControllerListener(childTabCtrl);
		return nodeEditCtr;
	}

	@Override
	public ConditionAccessEditConfig getAccessEditConfig() {
		return ConditionAccessEditConfig.regular(false);
	}

	@Override
	public NodeRunConstructionResult createNodeRunConstructionResult(UserRequest ureq, WindowControl wControl,
			UserCourseEnvironment userCourseEnv, CourseNodeSecurityCallback nodeSecCallback, String nodecmd) {
		Controller controller;
		Roles roles = ureq.getUserSession().getRoles();
		if (roles.isGuestOnly()) {
			Translator trans = Util.createPackageTranslator(AppointmentsCourseNode.class, ureq.getLocale());
			String title = trans.translate("guestnoaccess.title");
			String message = trans.translate("guestnoaccess.message");
			controller = MessageUIFactory.createInfoMessage(ureq, wControl, title, message);
		} else {
			AppointmentsSecurityCallback secCallback = AppointmentsSecurityCallbackFactory
					.create(getModuleConfiguration(), userCourseEnv);
			RepositoryEntry entry = userCourseEnv.getCourseEnvironment().getCourseGroupManager().getCourseEntry();
			controller = new AppointmentsRunController(ureq, wControl, entry, getIdent(), secCallback);
		}
		Controller ctrl = TitledWrapperHelper.getWrapper(ureq, wControl, controller, this, ICON_CSS);
		return new NodeRunConstructionResult(ctrl);
	}
	
	@Override
	public Controller createPeekViewRunController(UserRequest ureq, WindowControl wControl, UserCourseEnvironment userCourseEnv,
			CourseNodeSecurityCallback nodeSecCallback) {
		return new AppointmentsPeekViewController(ureq, wControl, userCourseEnv, this);
	}

	@Override
	public StatusDescription isConfigValid() {
		if (oneClickStatusCache != null) { return oneClickStatusCache[0]; }
		
		StatusDescription sd = StatusDescription.NOERROR;
		return sd;
	}

	@Override
	public StatusDescription[] isConfigValid(CourseEditorEnv cev) {
		List<StatusDescription> statusDescs = isConfigValidWithTranslator(cev, TRANSLATOR_PACKAGE,
				getConditionExpressions());
		return StatusDescriptionHelper.sort(statusDescs);
	}
	
	@Override
	public RepositoryEntry getReferencedRepositoryEntry() {
		return null;
	}

	@Override
	public boolean needsReferenceToARepositoryEntry() {
		return false;
	}
	
	@Override
	public void updateModuleConfigDefaults(boolean isNewNode, INode parent) {
		ModuleConfiguration config = getModuleConfiguration();
		
		if (isNewNode) {
			config.setBooleanEntry(CONFIG_COACH_EDIT_TOPIC, true);
			config.setBooleanEntry(CONFIG_COACH_EDIT_APPOINTMENT, true);
		}
		
		config.setConfigurationVersion(CURRENT_VERSION);
	}

	@Override
	public void cleanupOnDelete(ICourse course) {
		RepositoryEntry re = course.getCourseEnvironment().getCourseGroupManager().getCourseEntry();
		CoreSpringFactory.getImpl(AppointmentsService.class).deleteTopics(re, getIdent());
		super.cleanupOnDelete(course);
	}

}
