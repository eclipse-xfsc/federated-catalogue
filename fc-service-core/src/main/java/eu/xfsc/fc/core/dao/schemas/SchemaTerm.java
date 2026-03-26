package eu.xfsc.fc.core.dao.schemas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "schematerms")
@Getter
@Setter
@NoArgsConstructor
public class SchemaTerm {

  @Id
  @Column(name = "term", length = 256, nullable = false)
  private String term;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "schemaid", nullable = false)
  private SchemaFile schemaFile;
}
