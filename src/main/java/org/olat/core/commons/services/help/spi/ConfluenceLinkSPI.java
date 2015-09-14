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
package org.olat.core.commons.services.help.spi;

import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.poi.util.IOUtils;
import org.olat.admin.user.tools.UserTool;
import org.olat.core.commons.services.help.HelpLinkSPI;
import org.olat.core.gui.UserRequest;
import org.olat.core.gui.components.Component;
import org.olat.core.gui.components.link.ExternalLink;
import org.olat.core.gui.components.velocity.VelocityContainer;
import org.olat.core.gui.control.WindowControl;
import org.olat.core.helpers.Settings;
import org.olat.core.logging.OLog;
import org.olat.core.logging.Tracing;
import org.olat.core.util.StringHelper;
import org.olat.core.util.httpclient.HttpClientFactory;
import org.springframework.stereotype.Service;

/**
 * 
 * Build a link to openolat confluence. It has the following form:<br/>
 * https://confluence.openolat.org/display/OO100DE/OpenOLAT+10+Benutzerhandbuch
 * 
 * Initial date: 07.01.2015<br>
 * 
 * @author srosse, stephane.rosse@frentix.com, http://www.frentix.com
 *
 */
@Service("ooConfluenceLinkHelp")
public class ConfluenceLinkSPI implements HelpLinkSPI {
	private static final OLog logger = Tracing.createLoggerFor(ConfluenceLinkSPI.class);

	private static final Map<String, String> spaces = new ConcurrentHashMap<String, String>();
	private static final Map<String, String> translatedPages = new ConcurrentHashMap<String, String>();
	private static final Map<String, Date> translatTrials = new ConcurrentHashMap<String, Date>();

	public static final Locale EN_Locale = new Locale("en");
	public static final Locale DE_Locale = new Locale("de");

	@Override
	public UserTool getHelpUserTool(WindowControl wControl) {
		return new ConfluenceUserTool();
	}

	@Override
	public String getURL(Locale locale, String page) {
		StringBuilder sb = new StringBuilder(64);
		sb.append("https://confluence.openolat.org/display");
		String space = spaces.get(locale.toString());
		if (space == null) {
			// Generate space only once per language, version does not change at
			// runtime
			String version = Settings.getVersion();
			space = generateSpace(version, locale);
			spaces.putIfAbsent(locale.toString(), space);
		}
		sb.append(space);
		if (page != null) {
			int anchorPos = page.indexOf("#");
			if (anchorPos != -1) {
				// Page with anchor: real page name + anchor
				String realPage = page.substring(0, anchorPos);
				String anchor = page.substring(anchorPos + 1);

				// Special case for non-en spaces: CustomWare Redirection Plugin
				// can not redirect pages with anchors. We need to fix it here
				// by fetching the page and lookup the redirect path. Ugly, but
				// we see no other option here.
				if (!locale.getLanguage().equals(EN_Locale.getLanguage())) {
					String redirectedPage = getPageFromAlias(getURL(locale, realPage));
					if (redirectedPage != null) {
						realPage = redirectedPage;
					}
					// else realPage part stays the same - anchor won't work but
					// at least the right page is loading
				}

				// Confluence has some super-fancy way to addressing pages with
				// anchors
				sb.append(realPage.replaceAll(" ", "%20"));
				sb.append("#").append(realPage.replaceAll(" ", "")).append("-").append(anchor);

			} else {
				// Page without anchor
				sb.append(page.replaceAll(" ", "%20"));
			}
		}
		return sb.toString();
	}

	/**
	 * Convert 10.0 -> 100<br/>
	 * Convert 10.1.1 -> 101
	 * 
	 * 
	 * @param version
	 * @param locale
	 * @return
	 */
	protected String generateSpace(String version, Locale locale) {
		StringBuilder sb = new StringBuilder();
		sb.append("/OO");

		int firstPointIndex = version.indexOf('.');
		if (firstPointIndex > 0) {
			sb.append(version.substring(0, firstPointIndex));
			int secondPointIndex = version.indexOf('.', firstPointIndex + 1);
			if (secondPointIndex > firstPointIndex) {
				sb.append(version.substring(firstPointIndex + 1, secondPointIndex));
			} else if (firstPointIndex + 1 < version.length()) {
				String subVersion = version.substring(firstPointIndex + 1);
				char[] subVersionArr = subVersion.toCharArray();
				for (int i = 0; i < subVersionArr.length && Character.isDigit(subVersionArr[i]); i++) {
					sb.append(subVersionArr[i]);
				}
			} else {
				sb.append("0");
			}
		} else {
			char[] versionArr = version.toCharArray();
			for (int i = 0; i < versionArr.length && Character.isDigit(versionArr[i]); i++) {
				sb.append(versionArr[i]);
			}
			// add minor version
			sb.append("0");
		}

		if (locale.getLanguage().equals(DE_Locale.getLanguage())) {
			sb.append("DE/");
		} else {
			sb.append("EN/");
		}

		return sb.toString();
	}

