package eu.xfsc.fc.core.dao.assets;

import eu.xfsc.fc.core.pojo.AssetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "assets")
@Audited
@EntityListeners(AuditingEntityListener.class)
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

  @Enumerated(EnumType.STRING)
  @Column(name = "asset_type", length = 50)
  private AssetType assetType;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "linked_asset_id")
  private Asset linkedAsset;

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private String createdBy;

  @LastModifiedBy
  @Column(name = "modified_by")
  private String modifiedBy;

  @CreatedDate
  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "last_modified_at")
  private Instant lastModifiedAt;
}
