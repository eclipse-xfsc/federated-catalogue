package eu.xfsc.fc.core.dao.validatorcache;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "validatorcache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ValidatorCache {

  @Id
  @Column(name = "diduri", columnDefinition = "TEXT")
  private String didUri;

  @Column(name = "publickey", nullable = false, columnDefinition = "TEXT")
  private String publicKey;

  @Column(name = "expirationtime")
  private Instant expirationTime;
}
