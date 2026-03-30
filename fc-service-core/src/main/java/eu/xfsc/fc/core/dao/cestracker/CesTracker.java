package eu.xfsc.fc.core.dao.cestracker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ces_tracker")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CesTracker {

  @Id
  @Column(name = "ces_id", length = 36, nullable = false)
  private String cesId;

  @Column(name = "event", nullable = false, columnDefinition = "TEXT")
  private String event;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "cred_processed", nullable = false)
  private int credProcessed;

  @Column(name = "cred_id", columnDefinition = "TEXT")
  private String credId;

  @Column(name = "error", columnDefinition = "TEXT")
  private String error;
}
