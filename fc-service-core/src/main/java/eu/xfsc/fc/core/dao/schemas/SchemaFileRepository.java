package eu.xfsc.fc.core.dao.schemas;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;

public interface SchemaFileRepository
    extends JpaRepository<SchemaFile, String> {

  @Query("SELECT e.type, e.schemaId FROM SchemaFileEntity e")
  List<Object[]> findAllTypeAndSchemaId();

  @Query("""
    SELECT e.type, e.schemaId
    FROM SchemaFileEntity e
    JOIN e.terms t
    WHERE t.term = :term
  """)
  List<Object[]> findTypeAndSchemaIdByTerm(@Param("term") String term);

  @Query("""
    SELECT e.content
    FROM SchemaFileEntity e
    WHERE e.type = :type
    ORDER BY e.uploadTime DESC LIMIT 1
  """)
  Optional<String> findLatestContentByType(@Param("type") SchemaType type);

  @Query("SELECT t.term FROM SchemaTermEntity t WHERE t.term IN :terms")
  List<String> findExistingTerms(@Param("terms") Collection<String> terms);

  @Modifying
  @Query("DELETE FROM SchemaFileEntity")
  int deleteAllReturningCount();
}
