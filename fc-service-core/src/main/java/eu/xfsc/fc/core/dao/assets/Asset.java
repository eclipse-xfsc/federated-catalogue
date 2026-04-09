package eu.xfsc.fc.core.dao.assets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "assets")
@Audited
@Getter
@Setter
@NoArgsConstructor
public class Asset {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "assets_seq")
  @SequenceGenerator(name = "assets_seq", sequenceName = "assets_id_seq")
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "asset_hash", length = 64, nullable = false, unique = true)
  private String assetHash;

  @Column(name = "subjectid", nullable = false, columnDefinition = "TEXT")
  private String subjectId;

  @Column(name = "issuer", columnDefinition = "TEXT")
  private String issuer;

  @Column(name = "uploadtime", nullable = false)
  private Instant uploadTime;

  @Column(name = "statustime", nullable = false)
  private Instant statusTime;

  @Column(name = "expirationtime")
  private Instant expirationTime;

  @Column(name = "status", nullable = false, columnDefinition = "int2")
  private short status;

  @Column(name = "content", columnDefinition = "TEXT")
  private String content;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "validators", columnDefinition = "varchar(2048)[]")
  private String[] validators;

  @Column(name = "content_type")
  private String contentType;

  @Column(name = "file_size")
  private Long fileSize;

  @Column(name = "original_filename", length = 500)
  private String originalFilename;

  @Column(name = "change_comment", columnDefinition = "TEXT")
  private String changeComment;
}
