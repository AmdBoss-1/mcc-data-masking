package com.mcc.mask.cli;

import com.mcc.mask.MaskException;
import com.mcc.mask.codebook.Codebook;
import com.mcc.mask.codebook.CodebookJson;
import com.mcc.mask.codebook.CodebookStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 码本定位与打开：默认 ./maskbook/book.mccmask；新建时自动写目录级 .gitignore。 */
public final class BookIo {

    public static final String DEFAULT_DIR = "maskbook";
    public static final String FILE_NAME = "book.mccmask";

    public static Path resolve(String bookDir, boolean create) {
        Path dir = Path.of(bookDir == null || bookDir.isBlank() ? DEFAULT_DIR : bookDir);
        Path file = dir.resolve(FILE_NAME);
        if (create) {
            try {
                Files.createDirectories(dir);
                Path gi = dir.resolve(".gitignore");
                if (!Files.exists(gi)) {
                    Files.writeString(gi, "*\n", StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new MaskException("创建码本目录失败: " + e.getMessage(), e);
            }
        }
        return file;
    }

    /** 打开（或新建）码本。 */
    public static Codebook open(Path file, char[] pass, boolean allowCreate) {
        if (Files.exists(file)) {
            try {
                return new Codebook(CodebookStore.load(file, pass));
            } catch (IOException e) {
                throw new MaskException("码本读取失败: " + e.getMessage(), e);
            }
        }
        if (!allowCreate) {
            throw new MaskException("码本不存在: " + file + "（mask 需 --new 或首用自动新建；unmask/lookup 需已有码本）");
        }
        CodebookJson j = new CodebookJson();
        j.created = System.currentTimeMillis();
        return new Codebook(j);
    }

    public static void save(Path file, char[] pass, Codebook book) {
        try {
            CodebookStore.save(file, pass, book.json());
        } catch (IOException e) {
            throw new MaskException("码本保存失败: " + e.getMessage(), e);
        }
    }

    private BookIo() {
    }
}
