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
package org.olat.modules.gotomeeting.manager;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.olat.core.commons.persistence.DB;
import org.olat.core.util.CodeHelper;
import org.olat.modules.gotomeeting.GoToMeeting;
import org.olat.modules.gotomeeting.GoToOrganizer;
import org.olat.modules.gotomeeting.model.GoToType;
import org.olat.repository.RepositoryEntry;
import org.olat.test.JunitTestHelper;
import org.olat.test.OlatTestCase;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 
 * Initial date: 22.03.2016<br>
 * @author srosse, stephane.rosse@frentix.com, http://www.frentix.com
 *
 */
public class GoToMeetingDAOTest extends OlatTestCase {
	
	@Autowired
	private DB dbInstance;
	@Autowired
	private GoToMeetingDAO meetingDao;
	@Autowired
	private GoToOrganizerDAO organizerDao;
	
	@Test
	public void createMeeting_without() {
		String username = UUID.randomUUID().toString();
		String accessToken = UUID.randomUUID().toString();
		String organizerKey = UUID.randomUUID().toString();
		
		GoToOrganizer organizer = organizerDao
				.createOrganizer("My account", username, accessToken, organizerKey, "Lucas", "de Leyde", null, null, 10l, null);
		Assert.assertNotNull(organizer);
		
		Date start = new Date();
		Date end = new Date();
		String trainingKey = Long.toString(CodeHelper.getForeverUniqueID());

		GoToMeeting training = meetingDao.createTraining("New training", null, "Very interessant", trainingKey, start, end, organizer, null, null, null);
		dbInstance.commit();
		Assert.assertNotNull(training);
		Assert.assertNotNull(training.getKey());
		Assert.assertNotNull(training.getCreationDate());
		Assert.assertNotNull(training.getLastModified());
		Assert.assertEquals("New training", training.getName());
		Assert.assertEquals("Very interessant", training.getDescription());
		Assert.assertNotNull(training.getStartDate());
		Assert.assertNotNull(training.getEndDate());
	}
	
	@Test
	public void getMeetings_withRepositoryEntry() {
		RepositoryEntry entry = JunitTestHelper.createAndPersistRepositoryEntry();
		
		String username = UUID.randomUUID().toString();
		String accessToken = UUID.randomUUID().toString();
		String organizerKey = UUID.randomUUID().toString();
		
		GoToOrganizer organizer = organizerDao
				.createOrganizer(null, username, accessToken, organizerKey, "Michael", "Wolgemut", null, null, 10l, null);
		Assert.assertNotNull(organizer);

		Date start = new Date();
		Date end = new Date();
		String trainingKey = Long.toString(CodeHelper.getForeverUniqueID());
		GoToMeeting training = meetingDao.createTraining("New training", null, "Very interessant", trainingKey, start, end,
				organizer, entry, "d9912", null);
		dbInstance.commit();
		Assert.assertNotNull(training);
		
		List<GoToMeeting> meetings = meetingDao.getMeetings(GoToType.training, entry, "d9912", null);
		Assert.assertNotNull(meetings);
		Assert.assertEquals(1, meetings.size());
		Assert.assertTrue(meetings.contains(training));
	}
}
