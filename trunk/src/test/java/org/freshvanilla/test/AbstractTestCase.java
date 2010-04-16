/*
 Copyright 2008 Peter Lawrey

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an &quot;AS IS&quot; BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.freshvanilla.test;

import junit.framework.TestCase;

public abstract class AbstractTestCase extends TestCase {

    protected AbstractTestCase() {
        // nothing here
    }

    protected AbstractTestCase(String name) {
        super(name);
    }

    public boolean isFullBuild() {
        return Boolean.getBoolean("full.build");
    }

}
