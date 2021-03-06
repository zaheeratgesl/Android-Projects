/*
 * Copyright (C) 2017 The Android Open Source Project
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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Main {
  public static void main(String[] args) throws Exception {
    doTest();
  }

  private static void doTest() throws Exception {
    Thread t1 = Thread.currentThread();
    Thread t2 = getCurrentThread();

    if (t1 != t2) {
      throw new RuntimeException("Expected " + t1 + " but got " + t2);
    }
    System.out.println("currentThread OK");

    printThreadInfo(t1);
    printThreadInfo(null);

    Thread t3 = new Thread("Daemon Thread");
    t3.setDaemon(true);
    // Do not start this thread, yet.
    printThreadInfo(t3);
    // Start, and wait for it to die.
    t3.start();
    t3.join();
    Thread.sleep(500);  // Wait a little bit.
    // Thread has died, check that we can still get info.
    printThreadInfo(t3);

    doStateTests();

    doAllThreadsTests();

    doTLSTests();

    doTestEvents();
  }

  private static class Holder {
    volatile boolean flag = false;
  }

  private static void doStateTests() throws Exception {
    System.out.println(Integer.toHexString(getThreadState(null)));
    System.out.println(Integer.toHexString(getThreadState(Thread.currentThread())));

    final CountDownLatch cdl1 = new CountDownLatch(1);
    final CountDownLatch cdl2 = new CountDownLatch(1);
    final CountDownLatch cdl3_1 = new CountDownLatch(1);
    final CountDownLatch cdl3_2 = new CountDownLatch(1);
    final CountDownLatch cdl4 = new CountDownLatch(1);
    final CountDownLatch cdl5 = new CountDownLatch(1);
    final Holder h = new Holder();
    Runnable r = new Runnable() {
      @Override
      public void run() {
        try {
          cdl1.countDown();
          synchronized(cdl1) {
            cdl1.wait();
          }

          cdl2.countDown();
          synchronized(cdl2) {
            cdl2.wait(1000);  // Wait a second.
          }

          cdl3_1.await();
          cdl3_2.countDown();
          synchronized(cdl3_2) {
            // Nothing, just wanted to block on cdl3.
          }

          cdl4.countDown();
          Thread.sleep(1000);

          cdl5.countDown();
          while (!h.flag) {
            // Busy-loop.
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };

    Thread t = new Thread(r);
    printThreadState(t);
    t.start();

    // Waiting.
    cdl1.await();
    Thread.yield();
    Thread.sleep(100);
    printThreadState(t);
    synchronized(cdl1) {
      cdl1.notifyAll();
    }

    // Timed waiting.
    cdl2.await();
    Thread.yield();
    Thread.sleep(100);
    printThreadState(t);
    synchronized(cdl2) {
      cdl2.notifyAll();
    }

    // Blocked on monitor.
    synchronized(cdl3_2) {
      cdl3_1.countDown();
      cdl3_2.await();
      // While the latch improves the chances to make good progress, scheduling might still be
      // messy. Wait till we get the right Java-side Thread state.
      do {
        Thread.yield();
      } while (t.getState() != Thread.State.BLOCKED);
      Thread.sleep(10);
      printThreadState(t);
    }

    // Sleeping.
    cdl4.await();
    Thread.yield();
    Thread.sleep(100);
    printThreadState(t);

    // Running.
    cdl5.await();
    Thread.yield();
    Thread.sleep(100);
    printThreadState(t);
    h.flag = true;

    // Dying.
    t.join();
    Thread.yield();
    Thread.sleep(100);

    printThreadState(t);
  }

  private static void doAllThreadsTests() {
    Thread[] threads = getAllThreads();
    List<Thread> threadList = new ArrayList<>(Arrays.asList(threads));

    // Filter out JIT thread. It may or may not be there depending on configuration.
    Iterator<Thread> it = threadList.iterator();
    while (it.hasNext()) {
      Thread t = it.next();
      if (t.getName().startsWith("Jit thread pool worker")) {
        it.remove();
        break;
      }
    }

    Collections.sort(threadList, THREAD_COMP);
    System.out.println(threadList);
  }

  private static void doTLSTests() throws Exception {
    doTLSNonLiveTests();
    doTLSLiveTests();
  }

  private static void doTLSNonLiveTests() throws Exception {
    Thread t = new Thread();
    try {
      setTLS(t, 1);
      System.out.println("Expected failure setting TLS for non-live thread");
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
    t.start();
    t.join();
    try {
      setTLS(t, 1);
      System.out.println("Expected failure setting TLS for non-live thread");
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
  }

  private static void doTLSLiveTests() throws Exception {
    setTLS(Thread.currentThread(), 1);

    long l = getTLS(Thread.currentThread());
    if (l != 1) {
      throw new RuntimeException("Unexpected TLS value: " + l);
    };

    final CountDownLatch cdl1 = new CountDownLatch(1);
    final CountDownLatch cdl2 = new CountDownLatch(1);

    Runnable r = new Runnable() {
      @Override
      public void run() {
        try {
          cdl1.countDown();
          cdl2.await();
          setTLS(Thread.currentThread(), 2);
          if (getTLS(Thread.currentThread()) != 2) {
            throw new RuntimeException("Different thread issue");
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };

    Thread t = new Thread(r);
    t.start();
    cdl1.await();
    setTLS(Thread.currentThread(), 1);
    cdl2.countDown();

    t.join();
    if (getTLS(Thread.currentThread()) != 1) {
      throw new RuntimeException("Got clobbered");
    }
  }

  private static void doTestEvents() throws Exception {
    enableThreadEvents(true);

    Thread t = new Thread("EventTestThread");

    System.out.println("Constructed thread");
    Thread.yield();

    t.start();
    t.join();

    System.out.println("Thread joined");

    enableThreadEvents(false);
  }

  private final static Comparator<Thread> THREAD_COMP = new Comparator<Thread>() {
    public int compare(Thread o1, Thread o2) {
      return o1.getName().compareTo(o2.getName());
    }
  };

  private final static Map<Integer, String> STATE_NAMES = new HashMap<Integer, String>();
  private final static List<Integer> STATE_KEYS = new ArrayList<Integer>();
  static {
    STATE_NAMES.put(0x1, "ALIVE");
    STATE_NAMES.put(0x2, "TERMINATED");
    STATE_NAMES.put(0x4, "RUNNABLE");
    STATE_NAMES.put(0x400, "BLOCKED_ON_MONITOR_ENTER");
    STATE_NAMES.put(0x80, "WAITING");
    STATE_NAMES.put(0x10, "WAITING_INDEFINITELY");
    STATE_NAMES.put(0x20, "WAITING_WITH_TIMEOUT");
    STATE_NAMES.put(0x40, "SLEEPING");
    STATE_NAMES.put(0x100, "IN_OBJECT_WAIT");
    STATE_NAMES.put(0x200, "PARKED");
    STATE_NAMES.put(0x100000, "SUSPENDED");
    STATE_NAMES.put(0x200000, "INTERRUPTED");
    STATE_NAMES.put(0x400000, "IN_NATIVE");
    STATE_KEYS.addAll(STATE_NAMES.keySet());
    Collections.sort(STATE_KEYS);
  }
  
  private static void printThreadState(Thread t) {
    int state = getThreadState(t);

    StringBuilder sb = new StringBuilder();

    for (Integer i : STATE_KEYS) {
      if ((state & i) != 0) {
        if (sb.length()>0) {
          sb.append('|');
        }
        sb.append(STATE_NAMES.get(i));
      }
    }

    if (sb.length() == 0) {
      sb.append("NEW");
    }

    System.out.println(Integer.toHexString(state) + " = " + sb.toString());
  }

  private static void printThreadInfo(Thread t) {
    Object[] threadInfo = getThreadInfo(t);
    if (threadInfo == null || threadInfo.length != 5) {
      System.out.println(Arrays.toString(threadInfo));
      throw new RuntimeException("threadInfo length wrong");
    }

    System.out.println(threadInfo[0]);  // Name
    System.out.println(threadInfo[1]);  // Priority
    System.out.println(threadInfo[2]);  // Daemon
    System.out.println(threadInfo[3]);  // Threadgroup
    System.out.println(threadInfo[4] == null ? "null" : threadInfo[4].getClass());  // Context CL.
  }

  private static native Thread getCurrentThread();
  private static native Object[] getThreadInfo(Thread t);
  private static native int getThreadState(Thread t);
  private static native Thread[] getAllThreads();
  private static native void setTLS(Thread t, long l);
  private static native long getTLS(Thread t);
  private static native void enableThreadEvents(boolean b);
}
