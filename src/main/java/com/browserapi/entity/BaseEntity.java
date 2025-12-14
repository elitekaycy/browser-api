package com.browserapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base entity class for all database entities in the Browser API.
 * Provides common fields for ID, creation timestamp, and update timestamp.
 * <p>
 * All entities should extend this class to inherit:
 * - UUID-based primary keys (globally unique, secure)
 * - Automatic creation timestamp tracking
 * - Automatic update timestamp tracking
 * <p>
 * Usage:
 * <pre>
 * {@code
 * @Entity
 * public class MyEntity extends BaseEntity {
 *     private String myField;
 * }
 * }
 * </pre>
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    /**
     * Unique identifier for this entity.
     * Uses UUID (Universally Unique Identifier) for global uniqueness.
     * Automatically generated before first persist.
     */
    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private UUID id;

    /**
     * Timestamp when this entity was first created.
     * Automatically set on first persist, never updated after.
     */
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when this entity was last updated.
     * Automatically updated on every merge/update operation.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * JPA lifecycle callback - called before entity is persisted for the first time.
     * Generates UUID and sets creation/update timestamps.
     */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * JPA lifecycle callback - called before entity is updated.
     * Updates the modification timestamp.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Equality based on ID.
     * Two entities are equal if they have the same ID.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseEntity that = (BaseEntity) o;
        return id != null && id.equals(that.id);
    }

    /**
     * Hash code based on ID.
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * String representation for debugging.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id=" + id +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
