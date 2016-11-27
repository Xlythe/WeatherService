package com.xlythe.service.weather;

public class Objects {
    public static boolean equals(Object a, Object b) {
        if (a == null) {
            return b == null;
        } else {
            return a.equals(b);
        }
    }

    public static int hashCode(Object... objects) {
        if (objects == null) {
            return 0;
        } else {
            int hashCode = 32;
            for (Object obj : objects) {
                if (obj == null) continue;
                hashCode += 32 * obj.hashCode();
            }
            return hashCode;
        }
    }
}
