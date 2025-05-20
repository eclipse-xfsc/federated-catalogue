package eu.xfsc.fc.server.model;


import jakarta.validation.constraints.Min;

@lombok.AllArgsConstructor
@lombok.Getter
@lombok.ToString
public class GraphRebuildRequest {
    
    @Min(1)   
    private int chunkCount;
    @Min(0)   
    private int chunkId;
    @Min(1)   
    private int threads;
    @Min(1)   
    private int batchSize;

}
