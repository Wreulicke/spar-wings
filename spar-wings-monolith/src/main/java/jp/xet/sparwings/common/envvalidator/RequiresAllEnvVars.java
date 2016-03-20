/*
 * Copyright 2015-2016 Miyamoto Daisuke.
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
package jp.xet.sparwings.common.envvalidator;

import java.util.Collection;

/**
 * 全ての環境変数が必要。
 * 
 * @since 0.4
 * @version $Id$
 * @author daisuke
 */
public class RequiresAllEnvVars extends AbstractRequiresAllRequirement {
	
	/**
	 * インスタンスを生成する。
	 * 
	 * @param keys environment variable names
	 */
	public RequiresAllEnvVars(Collection<String> keys) {
		super(keys);
	}
	
	/**
	 * インスタンスを生成する。
	 * 
	 * @param keys
	 */
	public RequiresAllEnvVars(String... keys) {
		super(keys);
	}
	
	@Override
	protected boolean exists(String key) {
		return System.getenv(key) != null;
	}
	
	@Override
	protected String getTargetName() {
		return "Environment variable";
	}
}