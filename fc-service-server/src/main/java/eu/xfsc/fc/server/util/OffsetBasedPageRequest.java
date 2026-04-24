package eu.xfsc.fc.server.util;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * {@link Pageable} implementation that carries a raw offset and limit.
 * Use instead of {@link org.springframework.data.domain.PageRequest} when the caller
 * provides an arbitrary byte offset that may not align to page boundaries.
 */
public class OffsetBasedPageRequest implements Pageable {

  private final long offset;
  private final int limit;

  public OffsetBasedPageRequest(long offset, int limit) {
    if (offset < 0) {
      throw new IllegalArgumentException("Offset must not be negative");
    }
    if (limit < 1) {
      throw new IllegalArgumentException("Limit must be at least 1");
    }
    this.offset = offset;
    this.limit = limit;
  }

  @Override
  public int getPageNumber() {
    return (int) (offset / limit);
  }

  @Override
  public int getPageSize() {
    return limit;
  }

  @Override
  public long getOffset() {
    return offset;
  }

  @Override
  public Sort getSort() {
    return Sort.unsorted();
  }

  @Override
  public Pageable next() {
    return new OffsetBasedPageRequest(offset + limit, limit);
  }

  @Override
  public Pageable previousOrFirst() {
    return hasPrevious() ? new OffsetBasedPageRequest(Math.max(0, offset - limit), limit) : first();
  }

  @Override
  public Pageable first() {
    return new OffsetBasedPageRequest(0, limit);
  }

  @Override
  public Pageable withPage(int pageNumber) {
    return new OffsetBasedPageRequest((long) pageNumber * limit, limit);
  }

  @Override
  public boolean hasPrevious() {
    return offset > 0;
  }
}
