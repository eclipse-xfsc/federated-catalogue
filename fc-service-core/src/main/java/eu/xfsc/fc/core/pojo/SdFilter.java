package eu.xfsc.fc.core.pojo;

import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import java.time.Instant;
import java.util.List;

/**
 * Filter parameters for searching self-description meta data. If multiple items
 * are present, they are combined with an 'AND' semantic.
 */
@lombok.Getter
public class SdFilter {

  /**
   * Start time for the time range filter when the self-description was uploaded
   * to the catalogue.
   */
  private Instant uploadTimeStart;

  /**
   * End time for the time range filter when the self-description was uploaded to
   * the catalogue.
   */
  private Instant uploadTimeEnd;

  /**
   * Start time for the time range filter when the status of the self-description
   * was last changed in the catalogue.
   */
  private Instant statusTimeStart;

  /**
   * End time for the time range filter when the status of the self-description
   * was last changed in the catalogue.
   */
  private Instant statusTimeEnd;

  /**
   * Filter for the issuer of the self-description. This is the unique ID
   * (credentialSubject) of the Participant that has prepared the
   * self-description.
   */
  @lombok.Setter
  private List<String> issuers;

  /**
   * Filter for a validator of the self-description. This is the unique ID
   * (credentialSubject) of the Participant that validated (part of) the
   * self-description.
   */
  @lombok.Setter
  private List<String> validators;

  /**
   * Filter for the status of the self-description.
   */
  @lombok.Setter
  private List<SelfDescriptionStatus> statuses;

  /**
   * Filter for a id/credentialSubject of the self-description.
   */
  @lombok.Setter
  private List<String> ids;

  /**
   * Filter for a hash of the self-description.
   */
  @lombok.Setter
  private List<String> hashes;

  /**
   * The offset to start returning results when applying this filter.
   */
  @lombok.Setter
  private int offset;

  /**
   * Maximum number of results to return when applying this filter. When set to 0,
   * no limit applies.
   */
  @lombok.Setter
  private int limit;

  /**
   * Sets the upload time range that the filter will check for a self-description
   * record to match. The upload time specifies when the self-description was
   * uploaded to the catalogue. Start time and end time must be either both
   * {@code null} or both non-{@code null}. Note: For not imposing any upper limit
   * in time, {@code Instant.MAX} is <em>not</em> usable, since Hibernate will not
   * accept this value and throw an exception.
   *
   * @param uploadTimeStart Start time of the time range that this filter will
   *                        check for a self-description record to match.
   * @param uploadTimeEnd   End time of the time range that this filter will check
   *                        for a self-description record to match.
   * @throws IllegalArgumentException If either start time or end time is
   *                                  {@code null}, while the other is
   *                                  non-{@code null}.
   */
  public void setUploadTimeRange(final Instant uploadTimeStart, final Instant uploadTimeEnd) {
    if ((uploadTimeStart == null) ^ (uploadTimeEnd == null)) {
      throw new IllegalArgumentException("start time and end time may be both null, but not just one of them");
    }
    this.uploadTimeStart = uploadTimeStart;
    this.uploadTimeEnd = uploadTimeEnd;
  }

  /**
   * Sets the status time range that the filter will check for a self-description
   * record to match. The status time specifies when the self-description was last
   * changed in the catalogue. Start time and end time must be either both
   * {@code null} or both non-{@code null}. Note: For not imposing any upper limit
   * in time, {@code Instant.MAX} is <em>not</em> usable, since Hibernate will not
   * accept this value and throw an exception.
   *
   * @param statusTimeStart Start time of the time range that this filter will
   *                        check for a self-description record to match.
   * @param statusTimeEnd   End time of the time range that this filter will check
   *                        for a self-description record to match.
   */
  public void setStatusTimeRange(final Instant statusTimeStart, final Instant statusTimeEnd) {
    if ((statusTimeStart == null) ^ (statusTimeEnd == null)) {
      throw new IllegalArgumentException("start time and end time may be both null, but not just one of them");
    }
    this.statusTimeStart = statusTimeStart;
    this.statusTimeEnd = statusTimeEnd;
  }
}
