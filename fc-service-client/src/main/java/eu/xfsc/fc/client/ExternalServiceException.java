package eu.xfsc.fc.client;

/*-
 * ---license-start
 * fc-service-client
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

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class ExternalServiceException extends RuntimeException {
	
	private static final long serialVersionUID = 1L;
	
	private final HttpStatusCode status;
	private final Map<String, Object> details;

	public ExternalServiceException(HttpStatusCode status, Map<String, Object> details) {
		super((String) null);
		this.status = status;
		this.details = details;
	}
	
	public HttpStatusCode getStatus() {
		return status;
	}
	
	public Map<String, Object> getDetails() {
		return details;
	}
	
	@Override
	public String getMessage() {
		return details == null ? HttpStatus.valueOf(status.value()).toString() : details.toString();
	}

}
