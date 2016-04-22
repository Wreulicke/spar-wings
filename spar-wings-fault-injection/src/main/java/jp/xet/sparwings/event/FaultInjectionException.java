/*
 * Copyright 2011 Daisuke Miyamoto. (http://d.hatena.ne.jp/daisuke-m)
 * Created on 2016/04/22
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package jp.xet.sparwings.event;

/**
 * Exception to indicate fault injection is occured.
 * 
 * @since #version#
 * @author daisuke
 */
@SuppressWarnings("serial")
public class FaultInjectionException extends RuntimeException {
	
	/**
	 * Create instance.
	 */
	public FaultInjectionException() {
	}
	
	/**
	* Create instance.
	* 
	* @param message The exception message
	*/
	public FaultInjectionException(String message) {
		super(message);
	}
}
