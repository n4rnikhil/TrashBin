package app.trashbin.common.persistence;

/**
 * Marker for repositories of entities that belong to a user. ArchitectureTest forbids calling
 * id-only lookups (findById, getReferenceById, existsById, deleteById) on these: every access must
 * be scoped by owner (docs/SECURITY_GUIDELINES.md section 4).
 */
public interface OwnedEntityRepository {}
