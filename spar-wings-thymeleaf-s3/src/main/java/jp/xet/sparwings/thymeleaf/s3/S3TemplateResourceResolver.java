/*
 * Copyright 2015-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jp.xet.sparwings.thymeleaf.s3;

import java.io.InputStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.thymeleaf.TemplateProcessingParameters;
import org.thymeleaf.resourceresolver.IResourceResolver;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * {@link IResourceResolver} implementation to get S3 object resource.
 * 
 * @since 0.8
 * @version $Id$
 * @author fd0
 */
@Slf4j
@RequiredArgsConstructor
public class S3TemplateResourceResolver implements IResourceResolver {
	
	private final S3Client s3;
	
	private final String bucketName;
	
	
	@Override
	public String getName() {
		return "S3TemplateResourceResolver";
	}
	
	@Override
	public InputStream getResourceAsStream(TemplateProcessingParameters templateProcessingParameters,
			String resourceName) {
		try {
			return s3.getObject(GetObjectRequest.builder()
				.bucket(bucketName)
				.key(resourceName)
				.build());
		} catch (NoSuchKeyException e) {
			log.trace(e.getMessage(), e);
		} catch (S3Exception e) {
			log.warn(e.getMessage());
		} catch (Exception e) { // NOPMD
			log.warn("Unexpected", e);
		}
		return null;
	}
}
