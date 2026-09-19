package cn.sfj.jiaowutong.service;

import java.util.Map;

/**
 * 姓氏拼音首字母工具（仅用于脱敏展示名，如 张→Z、欧阳→O）。
 * 覆盖常见姓氏，生僻姓回退 X。
 */
public final class PinyinUtil {

    private static final Map<Character, Character> INITIALS = Map.ofEntries(
            Map.entry('张', 'Z'), Map.entry('王', 'W'), Map.entry('李', 'L'),
            Map.entry('刘', 'L'), Map.entry('陈', 'C'), Map.entry('杨', 'Y'),
            Map.entry('赵', 'Z'), Map.entry('黄', 'H'), Map.entry('周', 'Z'),
            Map.entry('吴', 'W'), Map.entry('徐', 'X'), Map.entry('孙', 'S'),
            Map.entry('胡', 'H'), Map.entry('朱', 'Z'), Map.entry('高', 'G'),
            Map.entry('林', 'L'), Map.entry('何', 'H'), Map.entry('郭', 'G'),
            Map.entry('马', 'M'), Map.entry('罗', 'L'), Map.entry('梁', 'L'),
            Map.entry('宋', 'S'), Map.entry('郑', 'Z'), Map.entry('谢', 'X'),
            Map.entry('韩', 'H'), Map.entry('唐', 'T'), Map.entry('冯', 'F'),
            Map.entry('于', 'Y'), Map.entry('董', 'D'), Map.entry('萧', 'X'),
            Map.entry('程', 'C'), Map.entry('曹', 'C'), Map.entry('袁', 'Y'),
            Map.entry('邓', 'D'), Map.entry('许', 'X'), Map.entry('傅', 'F'),
            Map.entry('沈', 'S'), Map.entry('曾', 'Z'), Map.entry('彭', 'P'),
            Map.entry('吕', 'L'), Map.entry('苏', 'S'), Map.entry('卢', 'L'),
            Map.entry('蒋', 'J'), Map.entry('蔡', 'C'), Map.entry('贾', 'J'),
            Map.entry('丁', 'D'), Map.entry('魏', 'W'), Map.entry('薛', 'X'),
            Map.entry('叶', 'Y'), Map.entry('阎', 'Y'), Map.entry('余', 'Y'),
            Map.entry('潘', 'P'), Map.entry('杜', 'D'), Map.entry('戴', 'D'),
            Map.entry('夏', 'X'), Map.entry('钟', 'Z'), Map.entry('汪', 'W'),
            Map.entry('田', 'T'), Map.entry('任', 'R'), Map.entry('姜', 'J'),
            Map.entry('范', 'F'), Map.entry('方', 'F'), Map.entry('石', 'S'),
            Map.entry('姚', 'Y'), Map.entry('谭', 'T'), Map.entry('廖', 'L'),
            Map.entry('邹', 'Z'), Map.entry('熊', 'X'), Map.entry('金', 'J'),
            Map.entry('陆', 'L'), Map.entry('郝', 'H'), Map.entry('孔', 'K'),
            Map.entry('白', 'B'), Map.entry('崔', 'C'), Map.entry('康', 'K'),
            Map.entry('毛', 'M'), Map.entry('邱', 'Q'), Map.entry('秦', 'Q'),
            Map.entry('江', 'J'), Map.entry('史', 'S'), Map.entry('顾', 'G'),
            Map.entry('侯', 'H'), Map.entry('邵', 'S'), Map.entry('孟', 'M'),
            Map.entry('龙', 'L'), Map.entry('万', 'W'), Map.entry('段', 'D'),
            Map.entry('雷', 'L'), Map.entry('钱', 'Q'), Map.entry('汤', 'T'),
            Map.entry('尹', 'Y'), Map.entry('黎', 'L'), Map.entry('易', 'Y'),
            Map.entry('常', 'C'), Map.entry('武', 'W'), Map.entry('乔', 'Q'),
            Map.entry('贺', 'H'), Map.entry('赖', 'L'), Map.entry('龚', 'G')
    );

    private PinyinUtil() {
    }

    public static char surnameInitial(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return 'X';
        }
        return INITIALS.getOrDefault(fullName.charAt(0), 'X');
    }
}
