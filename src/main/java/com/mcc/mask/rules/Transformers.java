package com.mcc.mask.rules;

import java.util.Random;

/** 各类敏感值的假值变换器（格式保留；身份证/税号校验位重算）。 */
public final class Transformers {

    private static final String PLATE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
    private static final String USCC_CHARS = "0123456789ABCDEFGHJKLMNPQRTUWXY";
    private static final int[] ID_W = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final String ID_CHECK = "10X98765432";
    private static final int[] USCC_W = {1, 3, 9, 27, 19, 26, 16, 17, 20, 29, 25, 13, 8, 24, 10, 30, 28};

    public static Transformer phone() {
        return (orig, seed) -> {
            Random r = new Random(seed);
            StringBuilder sb = new StringBuilder("1").append(3 + r.nextInt(7));
            for (int i = 0; i < 9; i++) {
                sb.append(r.nextInt(10));
            }
            return sb.toString();
        };
    }

    public static Transformer maskPhone() {
        return (orig, seed) -> orig != null && orig.length() == 11
                ? orig.substring(0, 3) + "****" + orig.substring(7)
                : "****";
    }

    public static Transformer idCard() {
        return (orig, seed) -> {
            Random r = new Random(seed);
            StringBuilder sb = new StringBuilder();
            sb.append(1 + r.nextInt(9));
            for (int i = 0; i < 5; i++) {
                sb.append(r.nextInt(10));
            }
            sb.append(String.format("%04d", 1960 + r.nextInt(46)))
              .append(String.format("%02d", 1 + r.nextInt(12)))
              .append(String.format("%02d", 1 + r.nextInt(28)));
            for (int i = 0; i < 3; i++) {
                sb.append(r.nextInt(10));
            }
            return sb.toString() + idCardCheckChar(sb.toString());
        };
    }

    public static char idCardCheckChar(String body17) {
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (body17.charAt(i) - '0') * ID_W[i];
        }
        return ID_CHECK.charAt(sum % 11);
    }

    public static boolean idCardCheckValid(String s) {
        return s != null && s.length() == 18 && s.charAt(17) == idCardCheckChar(s.substring(0, 17));
    }

    public static Transformer plate() {
        return (orig, seed) -> {
            Random r = new Random(seed);
            String sep = orig != null && orig.contains("·") ? "·" : "";
            int tailLen = Math.max(5, (orig == null ? 8 : orig.length()) - 2 - sep.length());
            StringBuilder sb = new StringBuilder();
            sb.append(orig != null && !orig.isEmpty() ? orig.charAt(0) : '苏');
            sb.append((char) ('A' + r.nextInt(26)));
            sb.append(sep);
            for (int i = 0; i < tailLen; i++) {
                sb.append(PLATE_CHARS.charAt(r.nextInt(PLATE_CHARS.length())));
            }
            return sb.toString();
        };
    }

    public static Transformer taxNo() {
        return (orig, seed) -> {
            Random r = new Random(seed);
            StringBuilder sb = new StringBuilder("91");
            sb.append(1 + r.nextInt(9));
            for (int i = 0; i < 5; i++) {
                sb.append(r.nextInt(10));
            }
            for (int i = 0; i < 9; i++) {
                sb.append(USCC_CHARS.charAt(r.nextInt(USCC_CHARS.length())));
            }
            return sb.toString() + usccCheckChar(sb.toString());
        };
    }

    public static char usccCheckChar(String body17) {
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += USCC_CHARS.indexOf(body17.charAt(i)) * USCC_W[i];
        }
        return USCC_CHARS.charAt((31 - sum % 31) % 31);
    }

    public static boolean usccCheckValid(String s) {
        return s != null && s.length() == 18 && s.charAt(17) == usccCheckChar(s.substring(0, 17));
    }

    public static Transformer email() {
        return (orig, seed) -> FakePools.email(new Random(seed));
    }

    public static Transformer namePool() {
        return (orig, seed) -> FakePools.person(new Random(seed));
    }

    public static Transformer factoryPool() {
        return (orig, seed) -> FakePools.factory(new Random(seed), orig);
    }

    /** 地址：保留到省/市，街道门牌换假。 */
    public static Transformer address() {
        return (orig, seed) -> {
            Random r = new Random(seed);
            int idx = orig == null ? -1 : orig.indexOf('市');
            if (idx < 0) {
                idx = orig == null ? -1 : orig.indexOf('省');
            }
            String keep = idx >= 0 ? orig.substring(0, idx + 1) : "";
            return keep + FakePools.ZI[r.nextInt(FakePools.ZI.length)]
                    + FakePools.ZI[r.nextInt(FakePools.ZI.length)]
                    + "路" + (1 + r.nextInt(999)) + "号";
        };
    }

    /** 数值微扰：±pct% 内、保持原小数位。 */
    public static Transformer jitter(double pct) {
        return (orig, seed) -> {
            double v = Double.parseDouble(orig.trim());
            int scale = orig.trim().contains(".") ? orig.trim().split("\\.")[1].length() : 0;
            double factor = 1 + (new Random(seed).nextDouble() * 2 - 1) * pct / 100.0;
            return String.format("%." + scale + "f", v * factor);
        };
    }

    private Transformers() {
    }
}
