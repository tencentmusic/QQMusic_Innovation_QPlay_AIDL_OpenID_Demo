package com.tencent.qqmusic.api.demo.util;

/**
 * Created by qitianliu on 2022/8/8.
 */
public class BitConverter {
    public static int intFromByteArray(byte[] sizeBuf, int offset) {
        int ret = 0;

        for(int i = offset; i < offset + 4; ++i) {
            ret <<= 8;
            ret |= 255 & sizeBuf[i];
        }

        return ret;
    }
}
