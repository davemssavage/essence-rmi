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

package org.freshvanilla.rmi;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

import org.freshvanilla.test.AbstractTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MXRmiTest extends AbstractTestCase {

    private static Logger LOG = LoggerFactory.getLogger(MXRmiTest.class.getName());
    private static final NumberFormat DECIMAL = new DecimalFormat();

    public static void test_MXService() throws IOException {
        VanillaRmiServer<RuntimeMXBean> server = Proxies.newServer("mx", 0,
            ManagementFactory.getRuntimeMXBean());
        RuntimeMXBean mxBean = null;

        try {
            mxBean = Proxies.newClient("mx-client", "localhost:" + server.getPort(), RuntimeMXBean.class);

            final String name = mxBean.getName();
            assertNotNull(name);

            long start = System.nanoTime();

            final String path = mxBean.getClassPath();
            assertNotNull(path);

            final long startTime = mxBean.getStartTime();
            assertEquals(ManagementFactory.getRuntimeMXBean().getStartTime(), startTime);

            final boolean classPathSupported = mxBean.isBootClassPathSupported();
            assertTrue(classPathSupported);

            final List<String> stringList = mxBean.getInputArguments();
            assertNotNull(stringList);

            final Map<String, String> map = mxBean.getSystemProperties();
            assertNotNull(map);
            assertFalse(map.isEmpty());

            long time = System.nanoTime() - start;
            int count = 5;
            LOG.info("Got " + count + " values in " + DECIMAL.format(time / 1000 / count)
                     + " us/call without a warm up.");
        }
        finally {
            closeClient(mxBean);
            closeServer(server);
        }
    }
}
