/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2022 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.log;

import io.questdb.VisibleForTesting;
import io.questdb.network.Net;
import io.questdb.std.*;

import java.io.Closeable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.function.Consumer;

public class LogAlertSocket implements Closeable {

    public static final String localHostIp;

    static {
        try {
            localHostIp = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            throw new LogError("Cannot access our ip address info");
        }
    }

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 9093;
    public static final int IN_BUFFER_SIZE = 2 * 1024 * 1024;
    public static final int OUT_BUFFER_SIZE = 4 * 1024 * 1024;
    private static final int HOSTS_LIMIT = 12; // will never happen
    private static final int FAIL_OVER_LIMIT = 100;


    private final Rnd rand;
    private final FilesFacade ff;
    private final String[] alertHosts = new String[HOSTS_LIMIT];
    private final int[] alertPorts = new int[HOSTS_LIMIT];
    private final int outBufferSize;
    private final int inBufferSize;
    private long outBufferPtr;
    private long inBufferPtr;
    private int numAlertHosts;
    private int currentAlertHostIdx;
    private long fdSocketAddress = -1; // tcp/ip host:port address
    private long fdSocket = -1;
    private String alertTargets; // host[:port](,host[:port])*

    public LogAlertSocket(FilesFacade ff, String alertTargets) {
        this(ff, alertTargets, IN_BUFFER_SIZE, OUT_BUFFER_SIZE);
    }

    public LogAlertSocket(FilesFacade ff, String alertTargets, int outBufferSize) {
        this(ff, alertTargets, IN_BUFFER_SIZE, outBufferSize);
    }

    public LogAlertSocket(FilesFacade ff, String alertTargets, int inBufferSize, int outBufferSize) {
        this.ff = ff;
        this.rand = new Rnd(System.currentTimeMillis(), System.currentTimeMillis());
        this.alertTargets = alertTargets;
        parseAlertTargets();
        this.inBufferSize = inBufferSize;
        this.inBufferPtr = Unsafe.malloc(inBufferSize, MemoryTag.NATIVE_DEFAULT);
        this.outBufferSize = outBufferSize;
        this.outBufferPtr = Unsafe.malloc(outBufferSize, MemoryTag.NATIVE_DEFAULT);
    }

    public void connect() {
        fdSocketAddress = Net.sockaddr(alertHosts[currentAlertHostIdx], alertPorts[currentAlertHostIdx]);
        fdSocket = Net.socketTcp(true);
        System.out.printf("Connecting with: %s:%d%n", alertHosts[currentAlertHostIdx], alertPorts[currentAlertHostIdx]);
        if (fdSocket > -1) {
            if (Net.connect(fdSocket, fdSocketAddress) != 0) {
                System.out.println(" E could not connect");
                freeSocket();
            }
        } else {
            System.out.println(" E could not create TCP socket [errno=" + ff.errno() + "]");
            freeSocket();
        }
    }

    public void send(int len) {
        send(len, 0, null);
    }

    public void send(int len, Consumer<String> ackReceiver) {
        send(len, 0, ackReceiver);
    }

    @Override
    public void close() {
        if (fdSocket != -1) {
            freeSocket();
        }
        if (outBufferPtr != 0) {
            Unsafe.free(outBufferPtr, outBufferSize, MemoryTag.NATIVE_DEFAULT);
            outBufferPtr = 0;
        }
        if (inBufferPtr != 0) {
            Unsafe.free(inBufferPtr, IN_BUFFER_SIZE, MemoryTag.NATIVE_DEFAULT);
            inBufferPtr = 0;
        }
    }

    public long getOutBufferPtr() {
        return outBufferPtr;
    }

    public int getOutBufferSize() {
        return outBufferSize;
    }

    public long getInBufferPtr() {
        return inBufferPtr;
    }

    public long getInBufferSize() {
        return inBufferSize;
    }

    private void send(int len, int failOverLevel, Consumer<String> ackReceiver) {
        if (fdSocket > 0) {
            //
            int remaining = len;
            long p = outBufferPtr;
            boolean sendFail = false;
            while (remaining > 0) {
                int n = Net.send(fdSocket, p, remaining);
                if (n > 0) {
                    remaining -= n;
                    p += n;
                } else {
                    System.out.println("could not send [n=" + n + " [errno=" + ff.errno() + "]");
                    sendFail = true;
                }
            }
            if (sendFail) {
                failOver(len, failOverLevel + 1, ackReceiver);
            } else {
                // receive ack
                p = inBufferPtr;
                final int n = Net.recv(fdSocket, p, inBufferSize);
                if (n > 0) {
                    if (ackReceiver != null) {
                        ackReceiver.accept(Chars.stringFromUtf8Bytes(inBufferPtr, inBufferPtr + n));
                    } else {
                        Net.dumpAscii(p, n);
                    }
                } else {
                    failOver(len, failOverLevel + 1, ackReceiver);
                }
            }
        } else {
            failOver(len, failOverLevel + 1, ackReceiver);
        }
    }