	public class ConfluenceUserTool implements UserTool {

		@Override
		public Component getMenuComponent(UserRequest ureq, VelocityContainer container) {
			ExternalLink helpLink = new ExternalLink("topnav.help");
			container.put("topnav.help", helpLink);
			helpLink.setIconLeftCSS("o_icon o_icon_help o_icon-lg");
			helpLink.setName(container.getTranslator().translate("topnav.help"));
			helpLink.setTooltip(container.getTranslator().translate("topnav.help.alt"));
			helpLink.setTarget("oohelp");
			helpLink.setUrl(getURL(ureq.getLocale(), null));
			return helpLink;
		}

		@Override
		public void dispose() {
			//
		}
	}

	@Override
	public Component getHelpPageLink(UserRequest ureq, String title, String tooltip, String iconCSS, String elementCSS,
			String page) {
		ExternalLink helpLink = new ExternalLink("topnav.help." + page);
		helpLink.setName(title);
		helpLink.setTooltip(tooltip);
		helpLink.setIconLeftCSS(iconCSS);
		helpLink.setElementCssClass(elementCSS);
		helpLink.setTarget("oohelp");
		helpLink.setUrl(getURL(ureq.getLocale(), page));
		return helpLink;
	}

	/**
	 * Fetch the redirected page name for the given URL. Note that this is
	 * executed asynchronously, meaning that the first time this method is
	 * executed for a certain URL it will return null. As soon as the code could
	 * get the redirection from the confluence server it will return the
	 * redirected page name instead.
	 * 
	 * @param aliasUrl
	 * @return The translated page name or NULL if not found
	 */
	private String getPageFromAlias(String aliasUrl) {
		if (StringHelper.containsNonWhitespace(aliasUrl)) {
			String translatedPage = translatedPages.get(aliasUrl);
			if (translatedPage != null) {
				return translatedPage;
			}
			// Not in cache. Start a background thread to fetch the translated
			// page from the confluence. Since this can take several seconds, we
			// exit here with null. Next time the page is loaded the translated
			// page will be in the cache. 
			
			// Do this only once per 30 mins per page. Confluence might be down
			// or another user already trigger the fetch.
			Date lastTrial = translatTrials.get(aliasUrl);
			Date now = new Date();
			if (lastTrial == null || lastTrial.getTime() < (now.getTime() - (1800 * 1000))) {
				translatTrials.put(aliasUrl, now);				
				new Thread() {
					public void run() {
						CloseableHttpClient httpClient = HttpClientFactory.getHttpClientInstance(false);
						try {
							HttpGet httpMethod = new HttpGet(aliasUrl);
							httpMethod.setHeader("User-Agent", Settings.getFullVersionInfo());
							HttpResponse response = httpClient.execute(httpMethod);
							int httpStatusCode = response.getStatusLine().getStatusCode();
							// Looking at the HTTP status code tells us whether a
							// user with the given MSN name exists.
							if (httpStatusCode == HttpStatus.SC_OK) {
								String body = EntityUtils.toString(response.getEntity());
								// Page contains a javascript redirect call, extract
								// redirect location
								int locationPos = body.indexOf("location.replace('");
								if (locationPos == -1) {
									return;
								}
								int endPos = body.indexOf("'", locationPos + 18);
								if (endPos == -1) {
									return;
								}
								// Remove the path to extract the page name
								String path = body.substring(locationPos + 18, endPos);
								String translatedPage = path.substring(path.lastIndexOf("/") + 1);
								translatedPage = translatedPage.replaceAll("\\+", " ");
								// We're done. Put to cache for next retrieval
								translatedPages.putIfAbsent(aliasUrl, translatedPage);
							}
						} catch (Exception e) {
							logger.warn("Error while getting help page from EN alias", e);
						} finally {
							IOUtils.closeQuietly(httpClient);
						}
					}
				}.start();
			}
			return null;

		}
		return null;
	}
}
