/*
 * Copyright (c) 2015 Google Inc.  All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.ngo.squeezer.util;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScanNetworkTaskTest extends TestCase {
    public void testExtractNameFromBuffer() {
        List<BufferTest> testTable = new ArrayList<>();

        // Success cases.

        // Find NAME if it's the first entry in the buffer.
        testTable.add(new BufferTest(
                "NAME as first entry", "ENAME\04Test", "NAME:Test"));

        // Find NAME if it's not the first entry in the buffer.
        testTable.add(new BufferTest(
                "NAME as second entry", "EIPAD\049001NAME\04Test", "IPAD:9001;NAME:Test"));

        // Single character NAME is OK.
        testTable.add(new BufferTest(
                "NAME is a single char", "EIPAD\049001NAME\01T", "IPAD:9001;NAME:T"));

        // NAME tuple followed by another tuple is OK.
        testTable.add(new BufferTest(
                "Successor tuple", "EIPAD\049001NAME\04TestVERS\011", "IPAD:9001;NAME:Test"));

        // Names longer than 127 characters are OK (catch errors as bytes are signed).
        addLongName(testTable, 128);
        addLongName(testTable, 255);

        // Expected failure cases to handle..

        testTable.add(new BufferTest(
                "No NAME block in buffer", "EIPAD\049001", "IPAD:9001"));
        testTable.add(new BufferTest(
                "NAME block is too short (1)", "EIPAD\049001N", "IPAD:9001"));
        testTable.add(new BufferTest(
                "NAME block is too short (2)", "EIPAD\049001NA", "IPAD:9001"));
        testTable.add(new BufferTest(
                "NAME block is too short (3)", "EIPAD\049001NAM", "IPAD:9001"));
        testTable.add(new BufferTest(
                "NAME block is too short (4)", "EIPAD\049001NAME", "IPAD:9001"));
        testTable.add(new BufferTest(
                "NAME block is too short (5)", "EIPAD\049001NAME\01", "IPAD:9001"));
        testTable.add(new BufferTest(
                "Corrupted (short) packet", "EIPAD\049001NAME\10Test 2", "IPAD:9001"));

        for (BufferTest test : testTable) {
            Map<String, String> actual = ScanNetworkTask.parseDiscover(test.buffer.length, test.buffer);
            assertEquals(test.message, test.expected, actual);
        }
    }

    private void addLongName(List<BufferTest> testTable, int n) {
        String ENAME = "ENAME\01";

        // Need to use a byte[] here instead of a string, as a string literal \200
        // and above becomes a 2-byte value when String.getBytes() is called.
        byte[] name = new byte[n];
        Arrays.fill(name, (byte)'a');
        byte[] buffer = new byte[ENAME.length()+ n];
        System.arraycopy(ENAME.getBytes(), 0, buffer, 0, ENAME.length());
        System.arraycopy(name, 0, buffer, ENAME.length(), n);
        buffer[5] = (byte) n;
        testTable.add(new BufferTest(n + " char name", buffer, "NAME:" + new String(name)));
    }

    /**
     * Represents a single test of the buffer extraction code.
     */
    private static class BufferTest {
        /** Test message. */
        final String message;

        /** Buffer to test. */
        final byte[] buffer;

        /** Expected value. */
        final Map<String, String> expected;

        BufferTest(String message, byte[] buffer, String expected) {
            this.message = message;
            this.buffer = buffer;
            this.expected = new HashMap<>();
            for (String entry: expected.split(";")) {
                String[] split = entry.split(":");
                this.expected.put(split[0], split[1]);
            }
        }

        BufferTest(String message, String buffer, String expected) {
            this(message, buffer.getBytes(), expected);
        }
    }
}