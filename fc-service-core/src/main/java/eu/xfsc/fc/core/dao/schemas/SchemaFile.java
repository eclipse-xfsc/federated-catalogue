package eu.xfsc.fc.core.dao.schemas;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "schemafiles")
@Audited
@EntityListeners(AuditingEntityListener.class)
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

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "modified_at", nullable = false)
  private Instant modifiedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", length = 20, nullable = false)
  private SchemaType type;

  @Column(name = "content", columnDefinition = "TEXT", nullable = false)
  private String content;

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private String createdBy;

  @LastModifiedBy
  @Column(name = "modified_by")
  private String modifiedBy;

  @OneToMany(mappedBy = "schemaFile", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<SchemaTerm> terms = new HashSet<>();
}
