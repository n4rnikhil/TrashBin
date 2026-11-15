package app.trashbin.collection.service;

import app.trashbin.collection.domain.FileCollection;
import app.trashbin.collection.dto.CollectionDtos.CollectionRef;
import app.trashbin.collection.dto.CollectionDtos.CollectionSummary;
import app.trashbin.collection.repository.CollectionFileDao;
import app.trashbin.collection.repository.FileCollectionRepository;
import app.trashbin.common.error.ErrorCode;
import app.trashbin.common.error.InvalidRequestException;
import app.trashbin.common.error.ResourceNotFoundException;
import app.trashbin.common.error.TrashBinException;
import app.trashbin.common.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Collections and file membership. Every operation is scoped to one owner. */
@Service
@Transactional
public class CollectionService {

    public static final int MAX_COLLECTIONS_PER_FILE = 50;

    public static class CollectionAlreadyExistsException extends TrashBinException {
        private static final long serialVersionUID = 1L;

        public CollectionAlreadyExistsException() {
            super(
                    ErrorCode.COLLECTION_ALREADY_EXISTS,
                    "You already have a collection with this name.");
        }
    }

    private final FileCollectionRepository collections;
    private final CollectionFileDao members;
    private final Clock clock;

    public CollectionService(
            FileCollectionRepository collections, CollectionFileDao members, Clock clock) {
        this.collections = collections;
        this.members = members;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CollectionSummary> list(AuthenticatedUser caller) {
        Map<UUID, Long> counts = members.fileCounts(caller.id());
        return collections.findAllByOwnerIdOrderByNameNormalizedAsc(caller.id()).stream()
                .map(c -> summary(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CollectionSummary get(AuthenticatedUser caller, UUID id) {
        return summary(require(caller, id), members.fileCounts(caller.id()).getOrDefault(id, 0L));
    }

    public CollectionSummary create(AuthenticatedUser caller, String name, String description) {
        String clean = cleanName(name);
        if (collections.existsByOwnerIdAndNameNormalized(
                caller.id(), FileCollection.normalize(clean))) {
            throw new CollectionAlreadyExistsException();
        }
        try {
            FileCollection created =
                    collections.saveAndFlush(
                            new FileCollection(
                                    caller.id(),
                                    clean,
                                    blankToNull(description),
                                    Instant.now(clock)));
            return summary(created, 0);
        } catch (DataIntegrityViolationException e) {
            throw new CollectionAlreadyExistsException(); // lost a race with the same name
        }
    }

    public CollectionSummary update(
            AuthenticatedUser caller, UUID id, String name, String description) {
        FileCollection collection = require(caller, id);
        Instant now = Instant.now(clock);
        if (name != null) {
            String clean = cleanName(name);
            if (collections.existsByOwnerIdAndNameNormalizedAndIdNot(
                    caller.id(), FileCollection.normalize(clean), id)) {
                throw new CollectionAlreadyExistsException();
            }
            collection.rename(clean, now);
        }
        if (description != null) {
            collection.describe(blankToNull(description), now);
        }
        collections.flush();
        return summary(collection, members.fileCounts(caller.id()).getOrDefault(id, 0L));
    }

    /** Deletes the collection only; its files stay in the archive. */
    public void delete(AuthenticatedUser caller, UUID id) {
        collections.delete(require(caller, id));
    }

    /**
     * Sets which collections a file belongs to. The caller (archive module) has verified that
     * {@code fileId} belongs to {@code ownerId}; unknown or foreign collection ids are "not found".
     */
    public List<CollectionRef> replaceCollectionsOfFile(
            UUID ownerId, UUID fileId, Collection<UUID> collectionIds) {
        Set<UUID> wanted = new LinkedHashSet<>(collectionIds);
        if (wanted.size() > MAX_COLLECTIONS_PER_FILE) {
            throw new InvalidRequestException(
                    "A file can be in at most " + MAX_COLLECTIONS_PER_FILE + " collections.");
        }
        if (!wanted.isEmpty()
                && collections.findAllByOwnerIdAndIdIn(ownerId, wanted).size() != wanted.size()) {
            throw new ResourceNotFoundException();
        }
        members.replaceCollectionsOfFile(ownerId, fileId, wanted, Instant.now(clock));
        return collectionsOfFile(ownerId, fileId);
    }

    @Transactional(readOnly = true)
    public List<CollectionRef> collectionsOfFile(UUID ownerId, UUID fileId) {
        return members.collectionsOfFile(ownerId, fileId).stream()
                .map(m -> new CollectionRef(m.collectionId(), m.name()))
                .toList();
    }

    private FileCollection require(AuthenticatedUser caller, UUID id) {
        return collections
                .findByIdAndOwnerId(id, caller.id())
                .orElseThrow(ResourceNotFoundException::new);
    }

    private static String cleanName(String raw) {
        String clean =
                raw == null
                        ? ""
                        : raw.replaceAll("\\p{Cntrl}", " ").strip().replaceAll("\\s+", " ");
        if (clean.isEmpty() || clean.length() > FileCollection.MAX_NAME_LENGTH) {
            throw new InvalidRequestException(
                    "Collection names must be 1 to "
                            + FileCollection.MAX_NAME_LENGTH
                            + " characters.");
        }
        return clean;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static CollectionSummary summary(FileCollection c, long fileCount) {
        return new CollectionSummary(
                c.getId(), c.getName(), c.getDescription(), fileCount, c.getUpdatedAt());
    }
}
