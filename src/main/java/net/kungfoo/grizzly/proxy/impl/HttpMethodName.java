/**
 * Copyright 2009 Hubert Iwaniuk <neotyk@kungfoo.pl>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package net.kungfoo.grizzly.proxy.impl;

/**
 * HTTP Method names.
 *
 * @author Hubert Iwaniuk
 */
public enum HttpMethodName {
    TRACE,
    GET,
    DELETE,
    HEAD,
    OPTIONS,
    POST,
    PUT,
    CONNECT;

    public boolean equalsIgnoreCase(String methodName) {
        return this.name().equalsIgnoreCase(methodName);
    }
}
