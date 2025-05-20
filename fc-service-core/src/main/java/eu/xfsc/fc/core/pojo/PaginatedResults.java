package eu.xfsc.fc.core.pojo;

import java.util.List;

@lombok.AllArgsConstructor
@lombok.Getter
@lombok.ToString
public class PaginatedResults<R> {
    
    private long totalCount;
    private List<R> results;
    
    public PaginatedResults(List<R> results) {
        this.results = results;
        this.totalCount = results.size();
    }

}
