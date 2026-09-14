package eu.xfsc.fc.core.pojo;

/*-
 * ---license-start
 * fc-service-core
 * ---
 * Copyright (c) 2022 - 2026 Contributors to the Eclipse Foundation
 * ---
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 * ---license-end
 */

import java.util.Map;

import eu.xfsc.fc.api.generated.model.QueryLanguage;

/**
 * POJO Class for holding a Cypher Query.
 */
@lombok.AllArgsConstructor
@lombok.EqualsAndHashCode
@lombok.Getter
@lombok.ToString
public class GraphQuery {
    
    public static final int QUERY_TIMEOUT = 5;
	
	private final String query;
	private Map<String, Object> params;
	private QueryLanguage queryLanguage;
    private int timeout;
    private boolean withTotalCount;

    public GraphQuery(String query, Map<String, Object> params) {
      this(query, params, QueryLanguage.OPENCYPHER, QUERY_TIMEOUT, true);  
    }
    
}
