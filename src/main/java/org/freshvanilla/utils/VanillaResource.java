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

package org.freshvanilla.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VanillaResource implements SimpleResource {

    protected final String name;
    private volatile boolean closed = false;

    public VanillaResource(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isClosed() {
        return closed;
    }

    public void close() {
        closed = true;
    }

    public void checkedClosed() throws IllegalStateException {
        if (closed) {
            throw new IllegalStateException(name + " closed!");
        }
    }

    protected void finalize() throws Throwable {
        try {
            if (!closed) {
                close();
            }
        }
        finally {
            super.finalize();
        }
    }

    protected Logger getLog() {
        return LoggerFactory.getLogger(getClass());
    }

}
