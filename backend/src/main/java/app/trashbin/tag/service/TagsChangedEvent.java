package app.trashbin.tag.service;

import java.util.List;
import java.util.UUID;

/**
 * Published inside the transaction when a tag was renamed, merged or deleted. Listeners (the search
 * index) must update the listed files before the transaction commits.
 */
public record TagsChangedEvent(UUID ownerId, List<UUID> fileIds) {}