    private void failOver(int len, int failOverLevel, Consumer<String> ackReceiver) {
        freeSocket();
        currentAlertHostIdx = (currentAlertHostIdx + 1) % numAlertHosts;
        if (failOverLevel < FAIL_OVER_LIMIT) {
            System.out.printf("Failing over to: %d%n", currentAlertHostIdx);
            connect();
            send(len, failOverLevel, ackReceiver);
        }
    }

    @VisibleForTesting
    String getAlertTargets() {
        return alertTargets;
    }

    @VisibleForTesting
    String[] getAlertHosts() {
        return alertHosts;
    }

    @VisibleForTesting
    int[] getAlertPorts() {
        return alertPorts;
    }

    @VisibleForTesting
    int getNumberOfAlertHosts() {
        return numAlertHosts;
    }

    private void freeSocket() {
        if (fdSocketAddress != -1) {
            Net.freeSockAddr(fdSocketAddress);
            fdSocketAddress = -1;
        }
        if (fdSocket != -1) {
            Net.close(fdSocket);
            fdSocket = -1;
        }
    }

    private void parseAlertTargets() {
        if (alertTargets == null) {
            setDefaultHostPort();
            return;
        }
        int startIdx = 0;
        int endIdx = alertTargets.length();
        if (endIdx == 0) {
            setDefaultHostPort();
            return;
        }

        if (Chars.isQuoted(alertTargets)) {
            startIdx++;
            endIdx--;
        }
        while (alertTargets.charAt(startIdx) == ' ' && startIdx < endIdx - 1) {
            startIdx++;
        }
        while (alertTargets.charAt(endIdx - 1) == ' ' && endIdx > startIdx) {
            endIdx--;
        }
        final int len = endIdx - startIdx;
        if (len == 0) {
            setDefaultHostPort();
            return;
        }

        // expected format: host[:port](,host[:port])*
        int hostIdx = startIdx;
        int portIdx = -1;
        for (int i = startIdx; i < endIdx; ++i) {
            char c = alertTargets.charAt(i);
            switch (c) {
                case ':':
                    if (portIdx != -1) {
                        throw new LogError(String.format(
                                "Unexpected ':' found at position %d: %s",
                                i,
                                alertTargets));
                    }
                    portIdx = i;
                    break;

                case ',':
                    setHostPort(hostIdx, portIdx, i);
                    hostIdx = i + 1;
                    portIdx = -1;
                    break;
            }
        }
        setHostPort(hostIdx, portIdx, len);
        currentAlertHostIdx = rand.nextInt(numAlertHosts);
    }

    private void setDefaultHostPort() {
        alertHosts[currentAlertHostIdx] = DEFAULT_HOST;
        alertPorts[currentAlertHostIdx] = DEFAULT_PORT;
        alertTargets = DEFAULT_HOST + ":" + DEFAULT_PORT;
        currentAlertHostIdx = 0;
        numAlertHosts = 1;
    }

    private void setHostPort(int hostIdx, int portLimit, int hostLimit) {
        // host0:port0, host1 : port1 , ..., host9:port9
        //              ^     ^       ^
        //              |     |       hostLimit
        //              |     portLimit
        //              hostIdx

        boolean hostResolved = false;
        int hostEnd = hostLimit;
        if (portLimit == -1) { // no ':' was found
            if (hostIdx + 1 > hostLimit) {
                alertHosts[numAlertHosts] = DEFAULT_HOST;
                hostResolved = true;
            }
            alertPorts[numAlertHosts] = DEFAULT_PORT;
        } else {
            if (hostIdx + 1 > portLimit) {
                alertHosts[numAlertHosts] = DEFAULT_HOST;
                hostResolved = true;
            } else {
                hostEnd = portLimit;
            }
            if (portLimit + 2 > hostLimit) {
                alertPorts[numAlertHosts] = DEFAULT_PORT;
            } else {
                int port = 0;
                int scale = 1;
                for (int i = hostLimit - 1; i > portLimit; i--) {
                    int c = alertTargets.charAt(i) - '0';
                    if (c > -1 && c < 10) {
                        port += c * scale;
                        scale *= 10;
                    } else {
                        throw new LogError(String.format(
                                "Invalid port value [%s] at position %d for socketAddress: %s",
                                alertTargets.substring(portLimit + 1, hostLimit),
                                portLimit + 1,
                                alertTargets
                        ));
                    }
                }
                alertPorts[numAlertHosts] = port;
            }
        }
        if (!hostResolved) {
            String host = alertTargets.substring(hostIdx, hostEnd).trim();
            try {
                alertHosts[numAlertHosts] = InetAddress.getByName(host).getHostAddress();
            } catch (UnknownHostException e) {
                throw new LogError(String.format(
                        "Invalid host value [%s] at position %d for socketAddress: %s",
                        host,
                        hostIdx,
                        alertTargets
                ));
            }
        }
        numAlertHosts++;
    }
}
