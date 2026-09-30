package com.lzj.railway.user.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 用户域敏感信息脱敏工具。 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SensitiveDataMasker {

    /** 手机号保留前三位和后四位。 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        if (phone.length() < 7) {
            return "*".repeat(phone.length());
        }
        return phone.substring(0, 3)
                + "*".repeat(phone.length() - 7)
                + phone.substring(phone.length() - 4);
    }

    /** 证件号保留前四位和后四位。 */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.isBlank()) {
            return idCard;
        }
        if (idCard.length() <= 8) {
            return "*".repeat(idCard.length());
        }
        return idCard.substring(0, 4)
                + "*".repeat(idCard.length() - 8)
                + idCard.substring(idCard.length() - 4);
    }

    /** 邮箱仅保留本地部分首字符和完整域名。 */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            return "*".repeat(email.length());
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
