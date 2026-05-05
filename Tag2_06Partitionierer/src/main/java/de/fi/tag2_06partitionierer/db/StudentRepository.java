package de.fi.tag2_06partitionierer.db;

import de.fi.tag2_06partitionierer.db.entity.StudentEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

// CrudRepository reicht hier aus, weil der RepositoryItemWriter ausschließlich save() aufruft.
// Ein JpaRepository wäre unnötig: die flush()-/Paging-Methoden werden im Batch-Kontext
// von Spring Batch selbst gesteuert und nicht vom Repository benötigt.
public interface StudentRepository extends CrudRepository<StudentEntity, UUID> {
}
