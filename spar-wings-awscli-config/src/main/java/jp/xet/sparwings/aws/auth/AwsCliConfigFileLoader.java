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
package jp.xet.sparwings.aws.auth;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.profiles.internal.ProfileFileReader;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

/**
 * TODO for daisuke
 * 
 * @since 0.10
 * @version $Id$
 * @author daisuke
 */
public class AwsCliConfigFileLoader { // NOPMD - cc
	
	public static Map<String, AwsCliProfile> loadProfiles(File file) {
		if (file == null) {
			throw new IllegalArgumentException("Unable to load AWS profiles: specified file is null.");
		}
		
		if (file.exists() == false || file.isFile() == false) {
			throw new IllegalArgumentException("AWS credential profiles file not found in the given path: "
					+ file.getAbsolutePath());
		}
		
		try (FileInputStream fis = new FileInputStream(file)) {
			return loadProfiles(fis);
		} catch (IOException ioe) {
			throw new IllegalStateException(
					"Unable to load AWS credential profiles file at: " + file.getAbsolutePath(), ioe);
		}
	}
	
	/**
	 * Loads the credential profiles from the given input stream.
	 *
	 * @param is input stream from where the profile details are read.
	 * @return
	 * @throws IOException
	 */
	private static Map<String, AwsCliProfile> loadProfiles(InputStream is) throws IOException { // NOPMD - cc
		try (InputStream stream = is) {
			Map<String, Map<String, String>> allProfileProperties =
					ProfileFileReader.parseFile(is, ProfileFile.Type.CREDENTIALS);
			
			// Convert the loaded property map to credential objects
			Map<String, AwsCliProfile> profilesByName = new LinkedHashMap<>();
			
			for (Entry<String, Map<String, String>> entry : allProfileProperties.entrySet()) {
				String profileName = entry.getKey();
				Map<String, String> properties = entry.getValue();
				
				if (profileName.equals(AwsCliConfigFile.DEFAULT_PROFILE_NAME) == false) {
					if (profileName.startsWith("profile ")) {
						profileName = profileName.substring("profile ".length());
					}
				}
				
				String accessKey = properties.get(AwsCliProfile.AWS_ACCESS_KEY_ID);
				String secretKey = properties.get(AwsCliProfile.AWS_SECRET_ACCESS_KEY);
				String sessionToken = properties.get(AwsCliProfile.AWS_SESSION_TOKEN);
				String roleArn = properties.get(AwsCliProfile.AWS_ROLE_ARN);
				String sourceProfile = properties.get(AwsCliProfile.AWS_SOURCE_PROFILE);
				String roleSessionName = properties.get(AwsCliProfile.AWS_ROLE_SESSION_NAME);
				
				assertParameterNotEmpty(profileName,
						"Unable to load credentials into profile: ProfileName is empty.");
				if (accessKey != null && secretKey != null) {
					if (sessionToken == null) {
						AwsCredentialsProvider cp = StaticCredentialsProvider.create(
								AwsBasicCredentials.create(accessKey, secretKey));
						profilesByName.put(profileName, new AwsCliProfile(profileName, cp));
					} else {
						if (sessionToken.isEmpty()) {
							String msg = String.format(Locale.ENGLISH,
									"Unable to load credentials into profile [%s]: AWS Session Token is empty.",
									profileName);
							throw new IllegalStateException(msg);
						}
						
						AwsCredentialsProvider cp = StaticCredentialsProvider.create(
								AwsSessionCredentials.create(accessKey, secretKey, sessionToken));
						profilesByName.put(profileName, new AwsCliProfile(profileName, cp));
					}
				} else if (roleArn != null && sourceProfile != null) {
					if (roleSessionName == null) {
						roleSessionName = "defaultsession";
					}
					AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
						.roleArn(roleArn)
						.roleSessionName(roleSessionName)
						.build();
					AwsCredentialsProvider source = AwsCredentialsProviderChain.of(
							new AwsCliConfigProfileCredentialsProvider(sourceProfile),
							ProfileCredentialsProvider.create(sourceProfile));
					StsClient stsClient = StsClient.builder().credentialsProvider(source).build();
					AwsCredentialsProvider cp =
							StsAssumeRoleCredentialsProvider.builder()
								.stsClient(stsClient)
								.refreshRequest(assumeRoleRequest)
								.build();
					profilesByName.put(profileName, new AwsCliProfile(profileName, cp));
				}
			}
			
			return profilesByName;
		}
	}
	
	/**
	 * <p>
	 * Asserts that the specified parameter value is neither <code>empty</code>
	 * nor null, and if it is, throws an <code>AmazonClientException</code> with
	 * the specified error message.
	 * </p>
	 *
	 * @param parameterValue
	 *            The parameter value being checked.
	 * @param errorMessage
	 *            The error message to include in the AmazonClientException if
	 *            the specified parameter value is empty.
	 */
	private static void assertParameterNotEmpty(String parameterValue, String errorMessage) {
		if (parameterValue == null || parameterValue.isEmpty()) {
			throw new IllegalStateException(errorMessage);
		}
	}
	
}
