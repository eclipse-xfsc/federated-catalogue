package eu.xfsc.fc.core.dao.assets;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Asset {

  @Id
  @Column(name = "asset_hash", length = 64, nullable = false)
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

  @Column(name = "content_type", length = 255)
  private String contentType;

  @Column(name = "file_size")
  private Long fileSize;

  @Column(name = "original_filename", length = 500)
  private String originalFilename;
}
