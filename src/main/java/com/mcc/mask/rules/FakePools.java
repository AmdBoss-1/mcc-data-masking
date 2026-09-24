package com.mcc.mask.rules;

import java.util.Random;

/** 假值词池：组合生成、无生僻字、长度接近原值（耐抄）。 */
public final class FakePools {

    public static final String[] REGIONS = {
            "昆山", "吴江", "太仓", "常熟", "张家港", "江阴", "宜兴", "溧阳", "靖江", "泰兴",
            "嘉定", "青浦", "松江", "奉贤", "慈溪", "余姚", "诸暨", "海宁", "桐乡", "德清",
            "长兴", "嘉善", "平湖", "海盐", "安吉", "临海", "温岭", "瑞安", "乐清", "龙口"
    };
    public static final String[] ZI = {
            "华", "恒", "瑞", "鑫", "宏", "佳", "联", "盛", "远", "立",
            "光", "阳", "明", "达", "通", "源", "丰", "泰", "和", "信",
            "安", "巨", "凯", "朗", "茂", "诺", "鹏", "齐", "润", "升"
    };
    public static final String[] INDUSTRIES = {
            "精密五金", "金属制品", "电子科技", "机械制造", "模具", "塑胶", "汽车零部件",
            "纺织", "新材料", "智能装备", "液压件", "标准件", "铸造", "钣金", "热处理"
    };
    public static final String[] SURNAMES = {
            "王", "李", "张", "刘", "陈", "杨", "赵", "黄", "周", "吴",
            "徐", "孙", "胡", "朱", "高", "林", "何", "郭", "马", "罗",
            "梁", "宋", "郑", "谢", "韩", "唐", "冯", "于", "董", "萧"
    };
    public static final String[] GIVEN = {
            "伟", "芳", "娜", "敏", "静", "磊", "军", "洋", "勇", "艳",
            "杰", "娟", "涛", "明", "超", "秀英", "霞", "平", "刚", "桂英",
            "建华", "建国", "建军", "志强", "志明", "海燕", "玉兰", "文博", "雨桐", "子轩"
    };
    public static final String[] MAIL_LOCAL = {
            "wang", "li", "zhang", "liu", "chen", "yang", "zhao", "huang", "zhou", "wu"
    };
    public static final String[] MAIL_DOMAIN = {
            "hxmail.cn", "qymail.com", "corpmail.cn", "mxbox.cn", "entmail.com"
    };

    public static String factory(Random r, String orig) {
        String region = REGIONS[r.nextInt(REGIONS.length)];
        String zi = "" + ZI[r.nextInt(ZI.length)] + ZI[r.nextInt(ZI.length)];
        String industry = INDUSTRIES[r.nextInt(INDUSTRIES.length)];
        String suffix = orig != null && orig.endsWith("公司") ? "有限公司" : "厂";
        return region + zi + industry + suffix;
    }

    public static String person(Random r) {
        return SURNAMES[r.nextInt(SURNAMES.length)] + GIVEN[r.nextInt(GIVEN.length)];
    }

    public static String email(Random r) {
        return MAIL_LOCAL[r.nextInt(MAIL_LOCAL.length)] + (1000 + r.nextInt(9000))
                + "@" + MAIL_DOMAIN[r.nextInt(MAIL_DOMAIN.length)];
    }

    private FakePools() {
    }
}
