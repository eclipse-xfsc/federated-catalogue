package eu.xfsc.fc.core.dao.schemas;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "schemafiles")
@Audited
@Getter
@Setter
@NoArgsConstructor
public class SchemaFile {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "schemafiles_seq")
  @SequenceGenerator(name = "schemafiles_seq", sequenceName = "schemafiles_id_seq")
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "schemaid", length = 200, nullable = false, unique = true)
  private String schemaId;

  @Column(name = "namehash", length = 64, nullable = false)
  private String nameHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "modified_at", nullable = false)
  private Instant modifiedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", length = 20, nullable = false)
  private SchemaType type;

  @Column(name = "content", columnDefinition = "TEXT", nullable = false)
  private String content;

  @OneToMany(mappedBy = "schemaFile", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<SchemaTerm> terms = new HashSet<>();
}
