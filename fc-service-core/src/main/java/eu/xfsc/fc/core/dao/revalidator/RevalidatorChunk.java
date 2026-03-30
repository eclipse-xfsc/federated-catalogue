package eu.xfsc.fc.core.dao.revalidator;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "revalidatorchunks")
@Getter
@Setter
@NoArgsConstructor
public class RevalidatorChunk {

  @Id
  @Column(name = "chunkid", nullable = false)
  private Integer chunkId;

  @Column(name = "lastcheck", nullable = false)
  private Instant lastcheck;
}
