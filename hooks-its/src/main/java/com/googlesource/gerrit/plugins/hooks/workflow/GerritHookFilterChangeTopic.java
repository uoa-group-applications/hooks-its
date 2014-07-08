package com.googlesource.gerrit.plugins.hooks.workflow;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.events.PatchSetCreatedEvent;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.hooks.its.ItsFacade;
import com.googlesource.gerrit.plugins.hooks.util.IssueExtractor;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;

/**
 * This one is set on by default and will set the topic from the ticket. It overwrites it
 * every time in case the ticket that is being solved changes. The downside of this is that
 * the topic may have been set by the initial push. The extra logic would be to check if the patchset
 * is 1 and the topic is already set, but it wouldn't catch the subsequent ones.
 *
 * As this problem isn't a problem for UoA, I'm not worrying about it.
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
public class GerritHookFilterChangeTopic extends GerritHookFilter {
	private static final Logger log = LoggerFactory
		.getLogger(GerritHookFilterChangeTopic.class);

	@Inject
	private ItsFacade its;

	@Inject
	@GerritServerConfig
	private Config gerritConfig;

	@Inject
	private IssueExtractor issueExtractor;


	@Inject
	private ReviewDb db;


	@Override
	public void doFilter(PatchSetCreatedEvent patchsetCreated) throws IOException, OrmException {
		boolean setTopicFromTicket =
			gerritConfig.getBoolean(its.name(), null, "setTopicFromTicket",
				true);

		if (setTopicFromTicket) {

			String gitComment =
				getComment(patchsetCreated.change.project,
					patchsetCreated.patchSet.revision);

			String[] issues = issueExtractor.getIssueIds(gitComment);

			if (issues != null && issues.length == 1) {
				log.info("Setting topic from issue {}", issues[0]);

				Change.Id id = Change.Id.parse(patchsetCreated.change.number);
				Change change = db.changes().get(id);
				change.setTopic(issues[0]);
				try {
					db.changes().update(Collections.singleton(change));
				} catch (OrmException oex) {
					log.error("Failed to update topic to {} due to orm exception", issues[0], oex);
				}
			} else {
				log.info("No issues or too many.");
			}
		}
	}
}
