package com.mcc.mask.web;

import com.mcc.mask.codebook.Codebook;
import com.mcc.mask.codebook.CodebookJson;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 会话：口令只在内存、锁定即清零、闲置自动锁。 */
class SessionTest {

    private static Session open(long idleMillis) {
        Session s = new Session(idleMillis);
        s.open(Path.of("mb/book.mccmask"), "口令123".toCharArray(), new Codebook(new CodebookJson()));
        return s;
    }

    @Test
    void 解锁后可用且口令在手() {
        Session s = open(60_000);
        assertTrue(s.unlocked());
        assertArrayEquals("口令123".toCharArray(), s.pass());
        s.lock();
        assertFalse(s.unlocked());
    }

    @Test
    void 锁定后口令被清零() {
        Session s = open(60_000);
        char[] pass = s.pass();
        s.lock();
        for (char c : pass) {
            assertTrue(c == 0, "外部持有的口令副本应已被覆盖");
        }
    }

    @Test
    void 闲置超时自动锁() throws Exception {
        Session s = open(80);
        assertTrue(s.unlocked());
        Thread.sleep(140);
        assertFalse(s.unlocked());
    }

    @Test
    void 有活动就续期() throws Exception {
        Session s = open(200);
        for (int i = 0; i < 4; i++) {
            Thread.sleep(70);
            s.touch();
        }
        assertTrue(s.unlocked());
    }
}
