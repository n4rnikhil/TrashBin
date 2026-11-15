package app.trashbin.tag.service;

import app.trashbin.common.error.ErrorCode;
import app.trashbin.common.error.InvalidRequestException;
import app.trashbin.common.error.ResourceNotFoundException;
import app.trashbin.common.error.TrashBinException;
import app.trashbin.common.security.AuthenticatedUser;
import app.trashbin.tag.domain.Tag;
import app.trashbin.tag.domain.TagName;
import app.trashbin.tag.dto.TagDtos.TagRef;
import app.trashbin.tag.dto.TagDtos.TagSummary;
import app.trashbin.tag.repository.FileTagDao;
import app.trashbin.tag.repository.TagRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tags and their links to files. Every operation is scoped to one owner. */
@Service
@Transactional
public class TagService {

    public static final int MAX_TAGS_PER_FILE = 20;

    /** A tag with this name already exists for the owner. */
    public static class TagAlreadyExistsException extends TrashBinException {
        private static final long serialVersionUID = 1L;

        public TagAlreadyExistsException() {
            super(ErrorCode.TAG_ALREADY_EXISTS, "You already have a tag with this name.");
        }
    }

    private final TagRepository tags;
    private final FileTagDao links;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TagService(
            TagRepository tags, FileTagDao links, ApplicationEventPublisher events, Clock clock) {
        this.tags = tags;
        this.links = links;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TagSummary> list(AuthenticatedUser caller) {
        Map<UUID, Long> counts = links.fileCounts(caller.id());
        return tags.findAllByOwnerIdOrderByNameNormalizedAsc(caller.id()).stream()
                .map(t -> summary(t, counts.getOrDefault(t.getId(), 0L)))
                .toList();
    }

    public TagSummary create(AuthenticatedUser caller, String name, String color) {
        TagName tagName = parseName(name);
        if (tags.existsByOwnerIdAndNameNormalized(caller.id(), tagName.normalized())) {
            throw new TagAlreadyExistsException();
        }
        Tag tag = saveNew(new Tag(caller.id(), tagName, blankToNull(color), Instant.now(clock)));
        return summary(tag, 0);
    }

    public TagSummary update(AuthenticatedUser caller, UUID tagId, String name, String color) {
        Tag tag = require(caller, tagId);
        if (name != null) {
            TagName tagName = parseName(name);
            if (tags.existsByOwnerIdAndNameNormalizedAndIdNot(
                    caller.id(), tagName.normalized(), tagId)) {
                throw new TagAlreadyExistsException();
            }
            tag.rename(tagName);
        }
        if (color != null) {
            tag.recolor(blankToNull(color));
        }
        tags.flush();
        if (name != null) {
            publish(caller.id(), links.fileIdsWithTag(caller.id(), tagId));
        }
        return summary(tag, links.fileCounts(caller.id()).getOrDefault(tagId, 0L));
    }

    public void delete(AuthenticatedUser caller, UUID tagId) {
        Tag tag = require(caller, tagId);
        List<UUID> affected = links.fileIdsWithTag(caller.id(), tagId);
        tags.delete(tag); // file links cascade in the database
        tags.flush();
        publish(caller.id(), affected);
    }

    /** Moves every file from {@code sourceId} to {@code targetId} and deletes the source tag. */
    public TagSummary merge(AuthenticatedUser caller, UUID sourceId, UUID targetId) {
        if (sourceId.equals(targetId)) {
            throw new InvalidRequestException("A tag cannot be merged into itself.");
        }
        Tag source = require(caller, sourceId);
        Tag target = require(caller, targetId);
        List<UUID> affected = links.fileIdsWithTag(caller.id(), sourceId);
        links.copyLinks(caller.id(), sourceId, targetId, Instant.now(clock));
        tags.delete(source);
        tags.flush();
        publish(caller.id(), affected);
        return summary(target, links.fileCounts(caller.id()).getOrDefault(targetId, 0L));
    }

    /**
     * Replaces the tags of one file, creating tags that do not exist yet. The caller (archive
     * module) has already verified that {@code fileId} belongs to {@code ownerId}.
     */
    public List<TagRef> replaceFileTags(UUID ownerId, UUID fileId, Collection<String> names) {
        Map<String, TagName> wanted = new LinkedHashMap<>();
        for (String raw : names) {
            TagName name = parseName(raw);
            wanted.putIfAbsent(name.normalized(), name);
        }
        if (wanted.size() > MAX_TAGS_PER_FILE) {
            throw new InvalidRequestException(
                    "A file can have at most " + MAX_TAGS_PER_FILE + " tags.");
        }

        Instant now = Instant.now(clock);
        Map<String, Tag> existing =
                tags.findAllByOwnerIdAndNameNormalizedIn(ownerId, wanted.keySet()).stream()
                        .collect(Collectors.toMap(Tag::getNameNormalized, t -> t));
        List<Tag> result = new ArrayList<>();
        for (Map.Entry<String, TagName> entry : wanted.entrySet()) {
            Tag tag = existing.get(entry.getKey());
            result.add(tag != null ? tag : saveNew(new Tag(ownerId, entry.getValue(), null, now)));
        }
        links.replaceLinks(ownerId, fileId, result.stream().map(Tag::getId).toList(), now);
        return result.stream()
                .sorted((a, b) -> a.getNameNormalized().compareTo(b.getNameNormalized()))
                .map(TagService::ref)
                .toList();
    }

    /** Tags of each requested file, sorted by name. Files without tags map to an empty list. */
    @Transactional(readOnly = true)
    public Map<UUID, List<TagRef>> tagsForFiles(UUID ownerId, Collection<UUID> fileIds) {
        Map<UUID, List<TagRef>> result = new LinkedHashMap<>();
        fileIds.forEach(id -> result.put(id, new ArrayList<>()));
        for (FileTagDao.Link link : links.linksForFiles(ownerId, fileIds)) {
            result.get(link.fileId()).add(new TagRef(link.tagId(), link.name(), link.color()));
        }
        return result;
    }

    /** Lets dependents (search) react to tag changes that affect many files at once. */
    private void publish(UUID ownerId, List<UUID> fileIds) {
        if (!fileIds.isEmpty()) {
            events.publishEvent(new TagsChangedEvent(ownerId, fileIds));
        }
    }

    private Tag saveNew(Tag tag) {
        try {
            return tags.saveAndFlush(tag);
        } catch (DataIntegrityViolationException e) {
            throw new TagAlreadyExistsException(); // lost a race with an identical name
        }
    }

    private Tag require(AuthenticatedUser caller, UUID tagId) {
        return tags.findByIdAndOwnerId(tagId, caller.id())
                .orElseThrow(ResourceNotFoundException::new);
    }

    private static TagName parseName(String raw) {
        return TagName.parse(raw)
                .orElseThrow(
                        () ->
                                new InvalidRequestException(
                                        "Tag names must be 1 to "
                                                + TagName.MAX_LENGTH
                                                + " characters."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.toUpperCase(java.util.Locale.ROOT);
    }

    private static TagRef ref(Tag tag) {
        return new TagRef(tag.getId(), tag.getName(), tag.getColor());
    }

    private static TagSummary summary(Tag tag, long fileCount) {
        return new TagSummary(tag.getId(), tag.getName(), tag.getColor(), fileCount);
    }
}
