package eu.xfsc.fc.core.dao.schemas;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "schemafiles")
@Getter
@Setter
@NoArgsConstructor
public class SchemaFile {

  @Id
  @Column(name = "schemaid", length = 200)
  private String schemaId;

  @Column(name = "namehash", length = 64, nullable = false)
  private String nameHash;

  @Column(name = "uploadtime", nullable = false)
  private Instant uploadTime;

  @Column(name = "updatetime", nullable = false)
  private Instant updateTime;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", length = 20, nullable = false)
  private SchemaType type;

  @Column(name = "content", columnDefinition = "TEXT", nullable = false)
  private String content;

  @OneToMany(mappedBy = "schemaFile", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<SchemaTerm> terms = new HashSet<>();
}
