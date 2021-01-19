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
package org.olat.selenium.page.course;

import org.olat.selenium.page.graphene.OOGraphene;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.Select;

/**
 * 
 * Initial date: 18 mai 2020<br>
 * @author srosse, stephane.rosse@frentix.com, http://www.frentix.com
 *
 */
public class BigBlueButtonPage {

	private final WebDriver browser;
	
	public BigBlueButtonPage(WebDriver browser) {
		this.browser = browser;
	}
	
	/**
	 * Assert on the wrapper of the meetings lists.
	 * 
	 * @return Itself
	 */
	public BigBlueButtonPage assertOnRuntime() {
		By runtimeBy = By.className("o_sel_bbb_overview");
		OOGraphene.waitElement(runtimeBy, browser);
		return this;
	}
	
	/**
	 * The list of meetings to configure.
	 * 
	 * @return Itself
	 */
	public BigBlueButtonPage selectEditMeetingsList() {
		By editListBy = By.cssSelector("a.o_sel_bbb_edit_meetings_segment");
		OOGraphene.waitElement(editListBy, browser);
		browser.findElement(editListBy).click();
		OOGraphene.waitBusy(browser);
		OOGraphene.waitElement(By.className("o_sel_bbb_edit_meetings_list"), browser);
		return this;
	}	
	
	/**
	 * Add a single meeting.
	 * 
	 * @param name The name of the meeting
	 * @param template The name of the template
	 * @return Itself
	 */
	public BigBlueButtonPage addSingleMeeting(String name, String template) {
		openCreateDropDown();
		
		By addSingleMeetingBy = By.cssSelector("a.o_sel_bbb_single_meeting_add");
		OOGraphene.waitElement(addSingleMeetingBy, browser);
		browser.findElement(addSingleMeetingBy).click();
		OOGraphene.waitModalDialog(browser);
		
		By nameBy = By.cssSelector(".o_sel_bbb_edit_meeting_name input[type='text']");
		OOGraphene.waitElement(nameBy, browser);
		browser.findElement(nameBy).sendKeys(name);
		
		By templateBy = By.cssSelector(".o_sel_bbb_edit_meeting select#o_fiomeeting_template_SELBOX");
		new Select(browser.findElement(templateBy)).selectByVisibleText(template);
		OOGraphene.waitBusy(browser);
		
		By saveBy = By.cssSelector("fieldset.o_sel_bbb_edit_meeting button.btn.btn-primary");
		browser.findElement(saveBy).click();
		OOGraphene.waitBusy(browser);
		
		OOGraphene.waitModalDialogDisappears(browser);
		return this;
	}
	
	private BigBlueButtonPage openCreateDropDown() {
		By addMenuCaretBy = By.cssSelector("button.o_sel_bbb_meeting_add");
		OOGraphene.waitElement(addMenuCaretBy, browser);
		browser.findElement(addMenuCaretBy).click();
		
		By addMenuBy = By.cssSelector("ul.o_sel_bbb_meeting_add");
		OOGraphene.waitElement(addMenuBy, browser);
		return this;
	}
	
	/**
	 * The list of meetings for the end users.
	 * 
	 * @return Itself
	 */
	public BigBlueButtonPage selectMeetingsList() {
		By editListBy = By.cssSelector("a.o_sel_bbb_meetings_segment");
		OOGraphene.waitElement(editListBy, browser);
		browser.findElement(editListBy).click();
		OOGraphene.waitBusy(browser);
		return this;
	}
	
	/**
	 * Check if the meeting with the specified name is in the list (edit or run);
	 * @param meetingName The name of the meeting
	 * @return Itself
	 */
	public BigBlueButtonPage assertOnList(String meetingName) {
		By meetingBy = By.xpath("//div[contains(@class,'o_table_flexi')]//table//td[contains(@class,'o_dnd_label')][text()[contains(.,'" + meetingName + "')]]");
		OOGraphene.waitElement(meetingBy, browser);
		return this;
	}
	
	/**
	 * Select a meeting in the run list.
	 * 
	 * @param meetingName The name of the meeting to select
	 * @return Itself
	 */
	public BigBlueButtonPage selectMeeting(String meetingName) {
		By selectBy = By.xpath("//div[contains(@class,'o_table_flexi')]//table//tr[td[text()[contains(.,'" + meetingName + "')]]]/td/a[contains(@onclick,'select')]");
		OOGraphene.waitElement(selectBy, browser);
		browser.findElement(selectBy).click();
		OOGraphene.waitBusy(browser);
		return this;
	}
	
	/**
	 * Check that the meeting with the specified is on the start page.
	 * 
	 * @param meetingName The meeting name
	 * @return Itself
	 */
	public BigBlueButtonPage assertOnMeeting(String meetingName) {
		By titleBy = By.xpath("//div[@class='o_sel_bbb_meeting']//h3[text()[contains(.,'" + meetingName + "')]]");
		OOGraphene.waitElement(titleBy, browser);
		return this;
	}
	
	/**
	 * Check that the join button is visible.
	 * 
	 * @return Itself
	 */
	public BigBlueButtonPage assertOnJoin() {
		By joinBy = By.cssSelector("div.o_sel_bbb_meeting a.btn.o_sel_bbb_join");
		OOGraphene.waitElement(joinBy, browser);
		return this;
	}

}
