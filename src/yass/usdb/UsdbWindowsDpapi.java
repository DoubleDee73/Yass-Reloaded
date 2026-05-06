package yass.usdb;

import com.sun.jna.platform.win32.Crypt32Util;

final class UsdbWindowsDpapi {
    byte[] decrypt(byte[] encrypted) {
        return Crypt32Util.cryptUnprotectData(encrypted);
    }
}
