package eu.xfsc.fc.core.service.pubsub;

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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import jakarta.annotation.PostConstruct;

public abstract class BaseAssetPublisher implements AssetPublisher {

    @Value("${publisher.instance}")
    protected String instance;
    @Value("${publisher.pool-size:4}")
    protected int poolSize;
    @Value("${publisher.transactional:false}")
    protected boolean transactional;
	
    @Autowired 
	protected ObjectMapper jsonMapper;
	
    private ExecutorService threadPool;
    
    @PostConstruct
    public void init() throws Exception {
   		threadPool = Executors.newFixedThreadPool(poolSize); // .newVirtualThreadPerTaskExecutor();
    	initialize();
    }

    @Override
	public boolean isTransactional() {
    	return transactional;
    }
    
	@Override
	public boolean publish(AssetMetadata assetMetadata, CredentialVerificationResult verificationResult) {
		if (supportsMetadataUpdate()) {
			if (transactional) {
				return publishInternal(assetMetadata, verificationResult);
			} else {
				threadPool.execute(() -> {
					// set thread name?
					publishInternal(assetMetadata, verificationResult);
				});
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean publish(String hash, AssetEvent event, AssetStatus status) {
		if (supportsStatusUpdate()) {
			if (transactional) {
				return publishInternal(hash, event, status);
			} else {
				threadPool.execute(() -> {
					// set thread name?
					publishInternal(hash, event, status);
				});
				return true;
			}
		}
		return false;
	}

	@Override
	public void setTransactional(boolean transactional) {
		this.transactional = transactional;
	}

    protected void initialize() throws Exception {
    	// any initialization steps here..
    }

	protected boolean publishInternal(AssetMetadata assetMetadata, CredentialVerificationResult verificationResult) {
		return false;
	}

	protected boolean publishInternal(String hash, AssetEvent event, AssetStatus status) {
		return false;
	}
	
    protected boolean supportsMetadataUpdate() {
    	return true;
    }

    protected boolean supportsStatusUpdate() {
    	return true;
    }
    
}
