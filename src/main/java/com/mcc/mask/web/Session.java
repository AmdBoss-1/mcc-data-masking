package com.mcc.mask.web;

import com.mcc.mask.codebook.Codebook;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * 控制台会话：解锁后的码本 + 口令只活在这一份内存里。
 * 锁定（手动/刷新/闲置超时）即把 char[] 覆写清零；口令不进 URL、日志、码本文件。
 */
public final class Session {

    private final long idleMillis;
    private Path bookFile;
    private char[] pass;
    private Codebook book;
    private long lastTouch;

    public Session(long idleMillis) {
        this.idleMillis = idleMillis;
    }

    public synchronized void open(Path bookFile, char[] password, Codebook opened) {
        lock();
        this.bookFile = bookFile;
        this.pass = Arrays.copyOf(password, password.length);
        this.book = opened;
        this.lastTouch = System.currentTimeMillis();
    }

    public synchronized boolean unlocked() {
        if (book == null) {
            return false;
        }
        if (System.currentTimeMillis() - lastTouch > idleMillis) {
            lock();
            return false;
        }
        return true;
    }

    public synchronized void touch() {
        this.lastTouch = System.currentTimeMillis();
    }

    /** 立即清内存口令并丢码本句柄。 */
    public synchronized void lock() {
        if (pass != null) {
            Arrays.fill(pass, '\0');
        }
        pass = null;
        book = null;
        bookFile = null;
    }

    /** 返回内部口令数组本体（调用方不得长期持有、不得写日志）。 */
    public synchronized char[] pass() {
        return pass;
    }

    public synchronized Codebook book() {
        return book;
    }

    public synchronized Path bookFile() {
        return bookFile;
    }
}
